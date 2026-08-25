plugins {
    id("tallyvane.kotlin-module")
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(projects.platform.kernel)
}

tasks.withType<Test>().configureEach {
    systemProperty("konsist.root", rootProject.projectDir.absolutePath)
    systemProperty("repo.root", rootProject.projectDir.parentFile.absolutePath)
}
