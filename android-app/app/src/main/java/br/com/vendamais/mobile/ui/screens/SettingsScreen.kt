package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.StatusAdesao
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Slate500
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun SettingsScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val scope = rememberCoroutineScope()
    val config = state.cadastroWorkspace.config

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = "Configurações",
                subtitle = "Gerencie correspondências e regras do cadastro",
            )
        }

        if (config == null) {
            item { AdminLoadingCard() }
        } else {
            item {
                ConfigSwitchCard(
                    title = "Consulta Lemmit",
                    description = "Quando ativo, o sistema consulta a Lemmit no fluxo de cadastro.",
                    checked = config.ativarLemmit,
                    onCheckedChange = { value ->
                        scope.launch {
                            viewModel.updateCadastroConfig(buildJsonObject { put("ativar_lemmit", value) })
                        }
                    },
                )
            }

            item {
                ConfigSwitchCard(
                    title = "Exigir Envio de Arquivo",
                    description = "Quando ativo, o envio exige documento anexado.",
                    checked = config.exigirArquivo,
                    onCheckedChange = { value ->
                        scope.launch {
                            viewModel.updateCadastroConfig(buildJsonObject { put("exigir_arquivo", value) })
                        }
                    },
                )
            }

            item {
                ConfigSwitchCard(
                    title = "Lemmit no Dependente",
                    description = "Ativa preenchimento automático no dependente do novo cadastro.",
                    checked = config.lemmitDependente,
                    onCheckedChange = { value ->
                        scope.launch {
                            viewModel.updateCadastroConfig(buildJsonObject { put("lemmit_dependente", value) })
                        }
                    },
                )
            }

            item {
                ConfigSwitchCard(
                    title = "Lemmit Incluir Dep.",
                    description = "Ativa preenchimento automático no fluxo de inclusão de dependente.",
                    checked = config.lemmitInclusaoDependente,
                    onCheckedChange = { value ->
                        scope.launch {
                            viewModel.updateCadastroConfig(buildJsonObject { put("lemmit_inclusao_dependente", value) })
                        }
                    },
                )
            }

            item {
                ConfigListEditorCard(
                    title = "Situações que Barram Cadastro",
                    description = "Códigos de situação que impedem recadastro.",
                    value = config.situacoesQueBarram.joinToString(", "),
                    onSave = { text ->
                        val values = text.split(",").mapNotNull { it.trim().toIntOrNull() }
                        scope.launch {
                            viewModel.updateCadastroConfig(
                                buildJsonObject {
                                    put("situacoes_que_barram", intJsonArray(values))
                                },
                            )
                        }
                    },
                )
            }

            item {
                ConfigListEditorCard(
                    title = "Planos Válidos",
                    description = "Códigos de planos permitidos para recadastro.",
                    value = config.planosValidos.joinToString(", "),
                    onSave = { text ->
                        val values = text.split(",").mapNotNull { it.trim().toIntOrNull() }
                        scope.launch {
                            viewModel.updateCadastroConfig(
                                buildJsonObject {
                                    put("planos_validos", intJsonArray(values))
                                },
                            )
                        }
                    },
                )
            }

            item {
                ConfigListEditorCard(
                    title = "Planos Ocultos",
                    description = "Códigos que não aparecem na seleção de planos.",
                    value = config.planosOcultos.joinToString(", "),
                    onSave = { text ->
                        val values = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        scope.launch {
                            viewModel.updateCadastroConfig(
                                buildJsonObject {
                                    put("planos_ocultos", stringJsonArray(values))
                                },
                            )
                        }
                    },
                )
            }

            item {
                ConfigListEditorCard(
                    title = "Códigos de Empresa Inválidos",
                    description = "Empresas inválidas para novos cadastros.",
                    value = config.codigosEmpresaInvalidos.joinToString(", "),
                    onSave = { text ->
                        val values = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        scope.launch {
                            viewModel.updateCadastroConfig(
                                buildJsonObject {
                                    put("codigos_empresa_invalidos", stringJsonArray(values))
                                },
                            )
                        }
                    },
                )
            }

            item {
                MappingCard(
                    title = "Planos",
                    lines = state.planosMap.map { plano ->
                        "${plano.planoId} • ${plano.nomeExibicao} • ${plano.regraValor}"
                    },
                    emptyMessage = "Nenhum plano configurado.",
                )
            }

            item {
                MappingCard(
                    title = "Parentesco",
                    lines = state.parentescosMap.map { parentesco ->
                        "${parentesco.parentescoId} • ${parentesco.label}"
                    },
                    emptyMessage = "Nenhum parentesco configurado.",
                )
            }

            item {
                StatusAdesoesCard(state.statusAdesoes)
            }
        }
    }
}

@Composable
private fun ConfigSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    WebCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ConfigListEditorCard(
    title: String,
    description: String,
    value: String,
    onSave: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }

    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Slate500)
            if (editing) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(text)
                            editing = false
                        },
                    ) {
                        Text("Salvar")
                    }
                    TextButton(onClick = { editing = false }) {
                        Text("Cancelar")
                    }
                }
            } else {
                Text(if (value.isBlank()) "Não configurado" else value)
                TextButton(onClick = { editing = true }) {
                    Text("Editar")
                }
            }
        }
    }
}

@Composable
private fun MappingCard(
    title: String,
    lines: List<String>,
    emptyMessage: String,
) {
    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (lines.isEmpty()) {
                Text(emptyMessage, color = Slate500)
            } else {
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun StatusAdesoesCard(items: List<StatusAdesao>) {
    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Status de Adesão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("Nenhum status cadastrado.", color = Slate500)
            } else {
                items.forEach { status ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth(0.05f),
                        )
                        Text(status.nome, color = parseColor(status.cor))
                    }
                }
            }
        }
    }
}
