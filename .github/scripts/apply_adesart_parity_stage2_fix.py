from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

settings_path = ROOT / "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/SettingsScreen.kt"
settings = settings_path.read_text(encoding="utf-8-sig")
for old, new in (
    ("SelectionField(", "SettingsChoiceField("),
    ("private fun <T> SelectionField(", "private fun <T> SettingsChoiceField("),
    ("AdminLoadingCard(", "SettingsLoadingCard("),
    ("private fun AdminLoadingCard(", "private fun SettingsLoadingCard("),
    ("intJsonArray(", "settingsIntJsonArray("),
    ("private fun intJsonArray(", "private fun settingsIntJsonArray("),
    ("stringJsonArray(", "settingsStringJsonArray("),
    ("private fun stringJsonArray(", "private fun settingsStringJsonArray("),
    ("parseColor(", "settingsParseColor("),
    ("private fun parseColor(", "private fun settingsParseColor("),
):
    settings = settings.replace(old, new)
settings = settings.replace(
    "private fun settingsIntJsonArray(values: List<Int>) = kotlinx.serialization.json.buildJsonArray {\n    values.forEach { add(it) }\n}",
    "private fun settingsIntJsonArray(values: List<Int>) = kotlinx.serialization.json.buildJsonArray {\n    values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }\n}",
)
settings = settings.replace(
    "private fun settingsStringJsonArray(values: List<String>) = kotlinx.serialization.json.buildJsonArray {\n    values.forEach { add(it) }\n}",
    "private fun settingsStringJsonArray(values: List<String>) = kotlinx.serialization.json.buildJsonArray {\n    values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }\n}",
)
settings_path.write_text(settings, encoding="utf-8")

deleted_path = ROOT / "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/AdesoesExcluidasScreen.kt"
deleted = deleted_path.read_text(encoding="utf-8-sig")
deleted = deleted.replace(
    'deps.forEach { dep -> val d = runCatching { it.jsonObject }.getOrNull();',
    'deps.forEach { dep -> val d = runCatching { dep.jsonObject }.getOrNull();',
)
deleted_path.write_text(deleted, encoding="utf-8")

print("Stage 2 compile fixes applied")
