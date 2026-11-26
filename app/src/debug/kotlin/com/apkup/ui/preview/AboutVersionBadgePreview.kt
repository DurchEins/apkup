package com.apkup.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.apkup.ui.screen.AboutHeaderVersionRow
import com.apkup.ui.screen.CopyableVersionBadge
import com.apkup.ui.theme.AppTheme

@Composable
private fun PreviewFrame(
	darkTheme: Boolean,
	highContrast: Boolean,
	accessibleUi: Boolean,
	versionName: String,
	versionCode: Int
) {
	AppTheme(
		darkTheme = darkTheme,
		highContrast = highContrast,
		accessibleUi = accessibleUi
	) {
		Surface(
			color = MaterialTheme.colorScheme.surfaceContainerHigh,
			modifier = Modifier.fillMaxWidth()
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 14.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				AboutHeaderVersionRow(
					appName = "ApkUp",
					versionName = versionName,
					versionCode = versionCode
				)
				CopyableVersionBadge(
					versionName = versionName,
					versionCode = versionCode,
					modifier = Modifier.padding(top = 6.dp)
				)
			}
		}
	}
}

@Preview(name = "Standard Dark", showBackground = true, widthDp = 360)
@Composable
fun AboutVersionBadgeStandardDarkPreview() {
	PreviewFrame(
		darkTheme = true,
		highContrast = false,
		accessibleUi = false,
		versionName = "1.47.0+238.74a9f8dc",
		versionCode = 238
	)
}

@Preview(name = "Standard Light Beta", showBackground = true, widthDp = 360)
@Composable
fun AboutVersionBadgeStandardLightBetaPreview() {
	PreviewFrame(
		darkTheme = false,
		highContrast = false,
		accessibleUi = false,
		versionName = "1.47.0-beta.1+238.74a9f8d-debug",
		versionCode = 238
	)
}

@Preview(name = "WCAG 2.2 Accessible Dark", showBackground = true, widthDp = 360, fontScale = 1.15f)
@Composable
fun AboutVersionBadgeAccessibleDarkPreview() {
	PreviewFrame(
		darkTheme = true,
		highContrast = true,
		accessibleUi = true,
		versionName = "1.47.0-beta.1+238.74a9f8d-debug",
		versionCode = 238
	)
}

@Preview(name = "WCAG 2.2 Accessible Light", showBackground = true, widthDp = 360, fontScale = 1.15f)
@Composable
fun AboutVersionBadgeAccessibleLightPreview() {
	PreviewFrame(
		darkTheme = false,
		highContrast = true,
		accessibleUi = true,
		versionName = "1.47.0+238.74a9f8dc",
		versionCode = 238
	)
}
