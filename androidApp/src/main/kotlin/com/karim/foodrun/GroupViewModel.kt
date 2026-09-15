package com.karim.foodrun

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.karim.foodrun.shared.orders.GroupController

/** Keeps KMP drafts and in-flight commands intact across Activity recreation. */
class GroupViewModel(application: Application) : AndroidViewModel(application) {
    val platform = GroupAndroidPlatform(application)
    val controller = GroupController(platform)

    override fun onCleared() {
        controller.close()
        platform.close()
    }
}
