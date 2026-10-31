plugins {
    id("drop.android-application")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Local HTTP server (Ktor CIO) and QR encoding (ZXing).
    // Project build scripts get type-safe catalog accessors, so the
    // alias segments split on hyphens here (unlike the convention
    // plugin, which holds a real VersionCatalog and uses findLibrary).
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.zxing.core)
    // Type-safe navigation route.
    implementation(libs.kotlinx.serialization.json)
}
