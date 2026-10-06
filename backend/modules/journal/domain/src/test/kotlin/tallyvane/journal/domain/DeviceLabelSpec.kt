package tallyvane.journal.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Which devices count as the same kind, for "first sign-in from a device".
 */
class DeviceLabelSpec :
    StringSpec(
        {
            "is the same kind when the browser, the system and phone-or-not match, whatever the name" {
                DeviceLabel(
                    "chrome",
                    "windows",
                    false,
                    "Work",
                ).sameKindAs(DeviceLabel("chrome", "windows", false, null)) shouldBe
                    true
            }

            "is another kind on another browser" {
                DeviceLabel(
                    "chrome",
                    "windows",
                    false,
                    null,
                ).sameKindAs(DeviceLabel("firefox", "windows", false, null)) shouldBe
                    false
            }

            "is another kind on another system" {
                DeviceLabel(
                    "chrome",
                    "windows",
                    false,
                    null,
                ).sameKindAs(DeviceLabel("chrome", "macos", false, null)) shouldBe
                    false
            }

            "is another kind when one is a phone and the other is not" {
                DeviceLabel(
                    "chrome",
                    "android",
                    true,
                    null,
                ).sameKindAs(DeviceLabel("chrome", "android", false, null)) shouldBe
                    false
            }

            "tells what it is" {
                val told = mutableListOf<String>()
                DeviceLabel("safari", "ios", true, "Phone").writeTo { browser, platform, mobile, name ->
                    told += "$browser $platform $mobile $name"
                }

                told shouldBe listOf("safari ios true Phone")
            }
        },
    )
