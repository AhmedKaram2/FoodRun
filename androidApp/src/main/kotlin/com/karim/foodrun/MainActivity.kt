package com.karim.foodrun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val groups by viewModels<GroupViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groups.platform.attach(this)
        groups.platform.handleGoogleCallback(intent?.data)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            FoodTheme {
                GroupScreen(groups.controller)
            }
        }
    }
    override fun onStart() { super.onStart(); groups.controller.foreground(); groups.platform.googleForegrounded() }
    override fun onStop() { groups.platform.googleBackgrounded(); groups.controller.background(); super.onStop() }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); groups.platform.handleGoogleCallback(intent.data) }
    override fun onDestroy() { groups.platform.detach(this); super.onDestroy() }
}
