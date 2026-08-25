package tallyvane.platform.observability.log

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

/**
 * Runs [block] and returns the lines the configured appender wrote.
 *
 * Swaps only the appender's destination, so what is asserted on is the output of
 * the encoder `logback-tallyvane.xml` actually configures — a test that built its
 * own encoder would pass while the shipping configuration was wrong.
 */
internal suspend fun logged(block: suspend () -> Unit): List<String> {
    val appender = jsonAppender()
    val captured = ByteArrayOutputStream()
    appender.outputStream = captured
    try {
        block()
    } finally {
        appender.outputStream = System.out
    }
    return captured.toString(Charsets.UTF_8.name()).lines().filter { it.isNotBlank() }
}

private fun jsonAppender(): OutputStreamAppender<ILoggingEvent> {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val appender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("json")
    checkNotNull(appender) { "logback-test.xml must include logback-tallyvane.xml, which defines the 'json' appender" }
    @Suppress("UNCHECKED_CAST")
    return appender as OutputStreamAppender<ILoggingEvent>
}
