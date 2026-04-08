package br.com.vendamais.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import br.com.vendamais.mobile.data.models.TeamMemberOption
import br.com.vendamais.mobile.data.remote.InclusaoBuscaTipo
import br.com.vendamais.mobile.data.remote.ResponsavelFinanceiroResumo
import br.com.vendamais.mobile.data.remote.UploadedTempFile
import br.com.vendamais.mobile.domain.cadastro.CadastroApiErrorMapper
import br.com.vendamais.mobile.domain.cadastro.CadastroModalSignal
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Amber100
import br.com.vendamais.mobile.ui.theme.Amber500
import br.com.vendamais.mobile.ui.theme.BrandOrange
import br.com.vendamais.mobile.ui.theme.EmeraldDark
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Red100
import br.com.vendamais.mobile.ui.theme.Red500
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.NumberFormat
import java.io.File
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

private data class DependenteFormState(
    val nome: String = "",
    val cpf: TextFieldValue = TextFieldValue(""),
    val dataNascimento: TextFieldValue = TextFieldValue(""),
    val sexo: Int = -1,
    val parentesco: Int = 0,
    val plano: Int = 0,
    val planoValor: String = "0.00",
    val nomeMae: String = "",
    val arquivo: UploadedTempFile? = null,
    val saved: Boolean = false,
    val uploading: Boolean = false,
    val expanded: Boolean = true,
)

private data class PlanoOption(
    val codigo: Int,
    val nome: String,
    val valorTitular: Double? = null,
    val valorDependente: Double? = null,
    val label: String = nome,
)

private enum class InclusaoMessageTone {
    WARNING,
    ALERT,
    ERROR,
    SUCCESS,
}

@Composable
fun InclusaoDependenteDialog(
    state: AppUiState,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    cadastro: CadastroDetalhe? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = state.profile
    val isContinuacao = cadastro != null

    var tipoBusca by rememberSaveable { mutableStateOf(InclusaoBuscaTipo.CODIGO) }
    var valorBusca by rememberSaveable(stateSaver = textFieldValueSaver()) { mutableStateOf(TextFieldValue("")) }
    var loadingBusca by rememberSaveable { mutableStateOf(false) }
    var salvando by rememberSaveable { mutableStateOf(false) }
    var enviando by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var localNotice by rememberSaveable { mutableStateOf<String?>(null) }
    var successDialogMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var previewingArquivoIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    var selectedVendedorId by rememberSaveable {
        mutableStateOf(
            when {
                profile?.role == "VENDEDOR" -> profile.id
                state.cadastroWorkspace.selectedVendedorId.isNotBlank() -> state.cadastroWorkspace.selectedVendedorId
                else -> ""
            },
        )
    }
    var selectedAdesionistaId by rememberSaveable {
        mutableStateOf(if (profile?.role == "ADESIONISTA") profile.id else "")
    }
    var selectedStatusId by rememberSaveable { mutableStateOf(cadastro?.statusAdesaoId.orEmpty()) }

    var responsavelSelecionado by remember {
        mutableStateOf(
            cadastro?.let {
                ResponsavelFinanceiroResumo(
                    codigo = it.responsavelFinanceiroCodigo ?: 0,
                    codigoEmpresa = it.empresaCodigo ?: 0,
                    nome = it.responsavelFinanceiroNome.orEmpty(),
                    cpf = it.responsavelFinanceiroCpf.orEmpty(),
                    empresa = it.empresaNome.orEmpty(),
                )
            },
        )
    }
    var empresaCodigo by rememberSaveable { mutableStateOf(cadastro?.empresaCodigo ?: 0) }
    var empresaNome by rememberSaveable { mutableStateOf(cadastro?.empresaNome.orEmpty()) }
    var empresaRaw by remember { mutableStateOf<JsonElement?>(cadastro?.empresaRaw) }
    val resultados = remember { mutableStateListOf<ResponsavelFinanceiroResumo>() }
    val dependentes = remember {
        mutableStateListOf<DependenteFormState>().apply {
            if (cadastro != null) {
                addAll(parseDependentes(cadastro))
                if (isEmpty()) add(buildDependenteBase(cadastro))
            }
        }
    }

    var targetUploadIndex by remember { mutableStateOf<Int?>(null) }
    var showArquivoSourceModal by rememberSaveable { mutableStateOf(false) }
    var cameraCapturePath by rememberSaveable { mutableStateOf("") }

    suspend fun uploadDependenteArquivo(
        index: Int,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        validateUpload(fileName, mimeType, bytes.size.toLong())
        dependentes[index].arquivo?.path?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { viewModel.deleteTempFile(path) }
        }
        val cpf = dependentes[index].cpf.text.ifBlank { "0" }
        val uploaded = viewModel.uploadTempFile(
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            prefix = if (isContinuacao) "dependentes-continuar/$cpf" else "dependentes-temp/$cpf",
        )
        updateDependente(dependentes, index) { it.copy(arquivo = uploaded, uploading = false, saved = false) }
        localNotice = "Arquivo carregado com sucesso."
    }

    suspend fun openDependenteArquivoPreview(index: Int) {
        val dependente = dependentes.getOrNull(index)
        val arquivo = dependente?.arquivo
        if (arquivo == null || arquivo.path.isBlank()) {
            localError = "Nenhum arquivo anexado para visualizacao."
            return
        }
        previewingArquivoIndex = index
        runCatching {
            val bytes = viewModel.downloadTempFile(arquivo.path)
            val uri = writePreviewFile(
                context = context,
                fileName = arquivo.nome.ifBlank { arquivo.path.substringAfterLast('/') },
                bytes = bytes,
            )
            val mime = resolvePreviewMimeType(arquivo.nome.ifBlank { arquivo.path.substringAfterLast('/') })
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Visualizar arquivo"))
        }.onFailure { throwable ->
            localError = throwable.message ?: "Nao foi possivel abrir o arquivo."
        }
        previewingArquivoIndex = null
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val index = targetUploadIndex
        targetUploadIndex = null
        if (uri == null || index == null || index !in dependentes.indices) return@rememberLauncherForActivityResult
        scope.launch {
            updateDependente(dependentes, index) { it.copy(uploading = true) }
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Nao foi possivel ler o arquivo.")
                uploadDependenteArquivo(
                    index = index,
                    fileName = resolveFileName(context, uri),
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    bytes = bytes,
                )
            }.onSuccess {
            }.onFailure { throwable ->
                updateDependente(dependentes, index) { it.copy(uploading = false) }
                localError = throwable.message ?: "Erro ao carregar arquivo."
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val index = targetUploadIndex
        val cameraPath = cameraCapturePath
        targetUploadIndex = null
        cameraCapturePath = ""
        if (!success || index == null || index !in dependentes.indices || cameraPath.isBlank()) return@rememberLauncherForActivityResult
        scope.launch {
            updateDependente(dependentes, index) { it.copy(uploading = true) }
            runCatching {
                val cameraFile = File(cameraPath)
                val bytes = cameraFile.readBytes()
                uploadDependenteArquivo(
                    index = index,
                    fileName = cameraFile.name,
                    mimeType = "image/jpeg",
                    bytes = bytes,
                )
            }.onFailure { throwable ->
                updateDependente(dependentes, index) { it.copy(uploading = false) }
                localError = throwable.message ?: "Erro ao capturar arquivo pela camera."
            }
            runCatching { File(cameraPath).delete() }
        }
    }

    fun resolveVendedor(): TeamMemberOption? {
        return if (profile?.role == "VENDEDOR") {
            TeamMemberOption(profile.id, profile.name, profile.email, profile.externalId)
        } else {
            state.vendedores.firstOrNull { it.id == selectedVendedorId }
        }
    }

    fun resolveAdesionista(): TeamMemberOption? {
        return if (profile?.role == "ADESIONISTA") {
            TeamMemberOption(profile.id, profile.name, profile.email, profile.externalId)
        } else {
            state.adesionistas.firstOrNull { it.id == selectedAdesionistaId }
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        cadastro?.id,
        cadastro?.vendedorCodigo,
        cadastro?.adesionistaCodigo,
        state.cadastroWorkspace.selectedVendedorId,
        state.vendedores,
        state.adesionistas,
        profile?.role,
        profile?.id,
    ) {
        if (profile?.role == "VENDEDOR") {
            selectedVendedorId = profile.id
        } else if (selectedVendedorId.isBlank() && state.cadastroWorkspace.selectedVendedorId.isNotBlank()) {
            selectedVendedorId = state.cadastroWorkspace.selectedVendedorId
                .takeIf { selected -> state.vendedores.any { it.id == selected } }
                .orEmpty()
        } else if (selectedVendedorId.isBlank() && !cadastro?.vendedorCodigo.isNullOrBlank()) {
            selectedVendedorId = state.vendedores
                .firstOrNull { it.externalId == cadastro?.vendedorCodigo }
                ?.id
                .orEmpty()
        }

        if (profile?.role == "ADESIONISTA") {
            selectedAdesionistaId = profile.id
        } else if (selectedAdesionistaId.isBlank() && !cadastro?.adesionistaCodigo.isNullOrBlank()) {
            selectedAdesionistaId = state.adesionistas
                .firstOrNull { it.externalId == cadastro?.adesionistaCodigo }
                ?.id
                .orEmpty()
        }
    }

    fun validateDependente(dep: DependenteFormState, index: Int): String? {
        val iso = toIsoDateOrNull(dep.dataNascimento.text)
        if (dep.nome.isBlank()) return "Dependente ${index + 1}: nome obrigatorio."
        if (iso == null) return "Dependente ${index + 1}: data de nascimento invalida."
        if (!isUnder18(iso) && dep.cpf.text.length != 11) return "Dependente ${index + 1}: CPF obrigatorio."
        if (dep.cpf.text.isNotBlank() && !validateCpf(dep.cpf.text)) return "Dependente ${index + 1}: CPF invalido."
        if (dep.sexo !in setOf(0, 1)) return "Dependente ${index + 1}: sexo obrigatorio."
        if (dep.parentesco == 0) return "Dependente ${index + 1}: parentesco obrigatorio."
        if (dep.plano == 0) return "Dependente ${index + 1}: plano obrigatorio."
        if (dep.nomeMae.isBlank()) return "Dependente ${index + 1}: nome da mae obrigatorio."
        if (state.cadastroWorkspace.config?.exigirArquivo == true && dep.arquivo == null) {
            return "Dependente ${index + 1}: arquivo obrigatorio."
        }
        return null
    }

    fun resolveEmpresaCodigo(responsavel: ResponsavelFinanceiroResumo?): Int {
        return when {
            empresaCodigo > 0 -> empresaCodigo
            responsavel?.codigoEmpresa != null && responsavel.codigoEmpresa > 0 -> responsavel.codigoEmpresa
            else -> 0
        }
    }

    fun resolveEmpresaNome(responsavel: ResponsavelFinanceiroResumo?): String {
        return empresaNome
            .takeIf { it.isNotBlank() }
            ?: responsavel?.empresa.orEmpty()
    }

    fun ensureEmpresaIdentificada(responsavel: ResponsavelFinanceiroResumo?): Boolean {
        val codigo = resolveEmpresaCodigo(responsavel)
        val nome = resolveEmpresaNome(responsavel)
        if (codigo > 0 && nome.isNotBlank()) return true

        viewModel.resolveCadastroOverlay(
            CadastroModalSignal(
                empresaNaoIdentificada = true,
                empresaNaoIdentificadaRequired = true,
            ),
        )
        localError = "Selecione uma empresa valida antes de continuar."
        return false
    }

    suspend fun salvarPendente() {
        val responsavel = responsavelSelecionado ?: run {
            localError = "Selecione um responsavel financeiro."
            return
        }
        if (!ensureEmpresaIdentificada(responsavel)) return
        val vendedor = resolveVendedor()
        if (profile?.role != "VENDEDOR" && state.vendedores.isEmpty()) {
            localError = "Nenhum vendedor disponível. Entre em contato com o administrador."
            return
        }
        if (vendedor?.externalId.isNullOrBlank()) {
            localError = "Selecione um vendedor valido."
            return
        }

        val base = if (isContinuacao) dependentes.toList() else dependentes.filter { it.saved }
        if (base.isEmpty()) {
            localError = if (isContinuacao) "Adicione dependentes." else "Salve pelo menos um dependente."
            return
        }
        base.forEachIndexed { index, dep ->
            validateDependente(dep, index)?.let { error ->
                localError = error
                return
            }
        }

        val payload = buildCadastroPayload(
            profileId = profile?.id.orEmpty(),
            teamId = profile?.teamId,
            responsavel = responsavel,
            empresaCodigo = resolveEmpresaCodigo(responsavel),
            empresaNome = resolveEmpresaNome(responsavel),
            empresaRaw = empresaRaw,
            status = "incompleto",
            statusAdesaoId = selectedStatusId,
            vendedor = vendedor,
            adesionista = resolveAdesionista(),
            dependentes = base,
        )

        if (cadastro != null) {
            viewModel.updateCadastroRecord(cadastro.id, payload)
            localNotice = "Rascunho salvo com sucesso."
            onSuccess()
        } else {
            viewModel.createCadastroRecord(payload)
            localNotice = "Dependente(s) salvo(s) como pendente."
            onSuccess()
            onDismiss()
        }
    }

    suspend fun enviarDependentes() {
        val responsavel = responsavelSelecionado ?: run {
            localError = "Selecione um responsavel financeiro."
            return
        }
        if (!ensureEmpresaIdentificada(responsavel)) return
        val vendedor = resolveVendedor()
        if (profile?.role != "VENDEDOR" && state.vendedores.isEmpty()) {
            localError = "Nenhum vendedor disponível. Entre em contato com o administrador."
            return
        }
        val codigoParceiro = if (profile?.role == "VENDEDOR") {
            profile.externalId?.toIntOrNull()
        } else {
            vendedor?.externalId?.toIntOrNull()
        } ?: run {
            localError = "Vendedor sem codigo externo valido."
            return
        }

        val base = if (isContinuacao) dependentes.toList() else dependentes.filter { it.saved }
        if (base.isEmpty()) {
            localError = "Nenhum dependente valido para envio."
            return
        }
        base.forEachIndexed { index, dep ->
            validateDependente(dep, index)?.let { error ->
                localError = error
                return
            }
        }

        val adesionista = resolveAdesionista()
        val prePayload = buildCadastroPayload(
            profileId = profile?.id.orEmpty(),
            teamId = profile?.teamId,
            responsavel = responsavel,
            empresaCodigo = resolveEmpresaCodigo(responsavel),
            empresaNome = resolveEmpresaNome(responsavel),
            empresaRaw = empresaRaw,
            status = "incompleto",
            statusAdesaoId = selectedStatusId,
            vendedor = vendedor ?: TeamMemberOption("", "", "", null),
            adesionista = adesionista,
            dependentes = base,
        )

        var cadastroId = cadastro?.id
        if (cadastroId == null) {
            cadastroId = viewModel.createCadastroRecord(prePayload).id
        } else {
            viewModel.updateCadastroRecord(cadastroId, prePayload)
        }

        val response = viewModel.enviarInclusaoDependente(
            buildErpPayload(
                responsavelCodigo = responsavel.codigo,
                parceiroCodigo = codigoParceiro,
                adesionistaCodigo = adesionista?.externalId?.toIntOrNull() ?: 0,
                funcionarioCodigo = profile?.externalId?.toIntOrNull() ?: 0,
                dependentes = base,
            ),
        )

        val codes = extractDependenteCodes(response)
        val funcionario = profile?.externalId?.toIntOrNull() ?: 0
        if (funcionario > 0) {
            base.forEachIndexed { idx, dep ->
                val file = dep.arquivo ?: return@forEachIndexed
                val code = codes.getOrNull(idx) ?: return@forEachIndexed
                val uploaded = runCatching {
                    viewModel.uploadDependenteDocumento(funcionario, code, file.path, file.nome)
                }.getOrDefault(false)
                if (!uploaded) {
                    runCatching {
                        viewModel.enqueueDependenteUpload(cadastroId, funcionario, code, file.path, file.nome)
                    }
                } else {
                    runCatching { viewModel.deleteTempFile(file.path) }
                }
            }
        }

        viewModel.updateCadastroRecord(
            cadastroId,
            buildJsonObject {
                put("status", "enviado")
                put("tipo_cadastro", "inclusao_dependente")
                selectedStatusId.takeIf { it.isNotBlank() }?.let { put("status_adesao_id", it) }
                put("erp_response", response)
            },
        )
        successDialogMessage = "Dependentes incluidos com sucesso."
    }

    val planoOptions = remember(empresaRaw, state.planosMap) {
        extractPlanosFromEmpresa(empresaRaw, state.planosMap)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (isContinuacao) "Continuar Inclusao de Dependentes" else "Inclusao de Dependente", style = MaterialTheme.typography.headlineSmall)

                if (profile?.role != "VENDEDOR") {
                    SelectionField(
                        label = "Vendedor",
                        value = state.vendedores
                            .firstOrNull { it.id == selectedVendedorId }
                            ?.let { "${it.name} - ${it.externalId ?: "-"}" }
                            ?: "Selecione um vendedor",
                        options = state.vendedores.map { it.id to "${it.name} - ${it.externalId ?: "-"}" },
                        onSelected = { selectedVendedorId = it },
                    )
                    if (state.vendedores.isEmpty()) {
                        Text(
                            text = "Nenhum vendedor disponivel. Entre em contato com o administrador.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = "${profile.name} - ${profile.externalId ?: "-"}",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Vendedor") },
                        enabled = false,
                    )
                }

                if (!isContinuacao) {
                    WebCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { tipoBusca = InclusaoBuscaTipo.CODIGO }, enabled = tipoBusca != InclusaoBuscaTipo.CODIGO) { Text("Codigo") }
                                Button(onClick = { tipoBusca = InclusaoBuscaTipo.CPF }, enabled = tipoBusca != InclusaoBuscaTipo.CPF) { Text("CPF") }
                            }
                            OutlinedTextField(
                                value = valorBusca,
                                onValueChange = { valorBusca = if (tipoBusca == InclusaoBuscaTipo.CPF) sanitizeDigitsInput(it, 11) else it.copy(text = it.text.filter(Char::isDigit).take(20)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (tipoBusca == InclusaoBuscaTipo.CODIGO) "Codigo associado" else "CPF associado") },
                                visualTransformation = if (tipoBusca == InclusaoBuscaTipo.CPF) DependenteCpfVisualTransformation() else VisualTransformation.None,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        val query = valorBusca.text.trim()
                                        if (query.isBlank()) {
                                            localError = "Informe um valor para busca."
                                            return@launch
                                        }
                                        if (profile?.role != "VENDEDOR" && state.vendedores.isEmpty()) {
                                            localError = "Nenhum vendedor disponível. Entre em contato com o administrador."
                                            return@launch
                                        }
                                        val vendedor = resolveVendedor()
                                        if (vendedor?.externalId.isNullOrBlank()) {
                                            localError = "Selecione um vendedor antes de buscar."
                                            return@launch
                                        }
                                        loadingBusca = true
                                        resultados.clear()
                                        runCatching { viewModel.buscarResponsaveisFinanceiros(tipoBusca, query) }
                                            .onSuccess { lista ->
                                                resultados.addAll(lista)
                                                if (lista.isEmpty()) localError = "Nenhum associado encontrado."
                                            }
                                            .onFailure { throwable -> localError = throwable.message ?: "Falha na busca." }
                                        loadingBusca = false
                                    }
                                },
                                enabled = !loadingBusca,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (loadingBusca) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Buscar")
                            }
                        }
                    }
                }

                resultados.forEach { item ->
                    Surface(
                        onClick = {
                            responsavelSelecionado = item
                            empresaCodigo = item.codigoEmpresa
                            empresaNome = item.empresa
                            localError = null
                            scope.launch {
                                runCatching {
                                    viewModel.searchEmpresaDirect(item.codigoEmpresa.toString(), br.com.vendamais.mobile.data.models.EmpresaSearchType.CODIGO).firstOrNull()
                                }.onSuccess { empresa ->
                                    if (empresa != null) {
                                        empresaRaw = empresa.raw
                                        empresaNome = empresa.nomeFantasia.ifBlank { empresa.razaoSocial.ifBlank { item.empresa } }
                                        empresaCodigo = empresa.codigo ?: empresa.id
                                    } else {
                                        viewModel.resolveCadastroOverlay(
                                            CadastroModalSignal(
                                                empresaNaoIdentificada = true,
                                                empresaNaoIdentificadaRequired = true,
                                            ),
                                        )
                                    }
                                }.onFailure {
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

                responsavelSelecionado?.let { responsavel ->
                    WebCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Responsavel: ${responsavel.nome}")
                            Text("CPF: ${formatCpf(responsavel.cpf)}")
                            Text("Empresa: ${empresaNome.ifBlank { responsavel.empresa }}")
                        }
                    }
                    SelectionField(
                        label = "Adesionista",
                        value = state.adesionistas.firstOrNull { it.id == selectedAdesionistaId }?.name ?: if (profile?.role == "ADESIONISTA") profile.name else "Nenhum",
                        options = listOf("" to "Nenhum") + state.adesionistas.map { it.id to "${it.name} - ${it.externalId ?: "-"}" },
                        enabled = profile?.role != "ADESIONISTA",
                        onSelected = { selectedAdesionistaId = it },
                    )
                    SelectionField(
                        label = "Status da Adesao (opcional)",
                        value = state.statusAdesoes.firstOrNull { it.id == selectedStatusId }?.nome ?: "Nenhum",
                        options = listOf("" to "Selecione") + state.statusAdesoes.map { it.id to it.nome },
                        onSelected = { selectedStatusId = it },
                    )
                }

                if (responsavelSelecionado != null && dependentes.isEmpty()) {
                    Button(
                        onClick = { dependentes.add(DependenteFormState()) },
                        enabled = !salvando && !enviando,
                    ) {
                        Text("Adicionar dependente")
                    }
                }

                dependentes.forEachIndexed { index, dep ->
                    WebCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dependente ${index + 1}${if (dep.saved) " (salvo)" else ""}")
                            val isCollapsed = dep.saved && !dep.expanded
                            if (isCollapsed) {
                                Surface(
                                    onClick = {
                                        updateDependente(dependentes, index) { current ->
                                            current.copy(expanded = true)
                                        }
                                    },
                                    tonalElevation = 1.dp,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(dep.nome.ifBlank { "Dependente sem nome" }, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "CPF: ${formatCpf(dep.cpf.text)} • Plano: ${planoOptions.firstOrNull { it.codigo == dep.plano }?.nome ?: "-"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            "Toque para expandir e revisar os dados.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = {
                                            updateDependente(dependentes, index) { current ->
                                                current.copy(expanded = true)
                                            }
                                        },
                                    ) { Text("Expandir") }
                                    if (index == dependentes.lastIndex) {
                                        TextButton(
                                            onClick = { dependentes.add(DependenteFormState()) },
                                            enabled = !salvando && !enviando,
                                        ) { Text("Adicionar dependente") }
                                    }
                                    TextButton(
                                        onClick = { dependentes.removeAt(index) },
                                        enabled = !enviando && !salvando,
                                    ) { Text("Remover") }
                                }
                            } else {
                                OutlinedTextField(
                                    value = dep.nome,
                                    onValueChange = { value ->
                                        updateDependente(dependentes, index) { current ->
                                            current.copy(nome = value, saved = false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome") },
                                )
                                OutlinedTextField(
                                    value = dep.cpf,
                                    onValueChange = { value ->
                                        updateDependente(dependentes, index) { current ->
                                            current.copy(cpf = sanitizeDigitsInput(value, 11), saved = false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("CPF") },
                                    visualTransformation = DependenteCpfVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                OutlinedTextField(
                                    value = dep.dataNascimento,
                                    onValueChange = { value ->
                                        updateDependente(dependentes, index) { current ->
                                            current.copy(dataNascimento = sanitizeDigitsInput(value, 8), saved = false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Data nascimento") },
                                    visualTransformation = DependenteDateVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                SelectionField(label = "Sexo", value = when (dep.sexo) { 1 -> "Masculino"; 0 -> "Feminino"; else -> "Selecione" }, options = listOf(-1 to "Selecione", 1 to "Masculino", 0 to "Feminino"), onSelected = { v -> updateDependente(dependentes, index) { it.copy(sexo = v, saved = false) } })
                                SelectionField(label = "Parentesco", value = state.parentescosMap.firstOrNull { it.parentescoId == dep.parentesco }?.label ?: "Selecione", options = listOf(0 to "Selecione") + state.parentescosMap.filter { it.ativo && it.parentescoId != 1 }.map { it.parentescoId to it.label }, onSelected = { v -> updateDependente(dependentes, index) { it.copy(parentesco = v, saved = false) } })
                                SelectionField(
                                    label = "Plano",
                                    value = planoOptions.firstOrNull { it.codigo == dep.plano }?.label ?: "Selecione",
                                    options = listOf(0 to "Selecione") + planoOptions.map { it.codigo to it.label },
                                    highlighted = true,
                                    onSelected = { v ->
                                        updateDependente(dependentes, index) { current ->
                                            val selectedOption = planoOptions.firstOrNull { it.codigo == v }
                                            val planoValor = selectedOption
                                                ?.valorDependente
                                                ?.let(::formatPlanoValorDot)
                                                ?: current.planoValor
                                            current.copy(
                                                plano = v,
                                                planoValor = planoValor,
                                                saved = false,
                                            )
                                        }
                                    },
                                )
                                OutlinedTextField(
                                    value = dep.nomeMae,
                                    onValueChange = { value ->
                                        updateDependente(dependentes, index) { current ->
                                            current.copy(nomeMae = value, saved = false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome da mae") },
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { targetUploadIndex = index; showArquivoSourceModal = true }, enabled = !dep.uploading && !enviando && !salvando) {
                                        if (dep.uploading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (dep.arquivo == null) "Arquivo" else "Trocar arquivo")
                                    }
                                    if (dep.arquivo != null) {
                                        TextButton(
                                            onClick = { scope.launch { openDependenteArquivoPreview(index) } },
                                            enabled = previewingArquivoIndex != index,
                                        ) {
                                            Text(
                                                if (previewingArquivoIndex == index) {
                                                    "Abrindo..."
                                                } else {
                                                    dep.arquivo.nome
                                                },
                                            )
                                        }
                                        TextButton(onClick = { scope.launch { runCatching { viewModel.deleteTempFile(dep.arquivo.path) }; updateDependente(dependentes, index) { it.copy(arquivo = null, saved = false) } } }) { Text("Remover arquivo") }
                                    }
                                    TextButton(onClick = { dependentes.removeAt(index) }, enabled = !enviando && !salvando) { Text("Remover") }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (!isContinuacao) {
                                        Button(
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                validateDependente(dep, index)?.let { localError = it }
                                                    ?: updateDependente(dependentes, index) {
                                                        it.copy(saved = true, expanded = false)
                                                    }
                                            },
                                        ) { Text("Salvar") }
                                    }
                                    if (index == dependentes.lastIndex) {
                                        TextButton(
                                            modifier = Modifier.weight(1f),
                                            onClick = { dependentes.add(DependenteFormState()) },
                                            enabled = !salvando && !enviando,
                                        ) { Text("Adicionar") }
                                    }
                                }
                            }
                        }
                    }
                }

                localNotice?.let { message ->
                    val (container, textColor) = inclusaoMessageToneColors(resolveInclusaoMessageTone(message))
                    WebCard {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = container,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = message,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        modifier = Modifier.weight(0.9f),
                        onClick = onDismiss,
                        enabled = !salvando && !enviando,
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (salvando || enviando) return@Button
                            salvando = true
                            scope.launch {
                                runCatching { salvarPendente() }.onFailure { localError = it.message }
                                salvando = false
                            }
                        },
                        enabled = !salvando && !enviando && responsavelSelecionado != null,
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salvar")
                        }
                    }
                    Button(
                        modifier = Modifier.weight(1.1f),
                        onClick = {
                            if (salvando || enviando) return@Button
                            enviando = true
                            scope.launch {
                                runCatching { enviarDependentes() }
                                    .onFailure { throwable ->
                                        val mapped = CadastroApiErrorMapper.mapErpError(throwable.message)
                                        if (mapped != null) {
                                            viewModel.resolveCadastroOverlay(CadastroModalSignal(erpError = mapped))
                                        } else {
                                            localError = throwable.message
                                        }
                                    }
                                enviando = false
                            }
                        },
                        enabled = !salvando && !enviando && responsavelSelecionado != null,
                    ) {
                        if (enviando) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Incluir")
                        }
                    }
                }
            }
        }
    }

    if (showArquivoSourceModal) {
        AlertDialog(
            onDismissRequest = {
                showArquivoSourceModal = false
                targetUploadIndex = null
            },
            title = { Text("Anexar arquivo") },
            text = { Text("Escolha a origem do arquivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showArquivoSourceModal = false
                        filePicker.launch("*/*")
                    },
                ) {
                    Text("Documentos")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showArquivoSourceModal = false
                            runCatching {
                                val (uri, path) = createCameraCaptureUri(context)
                                cameraCapturePath = path
                                cameraLauncher.launch(uri)
                            }.onFailure { throwable ->
                                targetUploadIndex = null
                                localError = throwable.message ?: "Nao foi possivel iniciar a camera."
                            }
                        },
                    ) {
                        Text("Camera")
                    }
                    TextButton(
                        onClick = {
                            showArquivoSourceModal = false
                            targetUploadIndex = null
                        },
                    ) {
                        Text("Cancelar")
                    }
                }
            },
        )
    }

    localError?.let { message ->
        val tone = resolveInclusaoMessageTone(message)
        val (container, textColor) = inclusaoMessageToneColors(tone)
        val title = when (tone) {
            InclusaoMessageTone.ERROR -> "Erro"
            InclusaoMessageTone.ALERT -> "Atencao"
            InclusaoMessageTone.WARNING -> "Aviso"
            InclusaoMessageTone.SUCCESS -> "Sucesso"
        }
        AlertDialog(
            onDismissRequest = { localError = null },
            title = {
                Text(
                    title,
                    color = if (tone == InclusaoMessageTone.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = container,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = message,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { localError = null }) { Text("OK") } },
        )
    }

    successDialogMessage?.let { message ->
        val (container, textColor) = inclusaoMessageToneColors(InclusaoMessageTone.SUCCESS)
        AlertDialog(
            onDismissRequest = {
                successDialogMessage = null
                onSuccess()
                onDismiss()
            },
            title = { Text("Sucesso", color = textColor) },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = container,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = message,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        successDialogMessage = null
                        onSuccess()
                        onDismiss()
                    },
                ) {
                    Text("OK")
                }
            },
        )
    }
}

private fun updateDependente(
    list: MutableList<DependenteFormState>,
    index: Int,
    updater: (DependenteFormState) -> DependenteFormState,
) {
    if (index !in list.indices) return
    list[index] = updater(list[index])
}

private fun textFieldValueSaver(): Saver<TextFieldValue, Any> {
    return Saver(
        save = { listOf(it.text, it.selection.start, it.selection.end) },
        restore = {
            @Suppress("UNCHECKED_CAST")
            val data = it as List<Any>
            TextFieldValue(data[0] as String, TextRange(data[1] as Int, data[2] as Int))
        },
    )
}

private fun sanitizeDigitsInput(value: TextFieldValue, maxDigits: Int): TextFieldValue {
    val digits = value.text.filter(Char::isDigit).take(maxDigits)
    val digitsBeforeCursor = value.text
        .take(value.selection.start.coerceIn(0, value.text.length))
        .count(Char::isDigit)
        .coerceAtMost(digits.length)
    return TextFieldValue(digits, TextRange(digitsBeforeCursor))
}

private fun toIsoDateOrNull(rawDigits: String): String? {
    val digits = rawDigits.filter(Char::isDigit)
    if (digits.length != 8) return null
    val day = digits.substring(0, 2).toIntOrNull() ?: return null
    val month = digits.substring(2, 4).toIntOrNull() ?: return null
    val year = digits.substring(4, 8).toIntOrNull() ?: return null
    if (day !in 1..31 || month !in 1..12 || year !in 1900..2100) return null
    return "%04d-%02d-%02d".format(year, month, day)
}

private fun isUnder18(isoDate: String): Boolean {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return false
    return Period.between(date, LocalDate.now()).years < 18
}

private fun formatDateForErp(iso: String): String {
    val parts = iso.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

private fun formatCpf(value: String): String {
    val digits = value.filter(Char::isDigit).take(11)
    return when (digits.length) {
        in 0..3 -> digits
        in 4..6 -> "${digits.take(3)}.${digits.drop(3)}"
        in 7..9 -> "${digits.take(3)}.${digits.substring(3, 6)}.${digits.drop(6)}"
        else -> "${digits.take(3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.drop(9)}"
    }
}

private fun validateCpf(cpfDigits: String): Boolean {
    val cpf = cpfDigits.filter(Char::isDigit)
    if (cpf.length != 11) return false
    if ((0..9).any { cpf == it.toString().repeat(11) }) return false

    var sum = 0
    for (i in 0..8) sum += cpf[i].digitToInt() * (10 - i)
    var remainder = 11 - (sum % 11)
    if (remainder >= 10) remainder = 0
    if (remainder != cpf[9].digitToInt()) return false

    sum = 0
    for (i in 0..9) sum += cpf[i].digitToInt() * (11 - i)
    remainder = 11 - (sum % 11)
    if (remainder >= 10) remainder = 0
    return remainder == cpf[10].digitToInt()
}

private fun buildCadastroPayload(
    profileId: String,
    teamId: String?,
    responsavel: ResponsavelFinanceiroResumo,
    empresaCodigo: Int,
    empresaNome: String,
    empresaRaw: JsonElement?,
    status: String,
    statusAdesaoId: String?,
    vendedor: TeamMemberOption,
    adesionista: TeamMemberOption?,
    dependentes: List<DependenteFormState>,
): JsonObject {
    val dependentesDb = buildJsonArray {
        dependentes.forEach { dep ->
            val dataIso = toIsoDateOrNull(dep.dataNascimento.text).orEmpty()
            add(
                buildJsonObject {
                    put("nome", dep.nome.trim())
                    put("cpf", dep.cpf.text.filter(Char::isDigit))
                    put("data_nascimento", dataIso)
                    put("sexo", if (dep.sexo == 1) "Masculino" else "Feminino")
                    put("parentesco", dep.parentesco)
                    put("plano_codigo", dep.plano)
                    put("plano_valor", dep.planoValor)
                    put("nome_mae", dep.nomeMae.trim())
                    if (dep.arquivo != null) put("arquivo_path", dep.arquivo.path) else put("arquivo_path", JsonNull)
                },
            )
        }
    }

    return buildJsonObject {
        put("created_by", profileId)
        if (!teamId.isNullOrBlank()) put("team_id", teamId)
        put("status", status)
        statusAdesaoId?.takeIf { it.isNotBlank() }?.let { put("status_adesao_id", it) }
        put("tipo_cadastro", "inclusao_dependente")
        put("responsavel_financeiro_codigo", responsavel.codigo)
        put("responsavel_financeiro_nome", responsavel.nome)
        put("responsavel_financeiro_cpf", responsavel.cpf)
        put("empresa_id", empresaCodigo)
        put("empresa_codigo", empresaCodigo)
        put("empresa_nome", empresaNome)
        if (empresaRaw != null) put("empresa_raw", empresaRaw)
        put("dependentes", dependentesDb)
        if (vendedor.id.isNotBlank()) put("vendedor_id", vendedor.id)
        if (!vendedor.externalId.isNullOrBlank()) put("vendedor_codigo", vendedor.externalId)
        if (vendedor.name.isNotBlank()) put("vendedor_nome", vendedor.name)
        if (adesionista != null) {
            put("adesionista_id", adesionista.id)
            if (!adesionista.externalId.isNullOrBlank()) put("adesionista_codigo", adesionista.externalId)
            if (adesionista.name.isNotBlank()) put("adesionista_nome", adesionista.name)
        }
    }
}

private fun buildErpPayload(
    responsavelCodigo: Int,
    parceiroCodigo: Int,
    adesionistaCodigo: Int,
    funcionarioCodigo: Int,
    dependentes: List<DependenteFormState>,
): JsonObject {
    val mesAnoAtual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
    return buildJsonObject {
        put(
            "dados",
            buildJsonObject {
                put(
                    "parceiro",
                    buildJsonObject {
                        put("codigo", parceiroCodigo)
                        put("adesionista", adesionistaCodigo)
                    },
                )
                put(
                    "responsavelFinanceiro",
                    buildJsonObject {
                        put("codigo", responsavelCodigo)
                        put("dataAssinaturaContrato", "")
                    },
                )
                put(
                    "dependente",
                    buildJsonArray {
                        dependentes.forEach { dep ->
                            add(
                                buildJsonObject {
                                    put("tipo", dep.parentesco)
                                    put("nome", dep.nome.trim())
                                    put("cpf", dep.cpf.text.filter(Char::isDigit))
                                    put("sexo", dep.sexo)
                                    put("plano", dep.plano)
                                    put("planoValor", dep.planoValor)
                                    put("nomeMae", dep.nomeMae.trim())
                                    put("numeroProposta", "")
                                    put("carenciaAtendimento", 1)
                                    put("rcaId", 0)
                                    put("cd_orientacao_sexual", 0)
                                    put("OutraOrientacaoSexual", "")
                                    put("cd_ident_genero", 0)
                                    put("OutraIdentidadeGenero", "")
                                    put("idExterno", "")
                                    put("MMYYYY1Pagamento", mesAnoAtual)
                                    put("numeroCarteira", "")
                                    put("observacaoUsuario", "")
                                    put("dataNascimento", formatDateForErp(toIsoDateOrNull(dep.dataNascimento.text).orEmpty()))
                                    put("funcionarioCadastro", funcionarioCodigo)
                                    put("dataCadastroLoteContrato", "")
                                    put("estadoCivil", 0)
                                },
                            )
                        }
                    },
                )
                put("contatoDependente", buildJsonArray {})
            },
        )
    }
}

private fun parseDependentes(cadastro: CadastroDetalhe): List<DependenteFormState> {
    val arr = cadastro.dependentes?.jsonArray ?: return emptyList()
    return arr.map { element ->
        val obj = element.jsonObject
        val cpf = obj["cpf"]?.jsonPrimitive?.contentOrNull?.filter(Char::isDigit).orEmpty().take(11)
        val dateIso = obj["data_nascimento"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val dateDigits = if (Regex("""\d{4}-\d{2}-\d{2}""").matches(dateIso)) {
            val parts = dateIso.split("-")
            "${parts[2]}${parts[1]}${parts[0]}"
        } else {
            ""
        }
        val sexoRaw = obj["sexo"]?.jsonPrimitive?.contentOrNull.orEmpty()
        DependenteFormState(
            nome = obj["nome"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            cpf = TextFieldValue(cpf, TextRange(cpf.length)),
            dataNascimento = TextFieldValue(dateDigits, TextRange(dateDigits.length)),
            sexo = when {
                sexoRaw.equals("Masculino", ignoreCase = true) -> 1
                sexoRaw.equals("Feminino", ignoreCase = true) -> 0
                else -> -1
            },
            parentesco = obj["parentesco"]?.jsonPrimitive?.intOrNull ?: 0,
            plano = obj["plano_codigo"]?.jsonPrimitive?.intOrNull ?: 0,
            planoValor = obj["plano_valor"]?.jsonPrimitive?.contentOrNull ?: "0.00",
            nomeMae = obj["nome_mae"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            arquivo = obj["arquivo_path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
                UploadedTempFile(it.substringAfterLast('/'), it, "application/octet-stream", 0L)
            },
            saved = true,
            expanded = false,
        )
    }
}

private fun buildDependenteBase(cadastro: CadastroDetalhe): DependenteFormState {
    val cpf = cadastro.cpf.filter(Char::isDigit).take(11)
    val dateIso = cadastro.dataNascimento.orEmpty()
    val dateDigits = if (Regex("""\d{4}-\d{2}-\d{2}""").matches(dateIso)) {
        val parts = dateIso.split("-")
        "${parts[2]}${parts[1]}${parts[0]}"
    } else {
        ""
    }
    return DependenteFormState(
        nome = cadastro.nome.orEmpty(),
        cpf = TextFieldValue(cpf, TextRange(cpf.length)),
        dataNascimento = TextFieldValue(dateDigits, TextRange(dateDigits.length)),
        sexo = cadastro.sexoCodigo ?: -1,
        plano = cadastro.planoCodigo ?: 0,
        nomeMae = cadastro.nomeMae.orEmpty(),
        arquivo = cadastro.arquivoPath?.takeIf { it.isNotBlank() }?.let {
            UploadedTempFile(it.substringAfterLast('/'), it, "application/octet-stream", 0L)
        },
        saved = true,
        expanded = false,
    )
}

private fun extractDependenteCodes(response: JsonElement): List<Int> {
    val root = response.jsonObject
    if (root["success"]?.jsonPrimitive?.booleanOrNull != true) {
        throw IllegalStateException(
            root["error"]?.jsonPrimitive?.contentOrNull
                ?: root["message"]?.jsonPrimitive?.contentOrNull
                ?: "Erro ao incluir dependentes.",
        )
    }
    val dados = root["data"]?.jsonObject?.get("dados")?.jsonObject
        ?: throw IllegalStateException("Resposta invalida da API de inclusao de dependentes.")
    return dados["dependentes"]?.jsonArray?.mapNotNull { item ->
        item.jsonObject["codigo"]?.jsonPrimitive?.intOrNull
    } ?: emptyList()
}

private fun extractPlanosFromEmpresa(raw: JsonElement?, planosMap: List<br.com.vendamais.mobile.data.models.PlanoMap>): List<PlanoOption> {
    val source = when (raw) {
        is JsonObject -> raw["precoPlano"]?.jsonArray
        is JsonArray -> raw
        else -> null
    }
    if (source == null) {
        return planosMap
            .filter { it.ativo }
            .map {
                PlanoOption(
                    codigo = it.planoId,
                    nome = it.nomeExibicao,
                    label = it.nomeExibicao,
                )
            }
    }
    return source.mapNotNull { item ->
        val obj = item.jsonObject
        val codigo = extractPlanoCodigo(obj) ?: return@mapNotNull null
        val nome = planosMap.firstOrNull { it.planoId == codigo }?.nomeExibicao
            ?: obj.jsonString("NomeANS", "nomeANS", "nome", "nomeExibicao")
            ?: "Plano $codigo"
        val valorTitular = obj.jsonMoney("ValorTitular", "valorTitular", "valor_titular")
        val valorDependente = obj.jsonMoney("ValorDependente", "valorDependente", "valor_dependente")

        PlanoOption(
            codigo = codigo,
            nome = nome,
            valorTitular = valorTitular,
            valorDependente = valorDependente,
            label = buildPlanoLabel(
                nome = nome,
                valorTitular = valorTitular,
                valorDependente = valorDependente,
            ),
        )
    }
}

private fun extractPlanoCodigo(obj: JsonObject): Int? {
    return obj.jsonInt("Plano", "plano", "plano_id", "codigoPlano", "codigo_plano", "Id", "id")
}

private fun JsonObject.jsonInt(vararg keys: String): Int? {
    keys.forEach { key ->
        val value = this[key]?.jsonPrimitive?.intOrNull
        if (value != null) return value
    }
    return null
}

private fun JsonObject.jsonString(vararg keys: String): String? {
    keys.forEach { key ->
        val value = this[key]?.jsonPrimitive?.contentOrNull?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject.jsonMoney(vararg keys: String): Double? {
    keys.forEach { key ->
        val raw = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (raw.isBlank()) return@forEach

        val normalized = raw
            .replace("R$", "", ignoreCase = true)
            .replace(" ", "")
            .let {
                when {
                    it.contains(',') && it.contains('.') -> it.replace(".", "").replace(",", ".")
                    it.contains(',') -> it.replace(",", ".")
                    else -> it
                }
            }

        val asDouble = normalized.toDoubleOrNull()
        if (asDouble != null) return asDouble
    }
    return null
}

private fun buildPlanoLabel(
    nome: String,
    valorTitular: Double?,
    valorDependente: Double?,
): String {
    if (valorTitular == null && valorDependente == null) return nome

    val titular = valorTitular?.let(::formatCurrencyBr) ?: "-"
    val dependente = valorDependente?.let(::formatCurrencyBr) ?: "-"
    return "$nome | Titular: $titular | Dependente: $dependente"
}

private fun formatCurrencyBr(value: Double): String {
    return runCatching {
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
    }.getOrDefault(value.toString())
}

private fun formatPlanoValorDot(value: Double): String {
    return "%.2f".format(Locale.US, value)
}

private fun resolveInclusaoMessageTone(message: String): InclusaoMessageTone {
    val normalized = message.lowercase(Locale.ROOT)
    return when {
        normalized.contains("sucesso") -> InclusaoMessageTone.SUCCESS
        normalized.contains("falha") || normalized.contains("erro") || normalized.contains("invalid input syntax") -> InclusaoMessageTone.ERROR
        normalized.contains("obrigatorio") || normalized.contains("invalido") || normalized.contains("selecione") -> InclusaoMessageTone.ALERT
        else -> InclusaoMessageTone.WARNING
    }
}

@Composable
private fun inclusaoMessageToneColors(tone: InclusaoMessageTone): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (tone) {
        InclusaoMessageTone.SUCCESS ->
            if (darkTheme) MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            else EmeraldSoft to EmeraldDark
        InclusaoMessageTone.WARNING ->
            if (darkTheme) androidx.compose.ui.graphics.Color(0xFF4A3A1E) to androidx.compose.ui.graphics.Color(0xFFFFDE9E)
            else Amber100 to Amber500
        InclusaoMessageTone.ALERT ->
            if (darkTheme) androidx.compose.ui.graphics.Color(0xFF4B2F1F) to androidx.compose.ui.graphics.Color(0xFFFFC39A)
            else BrandOrange.copy(alpha = 0.18f) to BrandOrange
        InclusaoMessageTone.ERROR ->
            if (darkTheme) MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
            else Red100 to Red500
    }
}

private fun validateUpload(fileName: String, mimeType: String, size: Long) {
    val lower = fileName.lowercase()
    val acceptedName = lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    val acceptedMime = mimeType in setOf("application/pdf", "image/jpeg", "image/png")
    if (!acceptedName && !acceptedMime) throw IllegalStateException("Arquivo invalido. Use PDF, JPG ou PNG.")
    if (size > MAX_UPLOAD_BYTES) throw IllegalStateException("Arquivo excede 10MB.")
}

private fun createCameraCaptureUri(context: Context): Pair<Uri, String> {
    val directory = File(context.cacheDir, "camera_uploads").apply { mkdirs() }
    val file = File.createTempFile("dependente_", ".jpg", directory)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    return uri to file.absolutePath
}

private fun resolveFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) return cursor.getString(index)
    }
    return uri.lastPathSegment ?: "arquivo"
}

private fun writePreviewFile(
    context: Context,
    fileName: String,
    bytes: ByteArray,
): Uri {
    val directory = File(context.cacheDir, "shared_qrcodes").apply { mkdirs() }
    val safeName = fileName
        .ifBlank { "arquivo" }
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val file = File(directory, "preview_${System.currentTimeMillis()}_$safeName")
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

private fun resolvePreviewMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

private class DependenteCpfVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(11)
        val originalToTransformed = IntArray(digits.length + 1)
        val formatted = buildString {
            originalToTransformed[0] = 0
            digits.forEachIndexed { index, c ->
                append(c)
                if (index == 2 && digits.length > 3) append('.')
                if (index == 5 && digits.length > 6) append('.')
                if (index == 8 && digits.length > 9) append('-')
                originalToTransformed[index + 1] = length
            }
        }
        val transformedToOriginal = IntArray(formatted.length + 1) { pos ->
            formatted.take(pos).count(Char::isDigit).coerceAtMost(digits.length)
        }
        val offset = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int): Int = transformedToOriginal[offset.coerceIn(0, formatted.length)]
        }
        return TransformedText(AnnotatedString(formatted), offset)
    }
}

private class DependenteDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(8)
        val originalToTransformed = IntArray(digits.length + 1)
        val formatted = buildString {
            originalToTransformed[0] = 0
            digits.forEachIndexed { index, c ->
                append(c)
                if (index == 1 && digits.length > 2) append('/')
                if (index == 3 && digits.length > 4) append('/')
                originalToTransformed[index + 1] = length
            }
        }
        val transformedToOriginal = IntArray(formatted.length + 1) { pos ->
            formatted.take(pos).count(Char::isDigit).coerceAtMost(digits.length)
        }
        val offset = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, digits.length)]
            override fun transformedToOriginal(offset: Int): Int = transformedToOriginal[offset.coerceIn(0, formatted.length)]
        }
        return TransformedText(AnnotatedString(formatted), offset)
    }
}


