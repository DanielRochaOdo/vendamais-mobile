package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.EmpresaResumo
import br.com.vendamais.mobile.data.models.EmpresaSearchType
import br.com.vendamais.mobile.data.models.MobileProfile
import br.com.vendamais.mobile.data.models.TeamMemberOption
import br.com.vendamais.mobile.ui.CadastroWorkspaceState
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft

@Composable
fun CadastroOperationsCard(
    profile: MobileProfile?,
    workspace: CadastroWorkspaceState,
    vendedores: List<TeamMemberOption>,
    adesionistas: List<TeamMemberOption>,
    onSearchTypeChange: (EmpresaSearchType) -> Unit,
    onSearchValueChange: (String) -> Unit,
    onSearchEmpresa: () -> Unit,
    onSelectEmpresa: (EmpresaResumo) -> Unit,
    onClearEmpresa: () -> Unit,
    onCpfChange: (String) -> Unit,
    onSelectedVendedorChange: (String) -> Unit,
    onSelectedAdesionistaChange: (String) -> Unit,
    onConsultarCpf: () -> Unit,
) {
    val canChooseVendedor = profile?.role in setOf(
        "ADMINISTRADOR",
        "ADMIN",
        "GERENTE",
        "GESTOR",
        "SUPERVISOR",
        "CADASTRO",
        "ADESIONISTA",
    )
    val isVendedorProfile = profile?.role == "VENDEDOR"
    val canChooseAdesionista = profile?.role in setOf(
        "ADMINISTRADOR",
        "GERENTE",
        "GESTOR",
        "SUPERVISOR",
        "VENDEDOR",
        "CADASTRO",
    )

    var cpfFieldValue by remember {
        mutableStateOf(TextFieldValue(""))
    }
    val cpfDigitsFromState = workspace.cpfValue.filter(Char::isDigit).take(11)
    LaunchedEffect(cpfDigitsFromState) {
        if (cpfDigitsFromState != cpfFieldValue.text) {
            cpfFieldValue = TextFieldValue(
                text = cpfDigitsFromState,
                selection = TextRange(cpfDigitsFromState.length),
            )
        }
    }

    WebCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenHeading(
                title = "Nova Adesao",
                subtitle = "Selecione a empresa, informe o CPF e inicie o cadastro.",
            )

            if (workspace.selectedEmpresa == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SearchTypeRow(
                        selected = workspace.empresaSearchType,
                        onSelected = onSearchTypeChange,
                    )

                    OutlinedTextField(
                        value = workspace.empresaSearchValue,
                        onValueChange = onSearchValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = when (workspace.empresaSearchType) {
                                    EmpresaSearchType.CODIGO -> "Codigo da empresa"
                                    EmpresaSearchType.CNPJ -> "CNPJ"
                                    EmpresaSearchType.NOME -> "Nome da empresa"
                                },
                            )
                        },
                        placeholder = {
                            Text(
                                text = when (workspace.empresaSearchType) {
                                    EmpresaSearchType.CODIGO -> "Digite o codigo"
                                    EmpresaSearchType.CNPJ -> "00.000.000/0000-00"
                                    EmpresaSearchType.NOME -> "Digite o nome da empresa"
                                },
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (workspace.empresaSearchType) {
                                EmpresaSearchType.CODIGO -> KeyboardType.Number
                                EmpresaSearchType.CNPJ -> KeyboardType.Number
                                EmpresaSearchType.NOME -> KeyboardType.Text
                            },
                            imeAction = ImeAction.Search,
                        ),
                        singleLine = true,
                    )

                    Button(
                        onClick = onSearchEmpresa,
                        enabled = !workspace.operationLoading && workspace.empresaSearchValue.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (workspace.operationLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Buscar empresa")
                        }
                    }

                    if (workspace.empresaSearchResults.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            workspace.empresaSearchResults.forEach { empresa ->
                                Surface(
                                    onClick = { onSelectEmpresa(empresa) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = empresa.nomeFantasia.ifBlank { empresa.razaoSocial.ifBlank { "Empresa sem nome" } },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (empresa.razaoSocial.isNotBlank()) {
                                            Text(
                                                text = empresa.razaoSocial,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        Text(
                                            text = buildString {
                                                append("Codigo ${empresa.id}")
                                                if (empresa.cnpj.isNotBlank()) append(" - ${empresa.cnpj}")
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                WebCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = workspace.selectedEmpresa.nomeFantasia.ifBlank {
                                workspace.selectedEmpresa.razaoSocial.ifBlank { "Empresa sem nome" }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (workspace.selectedEmpresa.razaoSocial.isNotBlank()) {
                            Text(
                                text = workspace.selectedEmpresa.razaoSocial,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = buildString {
                                append("Codigo ${workspace.selectedEmpresa.id}")
                                if (workspace.selectedEmpresa.cnpj.isNotBlank()) append(" - ${workspace.selectedEmpresa.cnpj}")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        workspace.selectedEmpresa.observacoesResolvidas
                            ?.takeIf { it.isNotBlank() }
                            ?.let { observacao ->
                                Text(
                                    text = observacao,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        TextButton(onClick = onClearEmpresa) {
                            Text("Alterar empresa")
                        }
                    }
                }
            }

            if (workspace.selectedEmpresa != null) {
                if (canChooseVendedor) {
                    SelectionField(
                        label = "Vendedor",
                        value = vendedores
                            .firstOrNull { it.id == workspace.selectedVendedorId }
                            ?.toSelectionLabel()
                            ?: "Selecione um vendedor",
                        options = vendedores.map { it.id to it.toSelectionLabel() },
                        onSelected = onSelectedVendedorChange,
                    )
                    if (vendedores.isEmpty()) {
                        Text(
                            text = "Nenhum vendedor disponível. Entre em contato com o administrador.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else if (isVendedorProfile) {
                    OutlinedTextField(
                        value = buildString {
                            append(profile?.name.orEmpty())
                            append(" - Codigo ")
                            append(profile?.externalId?.takeIf { it.isNotBlank() } ?: "-")
                        },
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Vendedor") },
                        enabled = false,
                    )
                }

                if (canChooseAdesionista) {
                    SelectionField(
                        label = "Adesionista",
                        value = adesionistas
                            .firstOrNull { it.id == workspace.selectedAdesionistaId }
                            ?.toSelectionLabel()
                            ?: "Selecione um adesionista",
                        options = listOf("" to "Nenhum adesionista") + adesionistas.map { it.id to it.toSelectionLabel() },
                        onSelected = onSelectedAdesionistaChange,
                    )
                }

                OutlinedTextField(
                    value = cpfFieldValue,
                    onValueChange = { value ->
                        val digits = value.text.filter(Char::isDigit).take(11)
                        cpfFieldValue = TextFieldValue(
                            text = digits,
                            selection = TextRange(digits.length),
                        )
                        onCpfChange(digits)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CPF") },
                    placeholder = { Text("000.000.000-00") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { onConsultarCpf() },
                        onDone = { onConsultarCpf() },
                    ),
                    visualTransformation = CpfVisualTransformation(),
                    singleLine = true,
                )

                Button(
                    onClick = onConsultarCpf,
                    enabled = !workspace.operationLoading && workspace.cpfValue.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (workspace.operationLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Consultar CPF")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTypeRow(
    selected: EmpresaSearchType,
    onSelected: (EmpresaSearchType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchTypePill(
            label = "Codigo",
            selected = selected == EmpresaSearchType.CODIGO,
            onClick = { onSelected(EmpresaSearchType.CODIGO) },
        )
        SearchTypePill(
            label = "CNPJ",
            selected = selected == EmpresaSearchType.CNPJ,
            onClick = { onSelected(EmpresaSearchType.CNPJ) },
        )
        SearchTypePill(
            label = "Nome",
            selected = selected == EmpresaSearchType.NOME,
            onClick = { onSelected(EmpresaSearchType.NOME) },
        )
    }
}

@Composable
private fun SearchTypePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) EmeraldSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Emerald else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun TeamMemberOption.toSelectionLabel(): String {
    val codigo = externalId?.takeIf { it.isNotBlank() } ?: "-"
    return "$name - Codigo $codigo"
}

private class CpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(11)
        val originalToTransformed = IntArray(digits.length + 1)
        val formatted = buildString {
            originalToTransformed[0] = 0
            digits.forEachIndexed { index, char ->
                append(char)
                if (index == 2 && digits.length > 3) append('.')
                if (index == 5 && digits.length > 6) append('.')
                if (index == 8 && digits.length > 9) append('-')
                originalToTransformed[index + 1] = length
            }
        }
        val transformedToOriginal = IntArray(formatted.length + 1) { offset ->
            formatted.take(offset).count(Char::isDigit).coerceAtMost(digits.length)
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return originalToTransformed[offset.coerceIn(0, digits.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transformedToOriginal[offset.coerceIn(0, formatted.length)]
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

