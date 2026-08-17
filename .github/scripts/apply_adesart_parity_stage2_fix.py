from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

settings_path = ROOT / "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/SettingsScreen.kt"
settings = settings_path.read_text(encoding="utf-8-sig")
for old, new in (
    ("SelectionField(", "SettingsChoiceField("),
    ("private fun <T> SelectionField(", "private fun <T> SettingsChoiceField("),
    ("intJsonArray(", "settingsIntJsonArray("),
    ("private fun intJsonArray(", "private fun settingsIntJsonArray("),
    ("stringJsonArray(", "settingsStringJsonArray("),
    ("private fun stringJsonArray(", "private fun settingsStringJsonArray("),
    ("parseColor(", "settingsParseColor("),
    ("private fun parseColor(", "private fun settingsParseColor("),
):
    settings = settings.replace(old, new)
settings_path.write_text(settings, encoding="utf-8")

deleted_path = ROOT / "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/AdesoesExcluidasScreen.kt"
deleted = deleted_path.read_text(encoding="utf-8-sig")
deleted = deleted.replace(
    'deps.forEach { dep -> val d = runCatching { it.jsonObject }.getOrNull();',
    'deps.forEach { dep -> val d = runCatching { dep.jsonObject }.getOrNull();',
)
deleted_path.write_text(deleted, encoding="utf-8")

print("Stage 2 compile fixes applied")
