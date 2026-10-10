package com.karim.foodrun

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {
    private val app get() = application as FoodRunApplication
    override fun getMainComponentName() = "FoodRun"
    override fun createReactActivityDelegate(): ReactActivityDelegate = DefaultReactActivityDelegate(this,mainComponentName,fabricEnabled)
    override fun onCreate(savedInstanceState: Bundle?) {
        app.platform.attach(this)
        super.onCreate(savedInstanceState)
        handleNotification(intent)
    }
    override fun onStart() { super.onStart(); app.groups.foreground() }
    override fun onStop() { app.groups.background(); super.onStop() }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); handleNotification(intent) }
    private fun handleNotification(intent: android.content.Intent?) {
        val id = intent?.getStringExtra("notificationId") ?: return
        app.groups.openNotification(id,intent.getStringExtra("notificationAction") ?: "open")
        intent.removeExtra("notificationId");intent.removeExtra("notificationAction")
    }
    override fun onDestroy() { app.platform.detach(this); super.onDestroy() }
}
