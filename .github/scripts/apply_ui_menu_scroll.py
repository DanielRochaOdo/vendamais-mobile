from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, got {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"updated {label}")


cadastros = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastrosScreen.kt")
old_tab_bar = '''@Composable
private fun CadastroTabBar(
    selectedTab: CadastroAreaTab,
    pendentesCount: Int,
    completosCount: Int,
    onTabSelected: (CadastroAreaTab) -> Unit,
) {
    val tabs = listOf(
        CadastroAreaTab.NOVO,
        CadastroAreaTab.LINK,
        CadastroAreaTab.DEPENDENTE,
        CadastroAreaTab.INCOMPLETOS,
        CadastroAreaTab.COMPLETOS,
    )
    val labels = listOf(
        "Novo",
        "Link",
        "Depend.",
        if (pendentesCount > 0) "Pend. $pendentesCount" else "Pend.",
        if (completosCount > 0) "Env. $completosCount" else "Enviados",
    )
    VendaSectionTabs(
        items = labels,
        selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
        onSelected = { index -> tabs.getOrNull(index)?.let(onTabSelected) },
    )
}
'''
new_tab_bar = '''@Composable
private fun CadastroTabBar(
    selectedTab: CadastroAreaTab,
    pendentesCount: Int,
    completosCount: Int,
    onTabSelected: (CadastroAreaTab) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CadastroTabButton(
            label = "Novo",
            selected = selectedTab == CadastroAreaTab.NOVO,
            onClick = { onTabSelected(CadastroAreaTab.NOVO) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CadastroTabButton(
                label = "Dependente",
                selected = selectedTab == CadastroAreaTab.DEPENDENTE,
                onClick = { onTabSelected(CadastroAreaTab.DEPENDENTE) },
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
                label = "Enviadas",
                selected = selectedTab == CadastroAreaTab.COMPLETOS,
                onClick = { onTabSelected(CadastroAreaTab.COMPLETOS) },
                modifier = Modifier.weight(1f),
                badge = completosCount.takeIf { it > 0 }?.toString(),
            )
            CadastroTabButton(
                label = "Pendente",
                selected = selectedTab == CadastroAreaTab.INCOMPLETOS,
                onClick = { onTabSelected(CadastroAreaTab.INCOMPLETOS) },
                modifier = Modifier.weight(1f),
                badge = pendentesCount.takeIf { it > 0 }?.toString(),
            )
        }
    }
}
'''
replace_once(cadastros, old_tab_bar, new_tab_bar, "CadastroTabBar")
text = cadastros.read_text()
text = text.replace("import br.com.vendamais.mobile.ui.components.VendaSectionTabs\n", "", 1)
cadastros.write_text(text)

inclusion = Path("android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/InclusaoDependenteDialog.kt")
text = inclusion.read_text()
if "import androidx.compose.foundation.ExperimentalFoundationApi\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.Arrangement\n",
        "import androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.layout.Arrangement\n",
        1,
    )
if "import androidx.compose.foundation.relocation.BringIntoViewRequester\n" not in text:
    text = text.replace(
        "import androidx.compose.foundation.rememberScrollState\n",
        "import androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.relocation.BringIntoViewRequester\nimport androidx.compose.foundation.relocation.bringIntoViewRequester\n",
        1,
    )
if "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun InclusaoDependenteDialog(" not in text:
    text = text.replace(
        "@Composable\nfun InclusaoDependenteDialog(\n",
        "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nfun InclusaoDependenteDialog(\n",
        1,
    )
inclusion.write_text(text)

replace_once(
    inclusion,
    '''    val resultados = remember { mutableStateListOf<ResponsavelFinanceiroResumo>() }
    val dependentes = remember {
''',
    '''    val resultados = remember { mutableStateListOf<ResponsavelFinanceiroResumo>() }
    val firstResponsavelResultBringIntoViewRequester = remember { BringIntoViewRequester() }
    val dependentes = remember {
''',
    "dependent result requester",
)

old_results_ui = '''                resultados.forEach { item ->
                    Surface(
                        onClick = {
                            responsavelSelecionado = item
                            empresaCodigo = item.codigoEmpresa
                            empresaNome = item.empresa
                            empresaPlanosRaw = null
                            localError = null
                            cpfValidationErrors.clear()
                            consultedCpfByIndex.clear()
                            if (!isContinuacao) {
                                dependentes.clear()
                            }
                            scope.launch {
                                runCatching {
                                    viewModel.searchEmpresaDirect(item.codigoEmpresa.toString(), br.com.vendamais.mobile.data.models.EmpresaSearchType.CODIGO).firstOrNull()
                                }.onSuccess { empresa ->
                                    if (empresa != null) {
                                        val invalidCodes = state.cadastroWorkspace.config?.codigosEmpresaInvalidos.orEmpty()
                                        val situacaoCode = empresa.codigoSituacao?.toString()
                                        if (!situacaoCode.isNullOrBlank() && invalidCodes.contains(situacaoCode)) {
                                            empresaRaw = null
                                            empresaPlanosRaw = null
                                            viewModel.resolveCadastroOverlay(
                                                CadastroModalSignal(
                                                    empresaCanceladaNome = empresa.nomeFantasia.ifBlank {
                                                        empresa.razaoSocial.ifBlank { item.empresa }
                                                    },
                                                ),
                                            )
                                            localError = "A empresa selecionada esta bloqueada para cadastro."
                                            return@onSuccess
                                        }

                                        empresaRaw = empresa.raw
                                        empresaPlanosRaw = empresa.precoPlano ?: extractPlanosRawFromEmpresa(empresa.raw)
                                        empresaNome = empresa.nomeFantasia.ifBlank { empresa.razaoSocial.ifBlank { item.empresa } }
                                        empresaCodigo = empresa.codigo ?: empresa.id
                                        empresa.observacoesResolvidas
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { observacoes ->
                                                viewModel.resolveCadastroOverlay(
                                                    CadastroModalSignal(
                                                        empresaObservacaoNome = empresa.nomeFantasia.ifBlank {
                                                            empresa.razaoSocial.ifBlank { item.empresa }
                                                        },
                                                        empresaObservacaoTexto = observacoes,
                                                    ),
                                                )
                                            }
                                    } else {
                                        empresaPlanosRaw = null
                                        viewModel.resolveCadastroOverlay(
                                            CadastroModalSignal(
                                                empresaNaoIdentificada = true,
                                                empresaNaoIdentificadaRequired = true,
                                            ),
                                        )
                                    }
                                }.onFailure {
                                    empresaPlanosRaw = null
                                    viewModel.resolveCadastroOverlay(
                                        CadastroModalSignal(
                                            empresaNaoIdentificada = true,
                                            empresaNaoIdentificadaRequired = true,
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        Text("${item.nome} - Codigo ${item.codigo} - ${item.empresa}", modifier = Modifier.fillMaxWidth().padding(10.dp))
                    }
                }
'''
new_results_ui = '''                if (resultados.isNotEmpty()) {
                    resultados.forEachIndexed { index, item ->
                        Surface(
                            onClick = {
                                responsavelSelecionado = item
                                empresaCodigo = item.codigoEmpresa
                                empresaNome = item.empresa
                                empresaPlanosRaw = null
                                localError = null
                                cpfValidationErrors.clear()
                                consultedCpfByIndex.clear()
                                if (!isContinuacao) {
                                    dependentes.clear()
                                }
                                scope.launch {
                                    runCatching {
                                        viewModel.searchEmpresaDirect(item.codigoEmpresa.toString(), br.com.vendamais.mobile.data.models.EmpresaSearchType.CODIGO).firstOrNull()
                                    }.onSuccess { empresa ->
                                        if (empresa != null) {
                                            val invalidCodes = state.cadastroWorkspace.config?.codigosEmpresaInvalidos.orEmpty()
                                            val situacaoCode = empresa.codigoSituacao?.toString()
                                            if (!situacaoCode.isNullOrBlank() && invalidCodes.contains(situacaoCode)) {
                                                empresaRaw = null
                                                empresaPlanosRaw = null
                                                viewModel.resolveCadastroOverlay(
                                                    CadastroModalSignal(
                                                        empresaCanceladaNome = empresa.nomeFantasia.ifBlank {
                                                            empresa.razaoSocial.ifBlank { item.empresa }
                                                        },
                                                    ),
                                                )
                                                localError = "A empresa selecionada esta bloqueada para cadastro."
                                                return@onSuccess
                                            }

                                            empresaRaw = empresa.raw
                                            empresaPlanosRaw = empresa.precoPlano ?: extractPlanosRawFromEmpresa(empresa.raw)
                                            empresaNome = empresa.nomeFantasia.ifBlank { empresa.razaoSocial.ifBlank { item.empresa } }
                                            empresaCodigo = empresa.codigo ?: empresa.id
                                            empresa.observacoesResolvidas
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { observacoes ->
                                                    viewModel.resolveCadastroOverlay(
                                                        CadastroModalSignal(
                                                            empresaObservacaoNome = empresa.nomeFantasia.ifBlank {
                                                                empresa.razaoSocial.ifBlank { item.empresa }
                                                            },
                                                            empresaObservacaoTexto = observacoes,
                                                        ),
                                                    )
                                                }
                                        } else {
                                            empresaPlanosRaw = null
                                            viewModel.resolveCadastroOverlay(
                                                CadastroModalSignal(
                                                    empresaNaoIdentificada = true,
                                                    empresaNaoIdentificadaRequired = true,
                                                ),
                                            )
                                        }
                                    }.onFailure {
                                        empresaPlanosRaw = null
                                        viewModel.resolveCadastroOverlay(
                                            CadastroModalSignal(
                                                empresaNaoIdentificada = true,
                                                empresaNaoIdentificadaRequired = true,
                                            ),
                                        )
                                    }
                                }
                            },
                            modifier = if (index == 0) {
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(firstResponsavelResultBringIntoViewRequester)
                            } else {
                                Modifier.fillMaxWidth()
                            },
                        ) {
                            Text("${item.nome} - Codigo ${item.codigo} - ${item.empresa}", modifier = Modifier.fillMaxWidth().padding(10.dp))
                        }
                    }
                    LaunchedEffect(resultados.firstOrNull()?.codigo) {
                        firstResponsavelResultBringIntoViewRequester.bringIntoView()
                    }
                }
'''
replace_once(inclusion, old_results_ui, new_results_ui, "dependent search results scroll")
