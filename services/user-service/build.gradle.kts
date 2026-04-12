plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:events"))
    implementation(project(":shared:auth"))
    implementation(project(":shared:persistence"))
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.bundles.ktor.server.base)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.java.jwt)
    implementation(libs.bcrypt)
    implementation(libs.lettuce.core)
}
