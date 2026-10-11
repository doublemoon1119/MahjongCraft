plugins {
    kotlin("jvm")
}

group = rootProject.group
version = rootProject.version

dependencies {
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
