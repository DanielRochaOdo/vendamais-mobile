from pathlib import Path

path = Path('android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/SettingsScreen.kt')
text = path.read_text(encoding='utf-8')

replacements = [
    ('import androidx.compose.foundation.layout.padding\n', 'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.shape.RoundedCornerShape\n'),
    ('import androidx.compose.material3.Switch\n', 'import androidx.compose.material3.Surface\nimport androidx.compose.material3.Switch\n'),
    ('item { ScreenHeading(title = "Configuracoes", subtitle = "Gerencie as regras e tabelas do cadastro") }', 'item { ScreenHeading(title = "Configuracoes", subtitle = "Regras operacionais, tabelas do ERP e diagnostico do sistema.") }'),
    ('''@Composable\nprivate fun SectionButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {\n    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }\n    else TextButton(onClick = onClick, modifier = modifier) { Text(label) }\n}''', '''@Composable\nprivate fun SectionButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {\n    Surface(\n        onClick = onClick,\n        modifier = modifier,\n        shape = RoundedCornerShape(12.dp),\n        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,\n        border = androidx.compose.foundation.BorderStroke(\n            1.dp,\n            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)\n            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),\n        ),\n    ) {\n        Text(\n            text = label,\n            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),\n            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,\n            style = MaterialTheme.typography.labelLarge,\n            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,\n        )\n    }\n}'''),
    ('if (canModify) Button(onClick = { creating = true }) { Text("Adicionar Plano") }', 'if (canModify) Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar plano") }'),
    ('if (canModify) Button(onClick = { creating = true }) { Text("Adicionar Parentesco") }', 'if (canModify) Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar parentesco") }'),
    ('Button(onClick = { creating = true }) { Text("Adicionar Status") }', 'Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Adicionar status") }'),
    ('Text("Logs de API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)', 'Text("Logs de API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)\n        Text("Investigue chamadas, latencia e erros sem sair do aplicativo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one match, found {count}: {old[:100]!r}')
    text = text.replace(old, new, 1)

path.write_text(text, encoding='utf-8')
print('SettingsScreen.kt redesigned successfully')
