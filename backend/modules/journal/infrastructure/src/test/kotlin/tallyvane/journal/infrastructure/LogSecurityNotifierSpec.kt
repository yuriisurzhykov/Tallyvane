package tallyvane.journal.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.slf4j.Logger
import tallyvane.journal.domain.DeviceLabel
import tallyvane.journal.domain.Entry
import java.lang.reflect.Proxy
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val AT = Instant.parse("2026-10-06T09:00:00Z")
private val ADA = Uuid.parse("00000000-0000-7000-8000-000000000001")
private val SESSION = Uuid.parse("00000000-0000-7000-8000-0000000000e1")

/**
 * A logger that keeps what was written at the info level, as one line for each call.
 */
private class Lines {
    private val lines = mutableListOf<String>()

    fun logger(): Logger = Proxy.newProxyInstance(
        Logger::class.java.classLoader,
        arrayOf(Logger::class.java),
    ) { _, call, args ->
        if (call.name == "info") {
            lines += flat(args).joinToString(" | ")
        }
        null
    } as Logger

    private fun flat(args: Array<Any?>?): List<Any?> =
        (args ?: emptyArray()).flatMap { if (it is Array<*>) it.toList() else listOf(it) }

    fun written(): List<String> = lines.toList()
}

class LogSecurityNotifierSpec :
    StringSpec(
        {
            "writes a notable entry as one line: the kind, the account, the device and the time" {
                val lines = Lines()

                LogSecurityNotifier(lines.logger()).notify(
                    Entry.totpTurnedOff(ADA, AT, DeviceLabel("chrome", "windows", false, "Work laptop")),
                )

                lines.written().single() shouldBe
                    "security event kind={} account={} device={} at={} | TotpTurnedOff | $ADA | " +
                    "DeviceLabel(chrome on windows) | $AT"
            }

            "does not write the name the person gave the device" {
                val lines = Lines()

                LogSecurityNotifier(lines.logger()).notify(
                    Entry.signedIn(ADA, AT, SESSION, DeviceLabel("chrome", "windows", false, "Work laptop"), true),
                )

                lines.written().single() shouldNotContain "Work laptop"
            }

            "says the device is unknown when the entry has none" {
                val lines = Lines()

                LogSecurityNotifier(lines.logger()).notify(Entry.guessingStopped(ADA, AT))

                lines.written().single() shouldBe
                    "security event kind={} account={} device={} at={} | GuessingStopped | $ADA | unknown | $AT"
            }

            "writes nothing for an entry that is not notable" {
                val lines = Lines()

                LogSecurityNotifier(lines.logger()).notify(
                    Entry.signedIn(ADA, AT, SESSION, DeviceLabel("chrome", "windows", false, null), false),
                )

                lines.written() shouldBe emptyList()
            }
        },
    )
