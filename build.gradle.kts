buildscript {
    repositories { google(); mavenCentral() }
    dependencies { if(file("mobileApp/node_modules/@react-native/gradle-plugin").isDirectory) classpath("com.facebook.react:react-native-gradle-plugin") }
}
plugins {
    id("com.google.gms.google-services") version "4.4.4" apply false
    kotlin("jvm") version "2.4.20" apply false
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("android") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
    id("com.android.application") version "8.13.2" apply false
    id("com.android.kotlin.multiplatform.library") version "8.13.2" apply false
}

extra["compileSdkVersion"] = 36
extra["targetSdkVersion"] = 36
extra["minSdkVersion"] = 26
extra["kotlinVersion"] = "2.4.20"
extra["REACT_NATIVE_NODE_MODULES_DIR"] = file("mobileApp/node_modules/react-native").absolutePath
if(file("mobileApp/node_modules/@react-native/gradle-plugin").isDirectory) apply(from = "mobileApp/react-projects.gradle")
