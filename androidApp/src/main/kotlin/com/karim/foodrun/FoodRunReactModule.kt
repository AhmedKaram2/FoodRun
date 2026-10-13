package com.karim.foodrun

import android.os.Handler
import android.os.Looper
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ViewManager
import com.karim.foodrun.shared.orders.*

class FoodRunReactPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext) = listOf(FoodRunReactModule(context))
    override fun createViewManagers(context: ReactApplicationContext) = emptyList<ViewManager<*,*>>()
}
class FoodRunReactModule(context: ReactApplicationContext) : ReactContextBaseJavaModule(context), GroupObserver {
    private val app = context.applicationContext as FoodRunApplication
    private val updates = FoodRunUpdates(context)
    private fun updateRequest(promise: Promise, block: () -> org.json.JSONObject) { Thread { try { promise.resolve(block().toString()) } catch(_: Exception) { promise.reject("APP_UPDATE", "The update could not be completed. Try again.") } }.start() }
    private val main = Handler(Looper.getMainLooper())
    private var listeners = 0
    override fun getName() = "FoodRun"
    override fun initialize() { super.initialize(); main.post { app.groups.observe(this) } }
    override fun changed(state: GroupState) = emit()
    private fun emit() { if(listeners > 0 && reactApplicationContext.hasActiveReactInstance()) reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit("FoodRunState",app.viewBridge.snapshotJson()) }
    @ReactMethod fun addListener(event: String) { main.post { listeners++; emit() } }
    @ReactMethod fun removeListeners(count: Double) { main.post { listeners = maxOf(0,listeners-count.toInt()) } }
    @ReactMethod fun checkAppUpdate(promise: Promise) = updateRequest(promise) { updates.check() }
    @ReactMethod fun startAppUpdate(promise: Promise) = updateRequest(promise) { updates.start() }
    @ReactMethod fun getAppUpdateStatus(promise: Promise) = updateRequest(promise) { updates.status() }
    @ReactMethod fun installAppUpdate(promise: Promise) = updateRequest(promise) { updates.install() }
    @ReactMethod fun getSnapshot(promise: Promise) { main.post { try { promise.resolve(app.viewBridge.snapshotJson()) } catch(_: Exception) { promise.reject("STATE","Could not open the app. Please restart.") } } }
    @ReactMethod fun getNotificationPermission(promise: Promise) {
        promise.resolve(if(androidx.core.app.NotificationManagerCompat.from(reactApplicationContext).areNotificationsEnabled()) "enabled" else "disabled")
    }
    @ReactMethod fun openNotificationSettings(promise: Promise) { main.post {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, reactApplicationContext.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent); promise.resolve(null)
        } catch(_: Exception) { promise.reject("NOTIFICATIONS", "Could not open notification settings.") }
    } }
    @ReactMethod fun dispatch(action: String, value: String) { main.post { app.viewBridge.dispatch(action,value) } }
    @ReactMethod fun update(key: String, value: String) { main.post { app.viewBridge.update(key,value) } }
    @ReactMethod fun tick() { main.post { app.viewBridge.tick() } }
    @ReactMethod fun quickAction(action: String, value: String) { main.post { app.viewBridge.quickAction(action,value) } }
    @ReactMethod fun dismissFeedback(id: Double) { main.post { app.groups.dismissFeedback(id.toLong()) } }
    @ReactMethod fun share(text: String) { main.post { app.platform.share(text,"") } }
    @ReactMethod fun copy(text: String) { main.post { app.platform.copyToClipboard(text) } }
    @ReactMethod fun photo(key: String, promise: Promise) { main.post {
        if(key !in listOf("PHOTO","ADMIN_PHOTO","RECEIPT_PHOTO")) { promise.reject("PHOTO","Choose a profile or receipt photo."); return@post }
        app.platform.choosePhoto(key == "RECEIPT_PHOTO",object : GroupReplyCallback { override fun complete(body: String,error: String) { if(error.isEmpty()) promise.resolve(body) else promise.reject("PHOTO",error) } })
    } }
    override fun invalidate() { main.post { listeners = 0; app.groups.removeObserver(this) }; super.invalidate() }
}
