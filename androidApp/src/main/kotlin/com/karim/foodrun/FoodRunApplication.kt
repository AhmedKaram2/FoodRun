package com.karim.foodrun

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.karim.foodrun.shared.*
import com.karim.foodrun.shared.orders.*

class FoodRunApplication : Application(), ReactApplication {
    val platform by lazy { GroupAndroidPlatform(this) }
    val groups by lazy { GroupController(platform) }
    val viewBridge by lazy { GroupReactBridge(groups) }
    override val reactHost: ReactHost by lazy { getDefaultReactHost(applicationContext,PackageList(this).packages.apply { add(FoodRunReactPackage()) },useDevSupport = BuildConfig.DEBUG) }
    override fun onCreate() { super.onCreate(); loadReactNative(this) }
}
