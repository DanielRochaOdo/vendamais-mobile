from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/SettingsScreen.kt"

text = SETTINGS.read_text(encoding="utf-8-sig")
helpers = r'''

@Composable
private fun AdminLoadingCard() {
    WebCard {
        Text("Carregando configuracoes...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> SelectionField(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.take(5).forEach { (key, display) ->
                TextButton(onClick = { onSelected(key) }, modifier = Modifier.weight(1f)) {
                    Text(display, maxLines = 1)
                }
            }
        }
        if (options.size > 5) {
            options.drop(5).chunked(5).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (key, display) ->
                        TextButton(onClick = { onSelected(key) }, modifier = Modifier.weight(1f)) {
                            Text(display, maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun intJsonArray(values: List<Int>) = kotlinx.serialization.json.buildJsonArray {
    values.forEach { add(it) }
}

private fun stringJsonArray(values: List<String>) = kotlinx.serialization.json.buildJsonArray {
    values.forEach { add(it) }
}

@Composable
private fun parseColor(value: String): androidx.compose.ui.graphics.Color {
    val normalized = value.trim().removePrefix("#")
    return runCatching {
        when (normalized.length) {
            6 -> androidx.compose.ui.graphics.Color((0xFF000000L or normalized.toLong(16)).toULong())
            8 -> androidx.compose.ui.graphics.Color(normalized.toLong(16).toULong())
            else -> MaterialTheme.colorScheme.primary
        }
    }.getOrElse { MaterialTheme.colorScheme.primary }
}
'''

if "private fun AdminLoadingCard()" not in text:
    text += helpers

SETTINGS.write_text(text, encoding="utf-8")
print("Stage 2 post-fixes applied")
