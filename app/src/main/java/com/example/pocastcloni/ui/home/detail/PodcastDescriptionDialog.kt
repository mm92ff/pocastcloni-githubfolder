package com.example.pocastcloni.ui.home.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens

@Composable
fun PodcastDescriptionDialog(
    description: String,
    onDismissRequest: () -> Unit
) {
    val rememberedOnDismissRequest = remember(onDismissRequest) { onDismissRequest }

    Dialog(onDismissRequest = rememberedOnDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = Dimens.DialogMaxHeight)
        ) {
            Column(modifier = Modifier.padding(Dimens.PaddingLarge)) {
                val scrollState = rememberScrollState()
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .weight(1f)
                )

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
