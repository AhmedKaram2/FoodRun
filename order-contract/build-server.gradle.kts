plugins { kotlin("multiplatform"); kotlin("plugin.serialization") }
kotlin {
    jvmToolchain(17)
    jvm()
    sourceSets {
        commonMain.dependencies {
            api(project(":order-domain"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
