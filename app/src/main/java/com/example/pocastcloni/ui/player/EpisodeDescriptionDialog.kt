package com.example.pocastcloni.ui.player

import android.text.Spanned
import android.text.style.URLSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens

private const val URL_TAG = "URL"

@Composable
fun EpisodeDescriptionDialog(
    description: Spanned?,
    isParsing: Boolean,
    onDismissRequest: () -> Unit
) {
    val rememberedOnDismissRequest = remember(onDismissRequest) { onDismissRequest }

    Dialog(onDismissRequest = rememberedOnDismissRequest) {
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = Dimens.DialogMaxHeight)
        ) {
            Column(modifier = Modifier.padding(Dimens.PaddingLarge)) {
                Text(
                    text = stringResource(R.string.description),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Dimens.PaddingMedium)
                )

                Box(
                    modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isParsing) {
                        val loadingDescription = stringResource(R.string.status_loading)
                        CircularProgressIndicator(
                            modifier =
                            Modifier.semantics {
                                this.contentDescription = loadingDescription
                            }
                        )
                    } else if (description != null) {
                        val scrollState = rememberScrollState()
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val annotatedString =
                            remember(description, primaryColor) {
                                description.toAnnotatedString(primaryColor)
                            }
                        val uriHandler = LocalUriHandler.current

                        ClickableText(
                            text = annotatedString,
                            style =
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = URL_TAG, start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        uriHandler.openUri(annotation.item)
                                    }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = rememberedOnDismissRequest) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

private fun Spanned.toAnnotatedString(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        val spanned = this@toAnnotatedString
        append(spanned.toString())

        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)

        urlSpans.forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            addStyle(
                style =
                SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = URL_TAG,
                annotation = span.url,
                start = start,
                end = end
            )
        }
    }
