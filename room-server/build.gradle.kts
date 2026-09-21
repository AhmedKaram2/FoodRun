plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
kotlin { jvmToolchain(17) }
application { mainClass.set("com.karim.foodrun.server.MainKt") }
dependencies {
    implementation(project(":order-domain"))
    implementation(project(":order-contract"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.ktor:ktor-server-netty:3.5.2")
    implementation("io.ktor:ktor-server-websockets:3.5.2")
    implementation("io.ktor:ktor-server-cors:3.5.2")
    implementation("io.ktor:ktor-network-tls-certificates:3.5.2")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.google.cloud:google-cloud-firestore:3.47.0")
    implementation("org.jmdns:jmdns:3.6.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("io.ktor:ktor-server-test-host:3.5.2")
}
tasks.test { useJUnitPlatform() }

distributions { main { contents { from("README.md"); from("AUDIT.md") } } }

tasks.processResources { from(rootProject.file("webApp/dist")) { into("web") } }
