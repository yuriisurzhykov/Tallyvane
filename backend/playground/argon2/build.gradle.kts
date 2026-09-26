plugins {
    id("tallyvane.spike")
}

application {
    mainClass.set("tallyvane.playground.argon2.Argon2SpikeKt")
}

dependencies {
    implementation(libs.argon2.jvm.nolibs)
}
