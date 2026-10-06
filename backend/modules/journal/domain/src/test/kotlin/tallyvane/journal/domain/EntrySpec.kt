package tallyvane.journal.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val ACCOUNT = Uuid.parse("0199a000-0000-7000-8000-000000000001")
private val SESSION = Uuid.parse("0199a000-0000-7000-8000-0000000000a1")
private val AT = Instant.parse("2026-10-06T09:00:00Z")
private val CHROME_ON_WINDOWS = DeviceLabel("chrome", "windows", false, "Work laptop")

/**
 * What an entry tells and which of them are worth telling the person about at once.
 */
class EntrySpec :
    StringSpec(
        {
            "tells what happened, and then the device" {
                val told = mutableListOf<String>()
                Entry.signedIn(ACCOUNT, AT, SESSION, CHROME_ON_WINDOWS, firstFromDevice = true).writeTo(
                    object : Entry.Record {
                        override fun entry(
                            account: Uuid,
                            kind: EntryKind,
                            occurredAt: Instant,
                            session: Uuid?,
                            firstFromDevice: Boolean,
                            codesLeft: Int?,
                        ) {
                            told += "entry $kind $session $firstFromDevice $codesLeft"
                        }

                        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
                            told += "device $browser $platform $mobile $name"
                        }
                    },
                )

                told shouldBe listOf("entry SignedIn $SESSION true null", "device chrome windows false Work laptop")
            }

            "tells no device when none is known" {
                var devices = 0
                Entry.totpTurnedOff(ACCOUNT, AT, device = null).writeTo(
                    object : Entry.Record {
                        override fun entry(
                            account: Uuid,
                            kind: EntryKind,
                            occurredAt: Instant,
                            session: Uuid?,
                            firstFromDevice: Boolean,
                            codesLeft: Int?,
                        ) = Unit

                        override fun device(browser: String, platform: String, mobile: Boolean, name: String?) {
                            devices++
                        }
                    },
                )

                devices shouldBe 0
            }

            "a first sign-in from a device is notable, a later one is not" {
                Entry.signedIn(ACCOUNT, AT, SESSION, CHROME_ON_WINDOWS, firstFromDevice = true).isNotable() shouldBe
                    true
                Entry.signedIn(ACCOUNT, AT, SESSION, CHROME_ON_WINDOWS, firstFromDevice = false).isNotable() shouldBe
                    false
            }

            "turning TOTP off, spending a recovery code, signing out elsewhere and a stopped guess are notable" {
                Entry.totpTurnedOff(ACCOUNT, AT, null).isNotable() shouldBe true
                Entry.recoveryCodeSpent(ACCOUNT, AT, 7).isNotable() shouldBe true
                Entry.otherDevicesSignedOut(ACCOUNT, AT, null).isNotable() shouldBe true
                Entry.guessingStopped(ACCOUNT, AT).isNotable() shouldBe true
            }

            "turning TOTP on and issuing recovery codes again are not notable" {
                Entry.totpTurnedOn(ACCOUNT, AT, null).isNotable() shouldBe false
                Entry.recoveryCodesReissued(ACCOUNT, AT, null).isNotable() shouldBe false
            }

            "refuses a negative count of codes left" {
                shouldThrow<IllegalArgumentException> { Entry.recoveryCodeSpent(ACCOUNT, AT, -1) }
            }

            "is equal to an entry made of the same parts, and not to one made of others" {
                Entry.guessingStopped(ACCOUNT, AT) shouldBe Entry.guessingStopped(ACCOUNT, AT)
                (Entry.guessingStopped(ACCOUNT, AT) == Entry.totpTurnedOff(ACCOUNT, AT, null)) shouldBe false
            }
        },
    )
