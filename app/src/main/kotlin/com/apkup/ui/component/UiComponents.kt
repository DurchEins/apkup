package com.apkup.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun AutoResizeText(
	text: String,
	modifier: Modifier = Modifier,
	style: TextStyle = LocalTextStyle.current,
	color: Color = style.color,
	maxLines: Int = 1,
	minFontSize: TextUnit = 8.sp,
	fontWeight: FontWeight? = null
) {
	var scaledTextStyle by remember(text, style) { mutableStateOf(style) }
	var readyToDraw by remember(text, style) { mutableStateOf(false) }

	Text(
		text = text,
		modifier = modifier.drawWithContent {
			if (readyToDraw) drawContent()
		},
		style = scaledTextStyle,
		color = color,
		maxLines = maxLines,
		fontWeight = fontWeight,
		softWrap = (maxLines > 1),
		onTextLayout = { textLayoutResult ->
			if (textLayoutResult.didOverflowWidth || textLayoutResult.didOverflowHeight) {
				val currentFontSize = scaledTextStyle.fontSize
				if (currentFontSize > minFontSize) {
					scaledTextStyle = scaledTextStyle.copy(
						fontSize = (currentFontSize.value - 1f).sp
					)
				} else {
					readyToDraw = true
				}
			} else {
				readyToDraw = true
			}
		}
	)
}

