package com.apkup.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apkup.ui.screen.MainScreen


class MainActivity : ComponentActivity() {
	companion object {
		var isSnapshotMode by mutableStateOf(false)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		val isColdStart = savedInstanceState == null
		setContent { MainScreen(isColdStart = isColdStart) }
	}

	override fun onUserLeaveHint() {
		super.onUserLeaveHint()
		isSnapshotMode = true
	}

	override fun onPause() {
		super.onPause()
		isSnapshotMode = true
	}

	override fun onResume() {
		super.onResume()
		isSnapshotMode = false
	}
}
