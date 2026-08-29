package tallyvane.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import org.slf4j.LoggerFactory
import tallyvane.server.config.Configuration
import tallyvane.server.config.EnvironmentConfiguration
import tallyvane.platform.kernel.Environment
import java.util.Locale
import java.util.concurrent.CountDownLatch

/**
 * The process: it reads its settings, builds everything, listens, and gives it all back on the way
 * out.
 *
 * [close] is mandatory — it stops the server and releases the platform. `main` arranges it through a
 * shutdown hook; a test through `use`.
 *
 * The entry point is `main` on the companion, so `mainClass` is `tallyvane.server.Application` rather
 * than `…ApplicationKt`. `@JvmStatic` is required for the JVM to find it. ADR-010 explains the shape.
 */
public class Application(private val configuration: Configuration) : AutoCloseable {
    private val platform = PlatformWiring(configuration)

    private val wiring = Wiring(platform, configuration)

    private var server: EmbeddedServer<*, *>? = null

    /**
     * Starts listening and returns as soon as it does — a caller that wants to block does so itself.
     *
     * Also applies the configured log level to the root logger, overriding whatever `logback.xml`
     * started with (ADR-056).
     */
    public fun start() {
        // Library messages follow the JVM locale, and pgjdbc ships translations — so a log line
        // would otherwise be in whatever language the host runs in, and ungreppable across hosts.
        Locale.setDefault(Locale.ENGLISH)
        rootLogger().level = Level.toLevel(configuration.level.name)
        server = embeddedServer(
            factory = CIO,
            port = configuration.port,
        ) {
            wiring.api.install(this)
        }.also { server ->
            server.start(wait = false)
        }
    }

    /**
     * Stops accepting requests first, then releases the platform, so nothing in flight is left
     * holding a connection from a pool that has gone.
     */
    override fun close() {
        server?.stop()
        platform.close()
    }

    public companion object {
        private const val ONE = 1

        @JvmStatic
        public fun main(args: Array<String>) {
            require(args.isEmpty()) {
                "This program takes no arguments; it is configured by its environment."
            }
            val application = Application(
                configuration = EnvironmentConfiguration(
                    environment = Environment.Process(),
                ).read(),
            )
            val stopped = CountDownLatch(ONE)
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    application.close()
                    stopped.countDown()
                },
            )
            application.start()
            // `start` does not block, so this does: released by the shutdown hook above.
            stopped.await()
        }

        private fun rootLogger(): Logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    }
}
