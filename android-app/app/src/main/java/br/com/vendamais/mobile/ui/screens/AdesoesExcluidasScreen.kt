package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.CadastroExcluidoItem
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime

@Composable
fun AdesoesExcluidasScreen(state: AppUiState, viewModel: AppViewModel) {
    var selected by remember { mutableStateOf<CadastroExcluidoItem?>(null) }
    var nome by rememberSaveable { mutableStateOf("") }; var cpf by rememberSaveable { mutableStateOf("") }; var start by rememberSaveable { mutableStateOf("") }; var end by rememberSaveable { mutableStateOf("") }; var exclusor by rememberSaveable { mutableStateOf("") }; var page by rememberSaveable { mutableStateOf(1) }
    val perPage = 15
    LaunchedEffect(Unit) { viewModel.loadCadastrosExcluidos(1000) }
    val filtered = state.cadastrosExcluidos.filter { item ->
        val data = runCatching { item.dadosCadastro.jsonObject }.getOrNull()
        val title = data?.get("nome")?.jsonPrimitive?.contentOrNull.orEmpty()
        val dependentes = runCatching { data?.get("dependentes")?.jsonArray }.getOrNull().orEmpty()
        val matchNome = nome.isBlank() || title.contains(nome, true) || dependentes.any { runCatching { it.jsonObject["nome"]?.jsonPrimitive?.contentOrNull.orEmpty().contains(nome, true) }.getOrDefault(false) }
        val cpfData = data?.get("cpf")?.jsonPrimitive?.contentOrNull.orEmpty().filter(Char::isDigit)
        val matchCpf = cpf.filter(Char::isDigit).let { it.isBlank() || cpfData.contains(it) }
        val date = runCatching { OffsetDateTime.parse(item.excluidoEm).toLocalDate() }.getOrNull()
        val matchStart = start.takeIf { it.isNotBlank() }?.let { s -> runCatching { !date!!.isBefore(LocalDate.parse(s)) }.getOrDefault(false) } ?: true
        val matchEnd = end.takeIf { it.isNotBlank() }?.let { e -> runCatching { !date!!.isAfter(LocalDate.parse(e)) }.getOrDefault(false) } ?: true
        val matchExclusor = exclusor.isBlank() || item.excluidoPor == exclusor
        matchNome && matchCpf && matchStart && matchEnd && matchExclusor
    }
    val totalPages = ((filtered.size + perPage - 1) / perPage).coerceAtLeast(1); if (page > totalPages) page = totalPages
    val paged = filtered.drop((page - 1) * perPage).take(perPage)
    val exclusores = state.cadastrosExcluidos.distinctBy { it.excluidoPor }.sortedBy { it.excluidoPorNome }

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeading("Adesoes Excluidas", "Historico de exclusoes logicas com motivo e auditoria.") }
        item { WebCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filtros", fontWeight = FontWeight.SemiBold); OutlinedTextField(nome, { nome = it; page = 1 }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome Titular/Dependente") }); OutlinedTextField(cpf, { cpf = it.filter(Char::isDigit).take(11); page = 1 }, modifier = Modifier.fillMaxWidth(), label = { Text("CPF Titular") }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it; page = 1 }, modifier = Modifier.weight(1f), label = { Text("Data Inicio") }); OutlinedTextField(end, { end = it; page = 1 }, modifier = Modifier.weight(1f), label = { Text("Data Fim") }) }; SelectionField("Excluido por", exclusores.firstOrNull { it.excluidoPor == exclusor }?.excluidoPorNome ?: "Todos", listOf("" to "Todos") + exclusores.map { it.excluidoPor to it.excluidoPorNome }, onSelected = { exclusor = it; page = 1 }); TextButton(onClick = { nome = ""; cpf = ""; start = ""; end = ""; exclusor = ""; page = 1 }) { Text("Limpar filtros") }; Text("Mostrando ${paged.size} de ${filtered.size}")
        } } }
        if (paged.isEmpty()) item { WebCard { Text("Nenhuma exclusao encontrada.") } } else items(paged) { item ->
            val obj = runCatching { item.dadosCadastro.jsonObject }.getOrNull(); WebCard(modifier = Modifier.clickable { selected = item }) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(obj?.get("nome")?.jsonPrimitive?.contentOrNull ?: "Nome nao informado", fontWeight = FontWeight.SemiBold); Text("Empresa: ${obj?.get("empresa_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Vendedor: ${obj?.get("vendedor_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Motivo: ${item.motivoExclusao}", color = MaterialTheme.colorScheme.error); Text("Excluido por ${item.excluidoPorNome} (${item.excluidoPorRole})"); Text(formatDateTime(item.excluidoEm)) } }
        }
        item { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Button(onClick = { if (page > 1) page-- }, enabled = page > 1) { Text("Anterior") }; Text("Pagina $page de $totalPages"); Button(onClick = { if (page < totalPages) page++ }, enabled = page < totalPages) { Text("Proxima") } } }
    }
    selected?.let { current ->
        val obj = runCatching { current.dadosCadastro.jsonObject }.getOrNull(); val deps = runCatching { obj?.get("dependentes")?.jsonArray }.getOrNull().orEmpty()
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(obj?.get("nome")?.jsonPrimitive?.contentOrNull ?: "Detalhes da exclusao") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("CPF: ${obj?.get("cpf")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Empresa: ${obj?.get("empresa_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Vendedor: ${obj?.get("vendedor_nome")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Tipo: ${obj?.get("tipo_cadastro")?.jsonPrimitive?.contentOrNull ?: "-"}"); Text("Motivo: ${current.motivoExclusao}", color = MaterialTheme.colorScheme.error); Text("Excluido por: ${current.excluidoPorNome} (${current.excluidoPorRole})"); Text("Data: ${formatDateTime(current.excluidoEm)}"); if (deps.isNotEmpty()) { Text("Dependentes", fontWeight = FontWeight.SemiBold); deps.forEach { dep -> val d = runCatching { dep.jsonObject }.getOrNull(); Text("• ${d?.get("nome")?.jsonPrimitive?.contentOrNull ?: "Dependente"} - ${d?.get("cpf")?.jsonPrimitive?.contentOrNull ?: ""}") } } } }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Fechar") } })
    }
}

private fun formatDateTime(value: String): String = runCatching { OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(value)
