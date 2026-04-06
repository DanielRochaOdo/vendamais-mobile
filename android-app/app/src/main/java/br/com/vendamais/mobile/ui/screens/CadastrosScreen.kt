package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import br.com.vendamais.mobile.data.models.CadastroResumo
import br.com.vendamais.mobile.data.models.EmpresaResumo
import br.com.vendamais.mobile.data.models.EmpresaSearchType
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.CadastroAreaTab
import br.com.vendamais.mobile.ui.CadastroFiltro
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Amber100
import br.com.vendamais.mobile.ui.theme.Amber500
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Slate100
import br.com.vendamais.mobile.ui.theme.Slate500
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private enum class CadastrosSearchType {
    NOME,
    CPF,
    CODIGO,
    EMPRESA,
}

@Composable
fun CadastrosScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onTabChange: (CadastroAreaTab) -> Unit,
    onFilterChange: (CadastroFiltro) -> Unit,
    onCadastroClick: (String) -> Unit,
    onSearchTypeChange: (EmpresaSearchType) -> Unit,
    onSearchValueChange: (String) -> Unit,
    onSearchEmpresa: () -> Unit,
    onSelectEmpresa: (EmpresaResumo) -> Unit,
    onClearEmpresa: () -> Unit,
    onCpfChange: (String) -> Unit,
    onSelectedVendedorChange: (String) -> Unit,
    onSelectedAdesionistaChange: (String) -> Unit,
    onConsultarCpf: () -> Unit,
    onLinkSearchTypeChange: (EmpresaSearchType) -> Unit,
    onLinkSearchValueChange: (String) -> Unit,
    onLinkSearchEmpresa: () -> Unit,
    onLinkSelectEmpresa: (EmpresaResumo) -> Unit,
    onLinkClearEmpresa: () -> Unit,
    onGenerateLink: () -> Unit,
    onRegenerateLink: (String) -> Unit,
    onDeleteLink: (String) -> Unit,
    onOpenWebApp: (() -> Unit)? = null,
) {
    var showInclusaoDialog by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var searchType by rememberSaveable { mutableStateOf(CadastrosSearchType.NOME) }
    var searchValue by rememberSaveable { mutableStateOf("") }
    var tipoCadastroFiltro by rememberSaveable { mutableStateOf("todos") }
    var vendedorFiltro by rememberSaveable { mutableStateOf("") }
    var dataInicioFiltro by rememberSaveable { mutableStateOf("") }
    var dataFimFiltro by rememberSaveable { mutableStateOf("") }

    val baseCadastros = state.cadastros.filter {
        when (state.cadastroFiltro) {
            CadastroFiltro.PENDENTES -> it.status == "incompleto"
            CadastroFiltro.ENVIADOS -> it.status == "enviado"
        }
    }
    val searchValueTrim = searchValue.trim()
    val vendedorFiltroTrim = vendedorFiltro.trim()
    val dataInicio = parseFilterDate(dataInicioFiltro)
    val dataFim = parseFilterDate(dataFimFiltro)
    val filteredCadastros = baseCadastros.filter { cadastro ->
        val matchesBusca = when {
            searchValueTrim.isBlank() -> true
            searchType == CadastrosSearchType.CPF -> {
                val query = searchValueTrim.filter(Char::isDigit)
                if (query.isBlank()) true else cadastro.cpf.filter(Char::isDigit).contains(query)
            }
            searchType == CadastrosSearchType.CODIGO -> cadastro.id.contains(searchValueTrim, ignoreCase = true)
            searchType == CadastrosSearchType.EMPRESA -> cadastro.empresaNome.orEmpty().contains(searchValueTrim, ignoreCase = true)
            else -> {
                cadastro.nome.orEmpty().contains(searchValueTrim, ignoreCase = true) ||
                    cadastro.empresaNome.orEmpty().contains(searchValueTrim, ignoreCase = true)
            }
        }

        val matchesTipo = when (tipoCadastroFiltro) {
            "cadastro" -> cadastro.tipoCadastro == "cadastro"
            "inclusao_dependente" -> cadastro.tipoCadastro == "inclusao_dependente"
            else -> true
        }

        val matchesVendedor = if (vendedorFiltroTrim.isBlank()) {
            true
        } else {
            cadastro.vendedorNome.orEmpty().contains(vendedorFiltroTrim, ignoreCase = true)
        }

        val cadastroData = parseCadastroDate(cadastro.updatedAt) ?: parseCadastroDate(cadastro.createdAt)
        val matchesDataInicio = dataInicio?.let { start -> cadastroData?.let { !it.isBefore(start) } ?: false } ?: true
        val matchesDataFim = dataFim?.let { end -> cadastroData?.let { !it.isAfter(end) } ?: false } ?: true

        matchesBusca && matchesTipo && matchesVendedor && matchesDataInicio && matchesDataFim
    }
    val pendentesCount = state.cadastroStats.cadastro_incompletos + state.cadastroStats.inclusao_incompletos
    val completosCount = state.cadastroStats.cadastro_enviados + state.cadastroStats.inclusao_enviados

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = "Cadastro",
                subtitle = "Consulte CPF e gerencie cadastros",
            )
        }

        item {
            CadastroTabBar(
                selectedTab = state.cadastroTab,
                pendentesCount = pendentesCount,
                completosCount = completosCount,
                onTabSelected = { tab ->
                    when (tab) {
                        CadastroAreaTab.INCOMPLETOS -> onFilterChange(CadastroFiltro.PENDENTES)
                        CadastroAreaTab.COMPLETOS -> onFilterChange(CadastroFiltro.ENVIADOS)
                        else -> Unit
                    }
                    onTabChange(tab)
                },
            )
        }

        when (state.cadastroTab) {
            CadastroAreaTab.NOVO -> {
                item {
                    CadastroOperationsCard(
                        profile = state.profile,
                        workspace = state.cadastroWorkspace,
                        vendedores = state.vendedores,
                        adesionistas = state.adesionistas,
                        onSearchTypeChange = onSearchTypeChange,
                        onSearchValueChange = onSearchValueChange,
                        onSearchEmpresa = onSearchEmpresa,
                        onSelectEmpresa = onSelectEmpresa,
                        onClearEmpresa = onClearEmpresa,
                        onCpfChange = onCpfChange,
                        onSelectedVendedorChange = onSelectedVendedorChange,
                        onSelectedAdesionistaChange = onSelectedAdesionistaChange,
                        onConsultarCpf = onConsultarCpf,
                    )
                }
            }

            CadastroAreaTab.LINK -> {
                item {
                    CadastroLinksCard(
                        workspace = state.linkWorkspace,
                        onSearchTypeChange = onLinkSearchTypeChange,
                        onSearchValueChange = onLinkSearchValueChange,
                        onSearchEmpresa = onLinkSearchEmpresa,
                        onSelectEmpresa = onLinkSelectEmpresa,
                        onClearEmpresa = onLinkClearEmpresa,
                        onGenerateLink = onGenerateLink,
                        onRegenerateLink = onRegenerateLink,
                        onDeleteLink = onDeleteLink,
                    )
                }
            }

            CadastroAreaTab.DEPENDENTE -> {
                item {
                    WebCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "Inclusao de Dependente",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Clique no botao para buscar um responsavel financeiro e adicionar novos dependentes.",
                                color = Slate500,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { showInclusaoDialog = true }) {
                                Text("Iniciar Inclusao")
                            }
                        }
                    }
                }
            }

            CadastroAreaTab.INCOMPLETOS,
            CadastroAreaTab.COMPLETOS,
            -> {
                item {
                    CadastrosFilterPanel(
                        expanded = showFilters,
                        onToggleExpanded = { showFilters = !showFilters },
                        searchType = searchType,
                        searchValue = searchValue,
                        onSearchTypeChange = { searchType = it },
                        onSearchValueChange = { searchValue = it },
                        tipoCadastroFiltro = tipoCadastroFiltro,
                        onTipoCadastroFiltroChange = { tipoCadastroFiltro = it },
                        vendedorFiltro = vendedorFiltro,
                        onVendedorFiltroChange = { vendedorFiltro = it },
                        dataInicioFiltro = dataInicioFiltro,
                        onDataInicioFiltroChange = { dataInicioFiltro = it },
                        dataFimFiltro = dataFimFiltro,
                        onDataFimFiltroChange = { dataFimFiltro = it },
                        filteredCount = filteredCadastros.size,
                        totalCount = baseCadastros.size,
                        onClearFilters = {
                            searchType = CadastrosSearchType.NOME
                            searchValue = ""
                            tipoCadastroFiltro = "todos"
                            vendedorFiltro = ""
                            dataInicioFiltro = ""
                            dataFimFiltro = ""
                        },
                    )
                }

                if (state.cadastrosLoading && !state.cadastrosLoaded) {
                    item {
                        WebCard {
                            Text(
                                text = "Carregando cadastros...",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else if (filteredCadastros.isEmpty()) {
                    item {
                        WebCard {
                            Text(
                                text = if (state.cadastroTab == CadastroAreaTab.INCOMPLETOS) {
                                    "Nenhuma adesão pendente encontrada."
                                } else {
                                    "Nenhuma adesão cadastrada encontrada."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                } else {
                    items(filteredCadastros) { cadastro ->
                        CadastroCard(
                            cadastro = cadastro,
                            onClick = { onCadastroClick(cadastro.id) },
                        )
                    }
                }
            }
        }
    }

    if (showInclusaoDialog) {
        InclusaoDependenteDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showInclusaoDialog = false },
            onSuccess = {
                showInclusaoDialog = false
                onTabChange(CadastroAreaTab.INCOMPLETOS)
                onFilterChange(CadastroFiltro.PENDENTES)
            },
        )
    }
}

@Composable
private fun CadastrosFilterPanel(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    searchType: CadastrosSearchType,
    searchValue: String,
    onSearchTypeChange: (CadastrosSearchType) -> Unit,
    onSearchValueChange: (String) -> Unit,
    tipoCadastroFiltro: String,
    onTipoCadastroFiltroChange: (String) -> Unit,
    vendedorFiltro: String,
    onVendedorFiltroChange: (String) -> Unit,
    dataInicioFiltro: String,
    onDataInicioFiltroChange: (String) -> Unit,
    dataFimFiltro: String,
    onDataFimFiltroChange: (String) -> Unit,
    filteredCount: Int,
    totalCount: Int,
    onClearFilters: () -> Unit,
) {
    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Filtros de busca",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$filteredCount de $totalCount cadastros",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                    )
                }
                TextButton(onClick = onToggleExpanded) {
                    Text(if (expanded) "Ocultar" else "Mostrar")
                }
            }

            if (expanded) {
                Text("Buscar por", style = MaterialTheme.typography.labelMedium, color = Slate500)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipButton(
                        label = "Nome",
                        selected = searchType == CadastrosSearchType.NOME,
                        onClick = { onSearchTypeChange(CadastrosSearchType.NOME) },
                    )
                    FilterChipButton(
                        label = "CPF",
                        selected = searchType == CadastrosSearchType.CPF,
                        onClick = { onSearchTypeChange(CadastrosSearchType.CPF) },
                    )
                    FilterChipButton(
                        label = "Codigo",
                        selected = searchType == CadastrosSearchType.CODIGO,
                        onClick = { onSearchTypeChange(CadastrosSearchType.CODIGO) },
                    )
                    FilterChipButton(
                        label = "Empresa",
                        selected = searchType == CadastrosSearchType.EMPRESA,
                        onClick = { onSearchTypeChange(CadastrosSearchType.EMPRESA) },
                    )
                }

                OutlinedTextField(
                    value = searchValue,
                    onValueChange = onSearchValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when (searchType) {
                                CadastrosSearchType.NOME -> "Nome ou razao social"
                                CadastrosSearchType.CPF -> "CPF"
                                CadastrosSearchType.CODIGO -> "Codigo do cadastro"
                                CadastrosSearchType.EMPRESA -> "Nome da empresa"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (searchType == CadastrosSearchType.CPF) {
                            KeyboardType.Number
                        } else {
                            KeyboardType.Text
                        },
                    ),
                    singleLine = true,
                )

                Text("Tipo de cadastro", style = MaterialTheme.typography.labelMedium, color = Slate500)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChipButton(
                        label = "Todos",
                        selected = tipoCadastroFiltro == "todos",
                        onClick = { onTipoCadastroFiltroChange("todos") },
                    )
                    FilterChipButton(
                        label = "Cadastro",
                        selected = tipoCadastroFiltro == "cadastro",
                        onClick = { onTipoCadastroFiltroChange("cadastro") },
                    )
                    FilterChipButton(
                        label = "Incl. dep.",
                        selected = tipoCadastroFiltro == "inclusao_dependente",
                        onClick = { onTipoCadastroFiltroChange("inclusao_dependente") },
                    )
                }

                OutlinedTextField(
                    value = vendedorFiltro,
                    onValueChange = onVendedorFiltroChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Vendedor (nome)") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = dataInicioFiltro,
                    onValueChange = onDataInicioFiltroChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Data inicio (AAAA-MM-DD)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = dataFimFiltro,
                    onValueChange = onDataFimFiltroChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Data fim (AAAA-MM-DD)") },
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onClearFilters) {
                        Text("Limpar filtros")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldSoft else Slate100,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = if (selected) Emerald else Slate500,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CadastroTabBar(
    selectedTab: CadastroAreaTab,
    pendentesCount: Int,
    completosCount: Int,
    onTabSelected: (CadastroAreaTab) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CadastroTabButton(
                label = "Nova Adesão",
                selected = selectedTab == CadastroAreaTab.NOVO,
                onClick = { onTabSelected(CadastroAreaTab.NOVO) },
                modifier = Modifier.weight(1f),
            )
            CadastroTabButton(
                label = "Link",
                selected = selectedTab == CadastroAreaTab.LINK,
                onClick = { onTabSelected(CadastroAreaTab.LINK) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CadastroTabButton(
                label = "Incluir Dep.",
                selected = selectedTab == CadastroAreaTab.DEPENDENTE,
                onClick = { onTabSelected(CadastroAreaTab.DEPENDENTE) },
                modifier = Modifier.weight(1f),
            )
            CadastroTabButton(
                label = "Adesões Pendentes",
                selected = selectedTab == CadastroAreaTab.INCOMPLETOS,
                badge = pendentesCount.takeIf { it > 0 }?.toString(),
                onClick = { onTabSelected(CadastroAreaTab.INCOMPLETOS) },
                modifier = Modifier.weight(1f),
            )
        }
        CadastroTabButton(
            label = "Cadastradas",
            selected = selectedTab == CadastroAreaTab.COMPLETOS,
            badge = completosCount.takeIf { it > 0 }?.toString(),
            onClick = { onTabSelected(CadastroAreaTab.COMPLETOS) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CadastroTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) EmeraldSoft else Slate100,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (selected) Emerald else Slate500,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (badge != null) {
                BoxBadge(
                    value = badge,
                    bgColor = if (selected) Emerald else Amber100,
                    textColor = if (selected) androidx.compose.ui.graphics.Color.White else Amber500,
                )
            }
        }
    }
}

@Composable
private fun BoxBadge(
    value: String,
    bgColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = value,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun CadastroDetailDialog(
    cadastro: CadastroDetalhe,
    sendingCadastro: Boolean,
    onSendCadastro: (() -> Unit)?,
    onOpenWebApp: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onOpenWebApp?.let { openWeb ->
                    TextButton(onClick = openWeb) {
                        Text("Web")
                    }
                }
                if (cadastro.status != "enviado" && onSendCadastro != null) {
                    TextButton(onClick = onSendCadastro, enabled = !sendingCadastro) {
                        if (sendingCadastro) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            Text("Enviar")
                        }
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar")
                }
            }
        },
        title = { Text(cadastro.nome ?: "Cadastro ${cadastro.id.take(8)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Status", cadastro.status)
                DetailLine("Tipo", tipoCadastroLabel(cadastro.tipoCadastro))
                DetailLine("CPF", cadastro.cpf)
                DetailLine("Empresa", cadastro.empresaNome ?: "-")
                DetailLine("Vendedor", cadastro.vendedorNome ?: "-")
                DetailLine("Adesionista", cadastro.adesionistaNome ?: "-")
                DetailLine("Matrícula", cadastro.numeroMatricula ?: "-")
                DetailLine("Arquivo", cadastro.arquivoPath ?: "-")
                DetailLine("Data de envio", cadastro.dataEnvio?.let(::formatDateTime) ?: "-")
                DetailLine("Atualizado", formatDateTime(cadastro.updatedAt))
                if (!cadastro.motivoBloqueio.isNullOrBlank()) {
                    DetailLine("Motivo do bloqueio", cadastro.motivoBloqueio)
                }
            }
        },
    )
}

@Composable
private fun CadastroCard(
    cadastro: CadastroResumo,
    onClick: () -> Unit,
) {
    WebCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = cadastro.nome ?: "Sem nome",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${tipoCadastroLabel(cadastro.tipoCadastro)} • ${statusLabel(cadastro.status)}",
                style = MaterialTheme.typography.labelLarge,
                color = if (cadastro.status == "enviado") Emerald else Amber500,
            )
            Text("CPF: ${formatCpf(cadastro.cpf)}", style = MaterialTheme.typography.bodyMedium)
            Text("Empresa: ${cadastro.empresaNome ?: "-"}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Atualizado em ${formatDateTime(cadastro.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatDateTime(value: String): String {
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }.getOrDefault(value)
}

private fun tipoCadastroLabel(value: String): String {
    return when (value) {
        "inclusao_dependente" -> "Inclusão de Dependente"
        else -> "Cadastro"
    }
}

private fun statusLabel(value: String): String {
    return when (value) {
        "enviado" -> "Cadastrado"
        "incompleto" -> "Pendente"
        else -> value
    }
}

private fun formatCpf(value: String): String {
    val digits = value.filter(Char::isDigit).take(11)
    if (digits.length != 11) return value
    return "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
}

private fun parseFilterDate(value: String): LocalDate? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

private fun parseCadastroDate(value: String): LocalDate? {
    return runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
}

