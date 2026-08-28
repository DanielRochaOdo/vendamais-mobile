from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, got {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"updated {label}")


operational = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/components/OperationalComponents.kt")
text = operational.read_text()
if "import androidx.compose.foundation.horizontalScroll" not in text:
    text = text.replace(
        "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\n",
        "import androidx.compose.foundation.background\nimport androidx.compose.foundation.clickable\nimport androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.rememberScrollState\n",
        1,
    )
if "import androidx.compose.foundation.layout.widthIn" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.size\n",
        "import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.widthIn\n",
        1,
    )
old_tabs = """    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VendaRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(VendaSpacing.x1),
            horizontalArrangement = Arrangement.spacedBy(VendaSpacing.x1),
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    onClick = { onSelected(index) },
                    shape = RoundedCornerShape(VendaRadius.md),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    tonalElevation = if (selected) 1.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = VendaSpacing.x2, vertical = VendaSpacing.x2),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
"""
new_tabs = """    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VendaRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(VendaSpacing.x1),
            horizontalArrangement = Arrangement.spacedBy(VendaSpacing.x1),
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .widthIn(min = 88.dp)
                        .heightIn(min = 40.dp),
                    onClick = { onSelected(index) },
                    shape = RoundedCornerShape(VendaRadius.md),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    tonalElevation = if (selected) 1.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = VendaSpacing.x3, vertical = VendaSpacing.x2),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
"""
replace_once(operational, old_tabs, new_tabs, "VendaSectionTabs")
text = operational.read_text()
old_loading = "            Column(verticalArrangement = Arrangement.spacedBy(VendaSpacing.x1)) {\n                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n"
new_loading = "            Column(\n                modifier = Modifier.weight(1f),\n                verticalArrangement = Arrangement.spacedBy(VendaSpacing.x1),\n            ) {\n                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)\n"
replace_once(operational, old_loading, new_loading, "VendaLoadingState")

cadastros = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastrosScreen.kt")
old_filter_header = """            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = \"Filtros de busca\",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = \"Mostrando $filteredCount de $totalCount adesoes\",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onClearFilters) {
                        Text(\"Limpar\")
                    }
                    Button(onClick = onToggleExpanded) {
                        Text(if (expanded) \"Ocultar\" else \"Filtrar\")
                    }
                }
            }
"""
new_filter_header = """            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    Text(
                        text = \"Filtros de busca\",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = \"Mostrando $filteredCount de $totalCount adesoes\",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onClearFilters,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(\"Limpar\", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = onToggleExpanded,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (expanded) \"Ocultar\" else \"Filtrar\",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
"""
replace_once(cadastros, old_filter_header, new_filter_header, "Cadastros filter header")

deleted = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/AdesoesExcluidasScreen.kt")
old_deleted_footer = """                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = \"Vendedor: $vendedor\",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = item.excluidoPorNome,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
"""
new_deleted_footer = """                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = \"Vendedor: $vendedor\",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = \"Excluido por: ${item.excluidoPorNome}\",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
"""
replace_once(deleted, old_deleted_footer, new_deleted_footer, "Deleted adhesion card footer")

web = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/components/WebComponents.kt")
text = web.read_text()
if "import androidx.compose.ui.text.style.TextOverflow" not in text:
    text = text.replace(
        "import androidx.compose.ui.text.font.FontWeight\n",
        "import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.text.style.TextOverflow\n",
        1,
    )
web.write_text(text)
replace_once(
    web,
    """                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = VendaSpacing.x4, vertical = VendaSpacing.x3),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
""",
    """                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = VendaSpacing.x4, vertical = VendaSpacing.x3),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
""",
    "WebCard title",
)
replace_once(
    web,
    """        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
""",
    """        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
""",
    "InfoRow value",
)
