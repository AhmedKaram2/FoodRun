// The Docker service builds JVM code only and does not resolve mobile plugins or SDKs.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories { mavenCentral() }
}
rootProject.name = "FoodRun"
rootProject.buildFileName = "build-server.gradle.kts"
include(":order-domain", ":order-contract", ":room-server")
project(":order-domain").buildFileName = "build-server.gradle.kts"
project(":order-contract").buildFileName = "build-server.gradle.kts"
