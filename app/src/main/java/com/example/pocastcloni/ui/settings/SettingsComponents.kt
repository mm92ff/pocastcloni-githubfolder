package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.pocastcloni.ui.common.TransparentSurfaceDefaults
import com.example.pocastcloni.ui.theme.bestContrastingColor
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants
import kotlin.math.roundToInt

// --- BASE CARD LAYOUTS ---

internal val LocalTransparentSettingsCards = compositionLocalOf { false }

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingTiny)
    )
}

@Composable
fun SettingsCard(
    onClick: (() -> Unit)? = null,
    emphasized: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val transparent = LocalTransparentSettingsCards.current
    val contentColor by TransparentSurfaceDefaults.animatedContentColor(
        transparent = transparent,
        filledColor = MaterialTheme.colorScheme.onSurface,
        label = "settingsCardContentColor"
    )
    val containerColor by TransparentSurfaceDefaults.animatedContainerColor(
        transparent = transparent,
        filledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = Constants.UI.SETTINGS_CARD_ALPHA),
        label = "settingsCardContainerColor",
        transparentColor =
        if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = Constants.UI.SETTINGS_ACTION_CARD_TRANSPARENT_ALPHA)
        } else {
            Color.Transparent
        }
    )

    Card(
        border = TransparentSurfaceDefaults.border(transparent),
        colors =
        CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(Dimens.RoundedCornerExtraLarge),
        modifier =
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = TransparentSurfaceDefaults.elevation(transparent, Dimens.Zero))
    ) {
        Column(
            modifier = Modifier.padding(Dimens.PaddingSmall),
            content = content
        )
    }
}

@Composable
fun SettingsSwitchCard(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsCard(onClick = { onCheckedChange(!checked) }) {
        val subtitleColor by TransparentSurfaceDefaults.animatedSecondaryTextColor(
            transparent = LocalTransparentSettingsCards.current,
            label = "settingsSwitchSubtitleColor"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier =
                Modifier
                    .weight(Constants.Weights.FULL)
                    .padding(end = Dimens.PaddingMedium)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

class SettingsSliderValueMapping(
    val toSliderPosition: (Int) -> Float,
    val toValue: (Float) -> Int,
    val stateDescription: (@Composable (Int) -> String)? = null
) {
    companion object {
        val Identity =
            SettingsSliderValueMapping(
                toSliderPosition = { it.toFloat() },
                toValue = { it.roundToInt() }
            )
    }
}

@Composable
fun SettingsSliderCard(
    title: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (Int) -> Unit,
    valueDisplay: @Composable (Int) -> Unit,
    valueMapping: SettingsSliderValueMapping = SettingsSliderValueMapping.Identity
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(valueMapping.toSliderPosition(value)) }
    var displayedValue by remember(value) { mutableIntStateOf(value) }
    val displayedStateDescription = valueMapping.stateDescription?.invoke(displayedValue)

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(Constants.Weights.FULL)
            )
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Box(
                modifier = Modifier.weight(Constants.Weights.FULL / 2f),
                contentAlignment = Alignment.CenterEnd
            ) {
                valueDisplay(displayedValue)
            }
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        Slider(
            value = sliderPosition,
            onValueChange = {
                sliderPosition = it
                displayedValue = valueMapping.toValue(it)
            },
            onValueChangeFinished = {
                val finalValue = valueMapping.toValue(sliderPosition)
                sliderPosition = valueMapping.toSliderPosition(finalValue)
                displayedValue = finalValue
                onValueChangeFinished(finalValue)
            },
            valueRange = valueRange,
            steps = steps,
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    displayedStateDescription?.let { this.stateDescription = it }
                }
        )
    }
}

// --- HELPER COMPONENTS (required by SettingsSections.kt) ---

@Composable
fun ColorCircle(
    colorHex: Long,
    colorName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = Color(colorHex)
    Box(
        modifier =
        Modifier
            .size(Dimens.ColorCircleSize)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = colorName }
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .border(
                width = if (isSelected) Dimens.BorderWidthSelected else Dimens.Zero,
                color = if (isSelected) MaterialTheme.colorScheme.outline else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = bestContrastingColor(color),
                modifier = Modifier.size(Dimens.CheckIconSize)
            )
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.PaddingTiny),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon =
        if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.SmallIconSize)
                )
            }
        } else {
            null
        }
    )
}
