dependencies {
    implementation(project(":application:application-common"))
    api(platform(libs.koin.bom))
    api(libs.koin.core)
}