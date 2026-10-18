package com.apkup.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkup.R


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedButtonSetting(
    text: String,
    options: List<String>,
    getValue: () -> Int,
    setValue: (Int) -> Unit,
    @DrawableRes icon: Int = R.drawable.ic_system
) = Row(
    Modifier
        .fillMaxWidth()
        .heightIn(min = 54.dp)
        .padding(horizontal = 2.dp, vertical = 4.dp),
    verticalAlignment = CenterVertically
) {
    val currentExternal = getValue()
    var position by remember(currentExternal) { mutableIntStateOf(currentExternal) }
    SettingIconBadge(icon = icon, contentDescription = text)
    Column(Modifier.padding(start = 10.dp).weight(1f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Start)
        )
        SingleChoiceSegmentedButtonRow(Modifier.padding(top = 4.dp).fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = {
                        position = index
                        setValue(position)
                    },
                    selected = index == position
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun SwitchSetting(
    getValue: () -> Boolean,
    setValue: (Boolean) -> Unit,
    text: String,
    @DrawableRes icon: Int = R.drawable.ic_system,
    subtitle: String? = null
) {
    val currentExternal = getValue()
    var value by remember(currentExternal) { mutableStateOf(currentExternal) }
    val onToggle: (Boolean) -> Unit = { requested ->
        setValue(requested)
        value = getValue()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = value, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = CenterVertically
    ) {
        SettingIconBadge(icon = icon, contentDescription = text)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = value,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun SettingIconBadge(
    @DrawableRes icon: Int,
    contentDescription: String
) = Box(
    modifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center
) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
    )
}

