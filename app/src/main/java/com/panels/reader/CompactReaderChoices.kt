package com.panels.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Phone controls; the tablet keeps its existing segmented controls. */
@Composable
fun CompactReaderChoices(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    Text("Dirección de lectura", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadingMode.entries.forEach { mode ->
            val label = when (mode) {
                ReadingMode.RIGHT_TO_LEFT -> "Manga"
                ReadingMode.LEFT_TO_RIGHT -> "Cómic"
                ReadingMode.VERTICAL_CONTINUOUS -> "Webtoon"
            }
            ReaderChoice(label, settings.readingMode == mode, Modifier.weight(1f), stacked = true) {
                onChange(settings.copy(readingMode = mode))
            }
        }
    }
    Text(
        when (settings.readingMode) {
            ReadingMode.RIGHT_TO_LEFT -> "De derecha a izquierda ←"
            ReadingMode.LEFT_TO_RIGHT -> "De izquierda a derecha →"
            ReadingMode.VERTICAL_CONTINUOUS -> "Desplazamiento vertical ↓"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text("Ajuste de página", style = MaterialTheme.typography.labelLarge)
    Column(Modifier.selectableGroup().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageFit.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { fit ->
                    ReaderChoice(fit.label, settings.pageFit == fit, Modifier.weight(1f)) {
                        onChange(settings.copy(pageFit = fit))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant)
    ) {
        val touch = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 12.dp)
        val marker: @Composable () -> Unit = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        val title: @Composable () -> Unit = {
            Text(label, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center)
        }
        if (stacked) {
            Column(touch, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                marker()
                title()
            }
        } else {
            Row(touch, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                marker()
                Spacer(Modifier.width(8.dp))
                title()
                // Balance the selection indicator so the label stays centered.
                Spacer(Modifier.width(28.dp))
            }
        }
    }
}
