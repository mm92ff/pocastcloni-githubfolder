package com.example.pocastcloni.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens

private const val TITLE_MAX_LINES = 2
private const val SUBTITLE_MAX_LINES = 1
private const val IMAGE_ASPECT_RATIO = 1f

// Skalierung basiert jetzt auf der verfügbaren Gesamthöhe des Bereichs.
// 480dp ist ein geschätzter Wert: Wenn weniger Platz als ca. 480dp da ist,
// fangen Text und Abstände an zu schrumpfen.
private const val TOTAL_HEIGHT_REFERENCE_DP = 480f
private const val TITLE_SCALE_MIN = 0.75f

@Composable
fun FullPlayerMetadataFlexibleCover(
    modifier: Modifier = Modifier,
    playerState: PlayerUiState,
    onEvent: (PlayerScreenEvent) -> Unit,
    onCollapse: () -> Unit
) {
    // BoxWithConstraints liefert uns 'maxHeight'.
    // Dieser Wert kommt vom Parent (durch weight) und ist stabil,
    // egal wie groß der Text im Inneren tatsächlich wird.
    BoxWithConstraints(modifier = modifier) {
        // Berechnung des Skalierungsfaktors basierend auf dem verfügbaren Platz
        val scale =
            remember(maxHeight) {
                val availableHeightDp = maxHeight.value
                // Wenn Höhe = 0 (Start), dann 1f, sonst Verhältnis zur Referenz
                if (availableHeightDp <= 0f) {
                    1f
                } else {
                    (availableHeightDp / TOTAL_HEIGHT_REFERENCE_DP).coerceIn(TITLE_SCALE_MIN, 1f)
                }
            }

        val baseTitleStyle = MaterialTheme.typography.headlineSmall
        val titleStyle =
            remember(scale, baseTitleStyle) {
                baseTitleStyle.copy(
                    fontSize = baseTitleStyle.fontSize * scale,
                    lineHeight = baseTitleStyle.lineHeight * scale
                )
            }

        Column(
            modifier = Modifier.fillMaxSize(), // Füllt die BoxWithConstraints aus
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // Nimmt den restlichen Platz ein
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = playerState.coverUrl.ifBlank { android.R.drawable.ic_menu_gallery },
                    contentDescription = stringResource(R.string.desc_cover),
                    modifier =
                    Modifier
                        // onSizeChanged wurde entfernt, um den Layout-Loop zu verhindern
                        .aspectRatio(IMAGE_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.RoundedCornerLarge))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onEvent(PlayerScreenEvent.ShowDescription) },
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(Dimens.PaddingLarge * scale))

            Text(
                text = playerState.currentEpisodeTitle,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onCollapse)
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingMedium * scale))

            Text(
                text = playerState.currentEpisodeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = SUBTITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )

            // Abstand Author -> Progress (fester minimaler Abstand)
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        }
    }
}
