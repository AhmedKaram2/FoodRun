pluginManagement {
    if(file("mobileApp/node_modules/@react-native/gradle-plugin").isDirectory) includeBuild("mobileApp/node_modules/@react-native/gradle-plugin")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    if(file("mobileApp/node_modules/@react-native/gradle-plugin").isDirectory) id("com.facebook.react.settings")
}
val reactNativeInstalled = file("mobileApp/node_modules/@react-native/gradle-plugin").isDirectory
if(reactNativeInstalled) {
    includeBuild("mobileApp/node_modules/@react-native/gradle-plugin")
    apply(from = "mobileApp/react-settings.gradle")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "FoodRun"
include(":shared", ":order-domain", ":order-contract", ":room-server")
if(reactNativeInstalled) include(":androidApp")
