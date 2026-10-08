package com.karim.foodrun

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.karim.foodrun.shared.*
import com.karim.foodrun.shared.orders.*
import java.util.TimeZone

class FoodRunApplication : Application(), ReactApplication {
    val platform by lazy { GroupAndroidPlatform(this) }
    val groups by lazy { GroupController(platform) }
    val quickWheel by lazy {
        val preferences = getSharedPreferences("food_run",0)
        FoodRunController(object : FoodRunStorage {
            override fun read() = preferences.getString("state_v1",null)
            override fun write(value: String) { check(preferences.edit().putString("state_v1",value).commit()) }
        },object : FoodRunTimeZone { override fun offsetSecondsAt(timeMillis: Double) = TimeZone.getDefault().getOffset(timeMillis.toLong()) / 1000 })
    }
    val viewBridge by lazy { GroupReactBridge(groups,quickWheel) }
    override val reactHost: ReactHost by lazy { getDefaultReactHost(applicationContext,PackageList(this).packages.apply { add(FoodRunReactPackage()) },useDevSupport = BuildConfig.DEBUG) }
    override fun onCreate() { super.onCreate(); loadReactNative(this) }
}
