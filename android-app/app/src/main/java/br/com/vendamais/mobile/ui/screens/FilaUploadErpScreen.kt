package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard

@Composable
fun FilaUploadErpScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var selectedFilter by rememberSaveable { mutableStateOf("todos") }
    var currentPage by rememberSaveable { mutableStateOf(1) }
    val itemsPerPage = 20

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.loadUploadQueue()
            delay(5000)
        }
    }

    val filteredItems = state.uploadQueue.filter { item ->
        selectedFilter == "todos" || item.status == selectedFilter
    }
    val totalPages = ((filteredItems.size + itemsPerPage - 1) / itemsPerPage).coerceAtLeast(1)
    if (currentPage > totalPages) currentPage = totalPages
    val pagedItems = filteredItems.drop((currentPage - 1) * itemsPerPage).take(itemsPerPage)

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = "Fila Upload ERP",
                subtitle = "Monitoramento do fallback de upload (direto -> fila -> reprocesso).",
            )
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectionField(
                            label = "Filtro de status",
                            value = selectedFilter,
                            options = listOf(
                                "todos" to "todos",
                                "queued" to "queued",
                                "processing" to "processing",
                                "retry_wait" to "retry_wait",
                                "success" to "success",
                                "failed" to "failed",
                            ),
                            onSelected = { selectedFilter = it; currentPage = 1 },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.loadUploadQueue() },
                            enabled = !state.adminFeatureLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Atualizar")
                        }
                        Button(
                            onClick = { viewModel.processUploadQueue() },
                            enabled = !state.adminFeatureLoading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Processar fila")
                        }
                    }
                    Button(
                        onClick = { viewModel.resetStuckQueue() },
                        enabled = !state.adminFeatureLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Resetar travados")
                    }
                }
            }
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resumo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Itens carregados: ${state.uploadQueue.size}")
                    state.uploadQueueOperation?.message?.let { Text("Ultima operacao: $it") }
                    state.resetQueueResult?.let { Text("Ultimo reset: ${it.resetCount} item(ns)") }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item {
                WebCard {
                    Text("Nenhum item encontrado.")
                }
            }
        } else {
            items(pagedItems) { item ->
                WebCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Arquivo: ${item.arquivoNome}", fontWeight = FontWeight.Medium)
                        Text("Status: ${item.status}  Tentativas: ${item.attempts}")
                        Text("Cadastro: ${item.cadastroId ?: "-"}  Tipo: ${item.tipo}")
                        Text("Proxima tentativa: ${item.nextAttemptAt?.let(::formatDateTime) ?: "-"}")
                        if (!item.lastError.isNullOrBlank()) {
                            Text("Erro: ${item.lastError}", color = MaterialTheme.colorScheme.error)
                        }
                        if (item.status == "failed" || item.status == "retry_wait") {
                            Button(onClick = { viewModel.reprocessUploadQueueItem(item.id) }, enabled = !state.adminFeatureLoading) {
                                Text("Reprocessar item")
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1) { Text("Anterior") }
                    Text("Pagina $currentPage de $totalPages")
                    Button(onClick = { if (currentPage < totalPages) currentPage++ }, enabled = currentPage < totalPages) { Text("Proxima") }
                }
            }
        }
    }
}

private fun formatDateTime(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)
}
