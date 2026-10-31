plugins {
    id("drop.android-application")
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // Local HTTP server (Ktor CIO) and QR encoding (ZXing).
    implementation(libs.findLibrary("ktor-server-core").get())
    implementation(libs.findLibrary("ktor-server-cio").get())
    implementation(libs.findLibrary("zxing-core").get())
    // Type-safe navigation route.
    implementation(libs.findLibrary("kotlinx-serialization-json").get())
}
