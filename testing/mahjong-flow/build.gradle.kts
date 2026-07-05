plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlinx.coroutines.test)
    implementation(project(":mahjong-logic"))
    implementation(project(":mahjong-flow:mahjong-flow-common"))
}
