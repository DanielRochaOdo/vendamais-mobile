from pathlib import Path

path = Path('android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastrosScreen.kt')
text = path.read_text(encoding='utf-8')

replacements = [
    (
        'import androidx.compose.material3.OutlinedTextField\n',
        'import androidx.compose.material3.OutlinedButton\nimport androidx.compose.material3.OutlinedTextField\n',
    ),
    (
        'import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.runtime.LaunchedEffect\n',
        'import androidx.compose.runtime.LaunchedEffect\n',
    ),
    (
        'title = "Cadastro",\n                subtitle = "Consulte CPF e gerencie cadastros",',
        'title = "Cadastros",\n                subtitle = "Inicie adesoes, resolva pendencias e acompanhe o que ja foi enviado.",',
    ),
    (
        'TextButton(onClick = { showInclusaoDialog = true }) {\n                                Text("Iniciar Inclusao")\n                            }',
        'Button(\n                                onClick = { showInclusaoDialog = true },\n                                modifier = Modifier.fillMaxWidth(),\n                            ) {\n                                Text("Iniciar inclusao de dependente")\n                            }',
    ),
    (
        '''                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    IconButton(onClick = onToggleExpanded) {\n                        Icon(\n                            imageVector = if (expanded) Icons.Rounded.Search else Icons.Rounded.Search,\n                            contentDescription = if (expanded) "Ocultar filtros" else "Mostrar filtros",\n                        )\n                    }\n                    IconButton(onClick = onClearFilters) {\n                        Icon(\n                            imageVector = Icons.Rounded.CleaningServices,\n                            contentDescription = "Limpar filtros",\n                        )\n                    }\n                }''',
        '''                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                    OutlinedButton(onClick = onClearFilters) {\n                        Text("Limpar")\n                    }\n                    Button(onClick = onToggleExpanded) {\n                        Text(if (expanded) "Ocultar" else "Filtrar")\n                    }\n                }''',
    ),
    (
        '''                Row(\n                    modifier = Modifier.fillMaxWidth(),\n                    horizontalArrangement = Arrangement.End,\n                ) {\n                    Button(onClick = onApplyFilters) {\n                        Text("Filtrar")\n                    }\n                }''',
        '''                Button(\n                    onClick = onApplyFilters,\n                    modifier = Modifier.fillMaxWidth(),\n                ) {\n                    Text("Aplicar filtros")\n                }''',
    ),
    (
        'label = "Nova Adesao",',
        'label = "Nova",',
    ),
    (
        'label = "Incluir Dep.",',
        'label = "Dependente",',
    ),
    (
        'label = "Adesoes Pendentes",',
        'label = "Pendentes",',
    ),
    (
        '''            Text(\n                text = "${tipoCadastroLabel(cadastro.tipoCadastro)} - ${statusLabel(cadastro.status)}",\n                style = MaterialTheme.typography.labelLarge,\n                color = if (cadastro.status == "enviado") Emerald else Amber500,\n            )''',
        '''            Surface(\n                shape = RoundedCornerShape(999.dp),\n                color = if (cadastro.status == "enviado") EmeraldSoft else Amber100,\n            ) {\n                Text(\n                    text = "${tipoCadastroLabel(cadastro.tipoCadastro)} · ${statusLabel(cadastro.status)}",\n                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),\n                    style = MaterialTheme.typography.labelMedium,\n                    fontWeight = FontWeight.SemiBold,\n                    color = if (cadastro.status == "enviado") Emerald else Amber500,\n                )\n            }''',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one match, found {count}: {old[:80]!r}')
    text = text.replace(old, new, 1)

# Replace icon-only pending actions with explicit actions below the summary.
old_actions = '''                if (isPendingCadastro) {\n                    Row(\n                        horizontalArrangement = Arrangement.spacedBy(2.dp),\n                        verticalAlignment = Alignment.CenterVertically,\n                    ) {\n                        if (canDeleteCadastro) {\n                            IconButton(\n                                onClick = {\n                                    val titularNome = cadastro.nome.orEmpty().ifBlank {\n                                        cadastro.cpf\n                                            .filter(Char::isDigit)\n                                            .takeIf { it.isNotBlank() }\n                                            ?.let(::formatCpf)\n                                            ?: "Cadastro ${cadastro.id.take(8)}"\n                                    }\n                                    viewModel.resolveCadastroOverlay(\n                                        CadastroModalSignal(\n                                            excluirCadastroId = cadastro.id,\n                                            excluirCadastroTitular = titularNome,\n                                        ),\n                                    )\n                                },\n                                enabled = !updatingStatus,\n                            ) {\n                                Icon(\n                                    imageVector = Icons.Rounded.Delete,\n                                    contentDescription = "Excluir adesao pendente",\n                                    tint = MaterialTheme.colorScheme.error,\n                                )\n                            }\n                        }\n                        IconButton(\n                            onClick = onClick,\n                            enabled = !updatingStatus,\n                        ) {\n                            Icon(\n                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,\n                                contentDescription = "Continuar adesao pendente",\n                            )\n                        }\n                    }\n                }'''
new_actions = '''                if (!isPendingCadastro) {\n                    Text(\n                        text = formatDateTime(cadastro.updatedAt),\n                        style = MaterialTheme.typography.labelSmall,\n                        color = MaterialTheme.colorScheme.onSurfaceVariant,\n                    )\n                }'''
if text.count(old_actions) != 1:
    raise SystemExit(f'Expected pending header actions once, found {text.count(old_actions)}')
text = text.replace(old_actions, new_actions, 1)

old_after_status = '''                statusError?.let { message ->\n                    Text(\n                        text = message,\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.error,\n                    )\n                }\n            }'''
new_after_status = '''                statusError?.let { message ->\n                    Text(\n                        text = message,\n                        style = MaterialTheme.typography.bodySmall,\n                        color = MaterialTheme.colorScheme.error,\n                    )\n                }\n                Row(\n                    modifier = Modifier.fillMaxWidth(),\n                    horizontalArrangement = Arrangement.spacedBy(8.dp),\n                ) {\n                    if (canDeleteCadastro) {\n                        OutlinedButton(\n                            onClick = {\n                                val titularNome = cadastro.nome.orEmpty().ifBlank {\n                                    cadastro.cpf\n                                        .filter(Char::isDigit)\n                                        .takeIf { it.isNotBlank() }\n                                        ?.let(::formatCpf)\n                                        ?: "Cadastro ${cadastro.id.take(8)}"\n                                }\n                                viewModel.resolveCadastroOverlay(\n                                    CadastroModalSignal(\n                                        excluirCadastroId = cadastro.id,\n                                        excluirCadastroTitular = titularNome,\n                                    ),\n                                )\n                            },\n                            enabled = !updatingStatus,\n                            modifier = Modifier.weight(1f),\n                        ) {\n                            Text("Excluir")\n                        }\n                    }\n                    Button(\n                        onClick = onClick,\n                        enabled = !updatingStatus,\n                        modifier = Modifier.weight(1f),\n                    ) {\n                        Text("Continuar")\n                    }\n                }\n            }'''
if text.count(old_after_status) != 1:
    raise SystemExit(f'Expected status block once, found {text.count(old_after_status)}')
text = text.replace(old_after_status, new_after_status, 1)

path.write_text(text, encoding='utf-8')
print('CadastrosScreen.kt redesigned successfully')
