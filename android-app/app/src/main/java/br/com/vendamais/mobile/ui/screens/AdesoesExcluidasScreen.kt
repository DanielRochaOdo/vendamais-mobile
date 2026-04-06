package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.CadastroExcluidoItem
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard

@Composable
fun AdesoesExcluidasScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var selected by remember { mutableStateOf<CadastroExcluidoItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadCadastrosExcluidos()
    }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = "Adesoes Excluidas",
                subtitle = "Historico de exclusoes logicas com motivo e auditoria.",
            )
        }

        if (state.cadastrosExcluidos.isEmpty()) {
            item {
                WebCard {
                    Text("Nenhum registro de exclusao encontrado.")
                }
            }
        } else {
            items(state.cadastrosExcluidos) { item ->
                WebCard(
                    modifier = Modifier.clickable { selected = item },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = item.excluidoPorNome.ifBlank { "Sem nome" },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("Motivo: ${item.motivoExclusao}")
                        Text("Papel: ${item.excluidoPorRole}")
                        Text("Data: ${formatDateTime(item.excluidoEm)}")
                    }
                }
            }
        }
    }

    selected?.let { current ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Detalhes da exclusao") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cadastro ID: ${current.cadastroId}")
                    Text("Excluido por: ${current.excluidoPorNome} (${current.excluidoPorRole})")
                    Text("Data: ${formatDateTime(current.excluidoEm)}")
                    Text("Motivo: ${current.motivoExclusao}")
                    Text(
                        text = current.dadosCadastro.toString(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Fechar")
                }
            },
        )
    }
}

private fun formatDateTime(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)
}
