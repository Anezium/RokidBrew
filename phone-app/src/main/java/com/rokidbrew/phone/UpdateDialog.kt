package com.rokidbrew.phone

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun UpdateDialog(version: String, downloading: Boolean, downloadPercent: Int, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BrewPanelAlt),
            border = BorderStroke(1.dp, BrewBorderHi),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (downloading) "Downloading..." else "Update available",
                    style = MaterialTheme.typography.titleLarge,
                    color = BrewGreen,
                    fontFamily = BrewFont,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (downloading) "RokidBrew $version ($downloadPercent%)" else "RokidBrew $version is ready to install.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrewText,
                )
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { downloadPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrewGreen,
                        trackColor = BrewPanel,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, enabled = !downloading) {
                        Text("Later", color = BrewDim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        colors = ButtonDefaults.buttonColors(containerColor = BrewGreen),
                    ) {
                        Text("Update", color = BrewBg)
                    }
                }
            }
        }
    }
}
