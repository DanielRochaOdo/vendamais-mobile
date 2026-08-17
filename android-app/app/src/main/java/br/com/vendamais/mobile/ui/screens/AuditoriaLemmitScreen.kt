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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus

@Composable
fun AuditoriaLemmitScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var startDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().withDayOfMonth(1).toString()) }
    var endDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).toString()) }
    var currentPage by rememberSaveable { mutableStateOf(1) }
    val itemsPerPage = 20

    LaunchedEffect(currentPage) {
        val startIso = "${startDate}T00:00:00Z"
        val endIso = "${endDate}T00:00:00Z"
        viewModel.loadAuditLemmit(startIso = startIso, endIso = endIso, limit = itemsPerPage, offset = (currentPage - 1) * itemsPerPage)
    }

    val audit = state.auditLemmit

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = "Auditoria Lemmit",
                subtitle = "Custos, uso por usuario e ultimas consultas.",
            )
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startDate,
                            onValueChange = { startDate = it },
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus(),
                            label = { Text("Data inicio (YYYY-MM-DD)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = endDate,
                            onValueChange = { endDate = it },
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus(),
                            label = { Text("Data fim (YYYY-MM-DD)") },
                            singleLine = true,
                        )
                    }

                    Button(
                        onClick = {
                            currentPage = 1
                            val startIso = "${startDate}T00:00:00Z"
                            val endIso = "${endDate}T00:00:00Z"
                            viewModel.loadAuditLemmit(startIso = startIso, endIso = endIso, limit = itemsPerPage, offset = 0)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.adminFeatureLoading,
                    ) {
                        Text("Atualizar auditoria")
                    }
                }
            }
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resumo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Total consultas: ${audit.cards.totalConsultas}")
                    Text("Bem sucedidas: ${audit.cards.bemSucedidas}")
                    Text("Com erro: ${audit.cards.comErro}")
                    Text("Custo total: ${formatCurrency(audit.cards.custoTotal)}")
                    Text("Limite ajustado: ${formatCurrency(audit.cards.totalLimiteAjustado)}")
                }
            }
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usuarios por consultas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (audit.usuarioConsulta.isEmpty()) {
                        Text("Sem dados para o periodo.")
                    } else {
                        audit.usuarioConsulta.forEach { row ->
                            Text("${row.nome.ifBlank { "Sem nome" }} - ${row.consultas}")
                        }
                    }
                }
            }
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Usuarios por custo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (audit.usuarioCusto.isEmpty()) {
                        Text("Sem dados para o periodo.")
                    } else {
                        audit.usuarioCusto.forEach { row ->
                            Text("${row.nome.ifBlank { "Sem nome" }} - ${formatCurrency(row.custoTotal)}")
                        }
                    }
                }
            }
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ultimas consultas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (audit.ultimasConsultas.isEmpty()) {
                        Text("Sem consultas registradas.")
                    }
                }
            }
        }

        if (audit.ultimasConsultas.isNotEmpty()) {
            items(audit.ultimasConsultas) { consulta ->
                WebCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(consulta.nome.ifBlank { "Desconhecido" }, fontWeight = FontWeight.Medium)
                        Text("CPF: ${consulta.cpf.ifBlank { "N/A" }}")
                        Text("Hora: ${formatDateTime(consulta.hora)}")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1) { Text("Anterior") }
                    Text("Pagina $currentPage")
                    Button(onClick = { currentPage++ }, enabled = audit.ultimasConsultas.size == itemsPerPage) { Text("Proxima") }
                }
            }
        }
    }
}

private fun formatCurrency(value: Double): String {
    return runCatching {
        java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR")).format(value)
    }.getOrDefault(value.toString())
}

private fun formatDateTime(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)
}
