package com.apkup.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apkup.ui.screen.MainScreen


class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		val isColdStart = savedInstanceState == null
		setContent { MainScreen(isColdStart = isColdStart) }
	}
}
