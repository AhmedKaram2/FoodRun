plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
}
kotlin {
    jvmToolchain(17)
    android { namespace = "com.karim.foodrun.orders.domain"; compileSdk = 36; minSdk = 26 }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
