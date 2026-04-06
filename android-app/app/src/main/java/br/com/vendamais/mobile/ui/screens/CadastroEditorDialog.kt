package br.com.vendamais.mobile.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import br.com.vendamais.mobile.data.models.MobileProfile
import br.com.vendamais.mobile.data.models.TeamMemberOption
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
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
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
import java.time.LocalDate
import java.time.Period
import java.util.Locale

private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

private data class ContatoFormState(
    val tipo: String = "celular",
    val valor: String = "",
    val principal: Boolean = false,
)

private data class CadastroDependenteFormState(
    val tipo: Int = 0,
    val nome: String = "",
    val dataNascimento: TextFieldValue = TextFieldValue(""),
    val cpf: TextFieldValue = TextFieldValue(""),
    val sexo: Int = -1,
    val plano: Int = 0,
    val planoValor: String = "0,00",
    val nomeMae: String = "",
    val carenciaAtendimento: Int = 0,
)

private data class CadastroPlanoOption(
    val codigo: Int,
    val nome: String,
    val valorTitular: Double? = null,
    val valorDependente: Double? = null,
    val label: String = nome,
)

private enum class CadastroMessageTone {
    WARNING,
    ALERT,
    ERROR,
    SUCCESS,
}

@Composable
fun CadastroEditorDialog(
    state: AppUiState,
    viewModel: AppViewModel,
    cadastro: CadastroDetalhe,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val profile = state.profile

    var nome by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.nome.orEmpty()) }
    var sexoCodigo by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.sexoCodigo?.toString() ?: "") }
    var nomeMae by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.nomeMae.orEmpty()) }
    var numeroMatricula by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.numeroMatricula.orEmpty()) }
    var statusAdesaoId by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.statusAdesaoId.orEmpty()) }
    var arquivoPath by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.arquivoPath.orEmpty()) }
    var arquivoNome by rememberSaveable(cadastro.id) { mutableStateOf(cadastro.arquivoPath?.substringAfterLast('/') ?: "") }
    var currentStep by rememberSaveable(cadastro.id) { mutableStateOf(1) }
    var selectedVendedorId by rememberSaveable(cadastro.id) {
        mutableStateOf(if (profile?.role == "VENDEDOR") profile.id else "")
    }
    var selectedAdesionistaId by rememberSaveable(cadastro.id) {
        mutableStateOf(if (profile?.role == "ADESIONISTA") profile.id else "")
    }

    var novoContatoTipo by rememberSaveable(cadastro.id) { mutableStateOf("celular") }
    var novoContatoValorField by rememberSaveable(cadastro.id, stateSaver = textFieldValueSaver()) {
        mutableStateOf(TextFieldValue(""))
    }

    var saving by rememberSaveable { mutableStateOf(false) }
    var uploading by rememberSaveable { mutableStateOf(false) }
    var localMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showSelectStatusOnClose by rememberSaveable { mutableStateOf(false) }
    var suppressBackgroundPersist by rememberSaveable(cadastro.id) { mutableStateOf(false) }

    var dataNascimentoField by rememberSaveable(cadastro.id, stateSaver = textFieldValueSaver()) {
        val digits = extractDateDigits(cadastro.dataNascimento.orEmpty())
        mutableStateOf(TextFieldValue(digits, TextRange(digits.length)))
    }

    val contatos = remember(cadastro.id) {
        mutableStateListOf<ContatoFormState>().apply {
            addAll(parseContatos(cadastro))
        }
    }

    val dependentes = remember(cadastro.id) {
        mutableStateListOf<CadastroDependenteFormState>().apply {
            val parsed = parseDependentes(cadastro)
            if (parsed.isEmpty()) {
                add(buildTitularDependente(cadastro))
            } else {
                addAll(parsed)
            }
        }
    }

    val planoOptions = remember(
        cadastro.id,
        cadastro.planosRaw,
        cadastro.empresaRaw,
        state.planosMap,
        state.cadastroWorkspace.config?.planosOcultos,
    ) {
        extractPlanosFromCadastro(
            cadastro = cadastro,
            hiddenPlanos = state.cadastroWorkspace.config?.planosOcultos.orEmpty(),
            planosMap = state.planosMap,
        )
    }

    val parentescoOptions = remember(state.parentescosMap) {
        listOf(0 to "Selecione") +
            state.parentescosMap
                .filter { it.ativo && it.parentescoId != 1 }
                .map { it.parentescoId to it.label }
    }

    val canChooseVendedor = profile?.role in setOf(
        "ADMINISTRADOR",
        "ADMIN",
        "GERENTE",
        "GESTOR",
        "SUPERVISOR",
        "CADASTRO",
        "ADESIONISTA",
    )
    val canChooseAdesionista = profile?.role in setOf(
        "ADMINISTRADOR",
        "GERENTE",
        "GESTOR",
        "SUPERVISOR",
        "VENDEDOR",
        "CADASTRO",
    )

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        suppressBackgroundPersist = false
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            runCatching {
                if (arquivoPath.isNotBlank()) {
                    runCatching { viewModel.deleteTempFile(arquivoPath) }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Nao foi possivel ler o arquivo.")
                validateUpload(
                    fileName = resolveFileName(context, uri),
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    size = bytes.size.toLong(),
                )
                viewModel.uploadTempFile(
                    fileName = resolveFileName(context, uri),
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    bytes = bytes,
                    prefix = "cadastros/${cadastro.id}",
                )
            }.onSuccess { uploaded ->
                arquivoPath = uploaded.path
                arquivoNome = uploaded.nome
                viewModel.persistCadastroDraftSilently(
                    cadastro.id,
                    buildJsonObject { put("arquivo_path", uploaded.path) },
                )
                localMessage = "Arquivo atualizado com sucesso."
            }.onFailure { throwable ->
                localMessage = throwable.message ?: "Falha ao carregar arquivo."
            }
            uploading = false
        }
    }

    LaunchedEffect(nome, nomeMae, sexoCodigo, dataNascimentoField.text, cadastro.cpf) {
        if (dependentes.isEmpty()) {
            dependentes += buildTitularDependente(cadastro)
        }

        val titular = dependentes.firstOrNull() ?: return@LaunchedEffect
        val titularCpf = cadastro.cpf.filter(Char::isDigit).take(11)
        val titularDateDigits = dataNascimentoField.text.filter(Char::isDigit).take(8)
        val desiredDate = TextFieldValue(titularDateDigits, TextRange(titularDateDigits.length))
        val desiredCpf = TextFieldValue(titularCpf, TextRange(titularCpf.length))
        val desiredSexo = sexoCodigo.toIntOrNull() ?: -1

        dependentes[0] = titular.copy(
            tipo = 1,
            nome = nome,
            dataNascimento = desiredDate,
            cpf = desiredCpf,
            sexo = desiredSexo,
            nomeMae = nomeMae,
        )
    }

    LaunchedEffect(cadastro.id, state.vendedores, state.adesionistas, profile?.id, profile?.role) {
        if (profile?.role == "VENDEDOR") {
            selectedVendedorId = profile.id
        } else if (selectedVendedorId.isBlank() && !cadastro.vendedorCodigo.isNullOrBlank()) {
            selectedVendedorId = state.vendedores
                .firstOrNull { it.externalId == cadastro.vendedorCodigo }
                ?.id
                .orEmpty()
        }

        if (profile?.role == "ADESIONISTA") {
            selectedAdesionistaId = profile.id
        } else if (selectedAdesionistaId.isBlank() && !cadastro.adesionistaCodigo.isNullOrBlank()) {
            selectedAdesionistaId = state.adesionistas
                .firstOrNull { it.externalId == cadastro.adesionistaCodigo }
                ?.id
                .orEmpty()
        }
    }

    fun normalizeContatoValor(tipo: String, valor: String): String {
        val raw = valor.trim()
        return if (tipo == "email") raw else raw.filter(Char::isDigit)
    }

    fun contatoMaxDigits(tipo: String): Int = if (tipo == "fixo") 10 else 11

    fun normalizeContatoEditingValue(tipo: String, value: TextFieldValue): TextFieldValue {
        if (tipo == "email") return value
        val digits = value.text.filter(Char::isDigit).take(contatoMaxDigits(tipo))
        return TextFieldValue(
            text = digits,
            selection = TextRange(digits.length),
        )
    }

    fun buildDraftPatchPayload(): JsonObject {
        val dataIso = toIsoDateOrNull(dataNascimentoField.text)
        val funcionarioCadastro = state.profile?.externalId?.toIntOrNull() ?: 0
        val vendedor = resolveVendedor(profile, state, selectedVendedorId)
        val adesionista = resolveAdesionista(profile, state, selectedAdesionistaId)

        val contatosJson = buildJsonArray {
            contatos.forEach { contato ->
                add(
                    buildJsonObject {
                        put("tipo", contato.tipo)
                        put("valor", contato.valor)
                        put("principal", contato.principal)
                    },
                )
            }
        }

        val dependentesJson = buildJsonArray {
            dependentes.forEachIndexed { index, dep ->
                val cpfDigits = dep.cpf.text.filter(Char::isDigit)
                val sexo = dep.sexo
                val sexoDescricao = when (sexo) {
                    1 -> "Masculino"
                    0 -> "Feminino"
                    else -> ""
                }
                val dataDep = toIsoDateOrNull(dep.dataNascimento.text).orEmpty()
                val tipo = if (index == 0) 1 else dep.tipo

                add(
                    buildJsonObject {
                        put("tipo", tipo)
                        put("nome", dep.nome.trim())
                        put("dataNascimento", dataDep)
                        put("cpf", cpfDigits)
                        put("sexo", sexo)
                        put("sexoDescricao", sexoDescricao)
                        put("plano", dep.plano)
                        put("planoValor", dep.planoValor.ifBlank { "0,00" })
                        put("nomeMae", dep.nomeMae.trim())
                        put("carenciaAtendimento", dep.carenciaAtendimento)
                        put("funcionarioCadastro", funcionarioCadastro)
                    },
                )
            }
        }

        return buildJsonObject {
            put("nome", nome.ifBlank { "" })
            if (dataIso != null) {
                put("data_nascimento", dataIso)
            }
            sexoCodigo.toIntOrNull()?.let { put("sexo_codigo", it) }
            put("nome_mae", nomeMae.ifBlank { "" })
            put("numero_matricula", numeroMatricula.ifBlank { "" })
            if (statusAdesaoId.isNotBlank()) put("status_adesao_id", statusAdesaoId)
            put("arquivo_path", arquivoPath.ifBlank { "" })
            put("contatos", contatosJson)
            put("dependentes", dependentesJson)
            vendedor?.let {
                put("vendedor_id", it.id)
                if (!it.externalId.isNullOrBlank()) put("vendedor_codigo", it.externalId)
                put("vendedor_nome", it.name)
            }
            adesionista?.let {
                put("adesionista_id", it.id)
                if (!it.externalId.isNullOrBlank()) put("adesionista_codigo", it.externalId)
                put("adesionista_nome", it.name)
            }
        }
    }

    fun persistDraftSnapshotSilently() {
        if (saving || uploading || state.sendingCadastro || suppressBackgroundPersist) return
        val payload = buildDraftPatchPayload()
        viewModel.persistCadastroDraftSilently(cadastro.id, payload)
    }

    fun requestCloseEditor() {
        if (statusAdesaoId.isBlank() && state.statusAdesoes.isNotEmpty()) {
            showSelectStatusOnClose = true
            return
        }
        scope.launch {
            val payload = buildDraftPatchPayload()
            runCatching { viewModel.updateCadastroRecord(cadastro.id, payload) }
                .onFailure { throwable ->
                    localMessage = throwable.message ?: "Falha ao salvar rascunho antes de fechar."
                    return@launch
                }
            onDismiss()
        }
    }

    fun addContato() {
        val normalized = normalizeContatoValor(novoContatoTipo, novoContatoValorField.text)
        if (normalized.isBlank()) {
            localMessage = "Informe um valor de contato antes de adicionar."
            return
        }
        val shouldBePrincipal = contatos.none { it.principal }
        contatos += ContatoFormState(
            tipo = novoContatoTipo,
            valor = normalized,
            principal = shouldBePrincipal,
        )
        novoContatoValorField = TextFieldValue("")
        persistDraftSnapshotSilently()
    }

    fun removeContato(index: Int) {
        if (index !in contatos.indices) return
        val removedPrincipal = contatos[index].principal
        contatos.removeAt(index)
        if (removedPrincipal && contatos.isNotEmpty()) {
            contatos[0] = contatos[0].copy(principal = true)
        }
        persistDraftSnapshotSilently()
    }

    fun toggleContatoPrincipal(index: Int) {
        if (index !in contatos.indices) return
        contatos.indices.forEach { i ->
            contatos[i] = contatos[i].copy(principal = i == index)
        }
        persistDraftSnapshotSilently()
    }

    fun addDependente() {
        dependentes += CadastroDependenteFormState()
        persistDraftSnapshotSilently()
    }

    fun removeDependente(index: Int) {
        if (index <= 0 || index !in dependentes.indices) return
        dependentes.removeAt(index)
        persistDraftSnapshotSilently()
    }

    fun validateStepOne(): String? {
        val dataIso = toIsoDateOrNull(dataNascimentoField.text)
        val vendedor = resolveVendedor(profile, state, selectedVendedorId)
        if (requiresVendedorSelection(profile) && vendedor?.externalId.isNullOrBlank()) {
            return "Selecione um vendedor antes de continuar."
        }
        if (nome.isBlank()) return "Nome completo obrigatorio."
        if (dataIso == null) return "Data de nascimento invalida. Use dd/mm/aaaa."
        if (sexoCodigo.toIntOrNull() !in setOf(0, 1)) return "Selecione o sexo."
        if (nomeMae.isBlank()) return "Nome da mae obrigatorio."
        if (cadastro.empresaExigeMatricula == 1 && numeroMatricula.isBlank()) {
            return "Matricula obrigatoria para esta empresa."
        }

        val telefones = contatos.filter {
            it.tipo in setOf("celular", "fixo", "whatsapp") && it.valor.isNotBlank()
        }
        if (telefones.isEmpty()) return "Adicione pelo menos um telefone."

        if (dependentes.isEmpty()) return "Adicione ao menos o titular e um plano."
        val titular = dependentes.firstOrNull()
        if (titular == null || titular.tipo != 1) return "Titular nao identificado nos dependentes."

        dependentes.forEachIndexed { index, dep ->
            val label = if (index == 0) "Titular" else "Dependente ${index + 1}"
            val dateIsoDep = toIsoDateOrNull(dep.dataNascimento.text)
            if (dep.nome.isBlank()) return "$label: nome obrigatorio."
            if (dateIsoDep == null) return "$label: data de nascimento invalida."

            val cpfDigits = dep.cpf.text.filter(Char::isDigit)
            if (index == 0 && !validateCpf(cpfDigits)) return "$label: CPF invalido."
            if (index > 0) {
                if (!isUnder18(dateIsoDep) && cpfDigits.length != 11) {
                    return "$label: CPF obrigatorio para maior de idade."
                }
                if (cpfDigits.isNotBlank() && !validateCpf(cpfDigits)) {
                    return "$label: CPF invalido."
                }
            }

            if (dep.sexo !in setOf(0, 1)) return "$label: sexo obrigatorio."
            if (index > 0 && dep.tipo == 0) return "$label: parentesco obrigatorio."
            if (dep.plano == 0) return "$label: plano obrigatorio."
            if (dep.nomeMae.isBlank()) return "$label: nome da mae obrigatorio."
        }

        return null
    }

    fun buildPayload(
        requireStatus: Boolean,
        silentValidation: Boolean = false,
    ): JsonObject? {
        val dataIso = toIsoDateOrNull(dataNascimentoField.text)
        if (!silentValidation && dataNascimentoField.text.isNotBlank() && dataIso == null) {
            localMessage = "Data de nascimento invalida. Use formato dd/mm/aaaa."
            return null
        }

        if (requireStatus && statusAdesaoId.isBlank()) {
            if (!silentValidation) {
                localMessage = "Selecione o status da adesao antes de cadastrar."
            }
            return null
        }

        val firstPrincipalIndex = contatos.indexOfFirst { it.principal }
        if (firstPrincipalIndex == -1 && contatos.isNotEmpty()) {
            contatos[0] = contatos[0].copy(principal = true)
        }

        val funcionarioCadastro = state.profile?.externalId?.toIntOrNull() ?: 0
        val vendedor = resolveVendedor(profile, state, selectedVendedorId)
        val adesionista = resolveAdesionista(profile, state, selectedAdesionistaId)
        val empresaSelecionada = state.cadastroWorkspace.selectedEmpresa
        val empresaIdPersist = empresaSelecionada?.id?.takeIf { it > 0 }
            ?: cadastro.empresaId
            ?: cadastro.empresaCodigo
        val empresaCodigoPersist = empresaSelecionada?.codigo?.takeIf { it > 0 }
            ?: empresaSelecionada?.id?.takeIf { it > 0 }
            ?: cadastro.empresaCodigo
            ?: cadastro.empresaId
        val empresaNomePersist = empresaSelecionada
            ?.nomeFantasia
            ?.takeIf { it.isNotBlank() }
            ?: empresaSelecionada
                ?.razaoSocial
                ?.takeIf { it.isNotBlank() }
            ?: cadastro.empresaNome
        val empresaCnpjPersist = empresaSelecionada?.cnpj?.takeIf { it.isNotBlank() } ?: cadastro.empresaCnpj

        val contatosJson = buildJsonArray {
            contatos.forEach { contato ->
                add(
                    buildJsonObject {
                        put("tipo", contato.tipo)
                        put("valor", contato.valor)
                        put("principal", contato.principal)
                    },
                )
            }
        }

        val dependentesJson = buildJsonArray {
            dependentes.forEachIndexed { index, dep ->
                val cpfDigits = dep.cpf.text.filter(Char::isDigit)
                val sexo = dep.sexo
                val sexoDescricao = when (sexo) {
                    1 -> "Masculino"
                    0 -> "Feminino"
                    else -> ""
                }
                val dataDep = toIsoDateOrNull(dep.dataNascimento.text).orEmpty()
                val tipo = if (index == 0) 1 else dep.tipo

                add(
                    buildJsonObject {
                        put("tipo", tipo)
                        put("nome", dep.nome.trim())
                        put("dataNascimento", dataDep)
                        put("cpf", cpfDigits)
                        put("sexo", sexo)
                        put("sexoDescricao", sexoDescricao)
                        put("plano", dep.plano)
                        put("planoValor", dep.planoValor.ifBlank { "0,00" })
                        put("nomeMae", dep.nomeMae.trim())
                        put("carenciaAtendimento", dep.carenciaAtendimento)
                        put("funcionarioCadastro", funcionarioCadastro)
                    },
                )
            }
        }

        return buildJsonObject {
            profile?.id?.takeIf { it.isNotBlank() }?.let { put("created_by", it) }
            profile?.teamId?.takeIf { it.isNotBlank() }?.let { put("team_id", it) }
            empresaIdPersist?.let { put("empresa_id", it) }
            empresaCodigoPersist?.let { put("empresa_codigo", it) }
            empresaNomePersist?.takeIf { it.isNotBlank() }?.let { put("empresa_nome", it) }
            empresaCnpjPersist?.takeIf { it.isNotBlank() }?.let { put("empresa_cnpj", it) }
            empresaSelecionada?.raw?.let { put("empresa_raw", it) }
            put("nome", nome)
            if (dataIso != null) {
                put("data_nascimento", dataIso)
            } else {
                put("data_nascimento", JsonNull)
            }
            sexoCodigo.toIntOrNull()?.let { put("sexo_codigo", it) } ?: put("sexo_codigo", JsonNull)
            put("nome_mae", nomeMae.ifBlank { "" })
            put("numero_matricula", numeroMatricula.ifBlank { "" })
            if (statusAdesaoId.isNotBlank()) put("status_adesao_id", statusAdesaoId)
            vendedor?.let {
                put("vendedor_id", it.id)
                if (!it.externalId.isNullOrBlank()) put("vendedor_codigo", it.externalId)
                put("vendedor_nome", it.name)
            }
            adesionista?.let {
                put("adesionista_id", it.id)
                if (!it.externalId.isNullOrBlank()) put("adesionista_codigo", it.externalId)
                put("adesionista_nome", it.name)
            }
            put("arquivo_path", arquivoPath.ifBlank { "" })
            put("contatos", contatosJson)
            put("dependentes", dependentesJson)
        }
    }

    val autosaveSignature = buildString {
        append(nome)
        append('|')
        append(sexoCodigo)
        append('|')
        append(nomeMae)
        append('|')
        append(dataNascimentoField.text)
        append('|')
        append(numeroMatricula)
        append('|')
        append(statusAdesaoId)
        append('|')
        append(selectedVendedorId)
        append('|')
        append(selectedAdesionistaId)
        append('|')
        append(arquivoPath)
        append('|')
        append(arquivoNome)
        contatos.forEach { contato ->
            append("|c:")
            append(contato.tipo)
            append(':')
            append(contato.valor)
            append(':')
            append(contato.principal)
        }
        dependentes.forEach { dep ->
            append("|d:")
            append(dep.tipo)
            append(':')
            append(dep.nome)
            append(':')
            append(dep.cpf.text)
            append(':')
            append(dep.dataNascimento.text)
            append(':')
            append(dep.sexo)
            append(':')
            append(dep.plano)
            append(':')
            append(dep.planoValor)
            append(':')
            append(dep.nomeMae)
            append(':')
            append(dep.carenciaAtendimento)
        }
    }

    LaunchedEffect(cadastro.id, autosaveSignature) {
        if (saving || uploading || state.sendingCadastro || suppressBackgroundPersist) return@LaunchedEffect
        delay(1200)
        if (saving || uploading || state.sendingCadastro || suppressBackgroundPersist) return@LaunchedEffect
        val payload = buildPayload(
            requireStatus = false,
            silentValidation = true,
        ) ?: return@LaunchedEffect
        viewModel.persistCadastroDraftSilently(cadastro.id, payload)
    }

    val persistDraftOnBackground = rememberUpdatedState {
        if (saving || uploading || state.sendingCadastro || suppressBackgroundPersist) return@rememberUpdatedState
        val payload = buildPayload(
            requireStatus = false,
            silentValidation = true,
        ) ?: return@rememberUpdatedState

        viewModel.persistCadastroDraftSilently(cadastro.id, payload)
    }

    DisposableEffect(lifecycleOwner, cadastro.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                persistDraftOnBackground.value.invoke()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Cadastro",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Etapa $currentStep de 2",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = cadastro.empresaNome ?: "Sem empresa vinculada",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = { requestCloseEditor() },
                    ) {
                        Text("Fechar")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (currentStep == 1) {
                        WebCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = nome,
                                    onValueChange = { nome = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome Completo") },
                                )
                                OutlinedTextField(
                                    value = formatCpf(cadastro.cpf),
                                    onValueChange = {},
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("CPF") },
                                    enabled = false,
                                )
                                OutlinedTextField(
                                    value = dataNascimentoField,
                                    onValueChange = { input ->
                                        val digits = input.text.filter(Char::isDigit).take(8)
                                        dataNascimentoField = TextFieldValue(digits, TextRange(digits.length))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Data de Nascimento") },
                                    placeholder = { Text("dd/mm/aaaa") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    visualTransformation = DateVisualTransformation(),
                                    singleLine = true,
                                )
                                SelectionField(
                                    label = "Sexo",
                                    value = when (sexoCodigo) {
                                        "1" -> "Masculino"
                                        "0" -> "Feminino"
                                        else -> "Selecione"
                                    },
                                    options = listOf("" to "Selecione", "1" to "Masculino", "0" to "Feminino"),
                                    onSelected = { sexoCodigo = it },
                                )
                                OutlinedTextField(
                                    value = nomeMae,
                                    onValueChange = { nomeMae = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nome da Mae") },
                                )
                                if (cadastro.empresaExigeMatricula == 1) {
                                    OutlinedTextField(
                                        value = numeroMatricula,
                                        onValueChange = { numeroMatricula = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Matricula") },
                                    )
                                }

                                if (canChooseVendedor) {
                                    SelectionField(
                                        label = "Vendedor",
                                        value = state.vendedores
                                            .firstOrNull { it.id == selectedVendedorId }
                                            ?.toTeamSelectionLabel()
                                            ?: "Selecione um vendedor",
                                        options = state.vendedores.map { it.id to it.toTeamSelectionLabel() },
                                        onSelected = { selectedVendedorId = it },
                                    )
                                    if (state.vendedores.isEmpty()) {
                                        Text(
                                            text = "Nenhum vendedor disponível. Entre em contato com o administrador.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                } else if (profile?.role == "VENDEDOR") {
                                    OutlinedTextField(
                                        value = profile.toTeamSelectionLabel(),
                                        onValueChange = {},
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Vendedor") },
                                        enabled = false,
                                    )
                                }

                                if (canChooseAdesionista) {
                                    SelectionField(
                                        label = "Adesionista",
                                        value = state.adesionistas
                                            .firstOrNull { it.id == selectedAdesionistaId }
                                            ?.toTeamSelectionLabel()
                                            ?: "Selecione um adesionista",
                                        options = listOf("" to "Nenhum adesionista") + state.adesionistas.map { it.id to it.toTeamSelectionLabel() },
                                        onSelected = { selectedAdesionistaId = it },
                                    )
                                }

                            }
                        }

                        WebCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Contatos", fontWeight = FontWeight.SemiBold)

                                if (contatos.isEmpty()) {
                                    Text(
                                        "Nenhum contato adicionado. Adicione pelo menos um telefone.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                contatos.forEachIndexed { index, contato ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        tonalElevation = 1.dp,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text("${contato.tipo.uppercase()}${if (contato.principal) " - Principal" else ""}")
                                            Text(
                                                if (contato.tipo == "email") contato.valor else formatPhone(contato.valor),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = { toggleContatoPrincipal(index) }) {
                                                    Text(if (contato.principal) "Principal" else "Tornar principal")
                                                }
                                                TextButton(onClick = { removeContato(index) }) {
                                                    Text("Remover")
                                                }
                                            }
                                        }
                                    }
                                }

                                SelectionField(
                                    label = "Tipo",
                                    value = when (novoContatoTipo) {
                                        "celular" -> "Celular"
                                        "whatsapp" -> "WhatsApp"
                                        "fixo" -> "Fixo"
                                        "email" -> "Email"
                                        else -> "Celular"
                                    },
                                    options = listOf(
                                        "celular" to "Celular",
                                        "whatsapp" to "WhatsApp",
                                        "fixo" to "Fixo",
                                        "email" to "Email",
                                    ),
                                    onSelected = {
                                        novoContatoTipo = it
                                        novoContatoValorField = normalizeContatoEditingValue(it, novoContatoValorField)
                                    },
                                )

                                OutlinedTextField(
                                    value = novoContatoValorField,
                                    onValueChange = { value ->
                                        novoContatoValorField = normalizeContatoEditingValue(novoContatoTipo, value)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Contato") },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = if (novoContatoTipo == "email") KeyboardType.Email else KeyboardType.Number,
                                    ),
                                    visualTransformation = if (novoContatoTipo == "email") {
                                        VisualTransformation.None
                                    } else {
                                        ContatoPhoneVisualTransformation()
                                    },
                                    singleLine = true,
                                )

                                Button(onClick = ::addContato, modifier = Modifier.fillMaxWidth()) {
                                    Text("Adicionar contato")
                                }
                            }
                        }

                        WebCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Dependentes", fontWeight = FontWeight.SemiBold)

                                if (planoOptions.isEmpty()) {
                                    Text(
                                        "Nenhum plano disponivel para a empresa selecionada.",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }

                                dependentes.forEachIndexed { index, dep ->
                                    val isTitular = index == 0

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        tonalElevation = 1.dp,
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    if (isTitular) "Titular" else "Dependente ${index + 1}",
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                if (!isTitular) {
                                                    TextButton(onClick = { removeDependente(index) }) {
                                                        Text("Remover")
                                                    }
                                                }
                                            }

                                            if (!isTitular) {
                                                SelectionField(
                                                    label = "Parentesco",
                                                    value = parentescoOptions.firstOrNull { it.first == dep.tipo }?.second ?: "Selecione",
                                                    options = parentescoOptions,
                                                    onSelected = { tipo ->
                                                        dependentes[index] = dependentes[index].copy(tipo = tipo)
                                                    },
                                                )
                                            } else {
                                                Text("Parentesco: Titular", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            OutlinedTextField(
                                                value = dep.nome,
                                                onValueChange = { value ->
                                                    dependentes[index] = dependentes[index].copy(nome = value)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("Nome") },
                                                enabled = !isTitular,
                                            )

                                            OutlinedTextField(
                                                value = dep.cpf,
                                                onValueChange = { value ->
                                                    val digits = value.text.filter(Char::isDigit).take(11)
                                                    dependentes[index] = dependentes[index].copy(
                                                        cpf = TextFieldValue(digits, TextRange(digits.length)),
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("CPF") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                visualTransformation = CadastroDependenteCpfVisualTransformation(),
                                                singleLine = true,
                                                enabled = !isTitular,
                                            )

                                            OutlinedTextField(
                                                value = dep.dataNascimento,
                                                onValueChange = { value ->
                                                    val digits = value.text.filter(Char::isDigit).take(8)
                                                    dependentes[index] = dependentes[index].copy(
                                                        dataNascimento = TextFieldValue(digits, TextRange(digits.length)),
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("Data de Nascimento") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                visualTransformation = DateVisualTransformation(),
                                                singleLine = true,
                                                enabled = !isTitular,
                                            )

                                            SelectionField(
                                                label = "Sexo",
                                                value = when (dep.sexo) {
                                                    1 -> "Masculino"
                                                    0 -> "Feminino"
                                                    else -> "Selecione"
                                                },
                                                options = listOf(-1 to "Selecione", 1 to "Masculino", 0 to "Feminino"),
                                                onSelected = { selected ->
                                                    dependentes[index] = dependentes[index].copy(sexo = selected)
                                                },
                                                enabled = !isTitular,
                                            )

                                            SelectionField(
                                                label = "Plano",
                                                value = planoOptions.firstOrNull { it.codigo == dep.plano }?.label ?: "Selecione",
                                                options = listOf(0 to "Selecione") + planoOptions.map { it.codigo to it.label },
                                                enabled = planoOptions.isNotEmpty(),
                                                highlighted = true,
                                                onSelected = { selected ->
                                                    val selectedOption = planoOptions.firstOrNull { it.codigo == selected }
                                                    val planoValor = selectedOption
                                                        ?.valorFor(isTitular = isTitular)
                                                        ?.let(::formatPlanoValor)
                                                        ?: "0,00"
                                                    dependentes[index] = dependentes[index].copy(
                                                        plano = selected,
                                                        planoValor = planoValor,
                                                    )
                                                    persistDraftSnapshotSilently()
                                                },
                                            )

                                            OutlinedTextField(
                                                value = dep.nomeMae,
                                                onValueChange = { value ->
                                                    dependentes[index] = dependentes[index].copy(nomeMae = value)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("Nome da Mae") },
                                                enabled = !isTitular,
                                            )
                                        }
                                    }
                                }

                                Button(onClick = ::addDependente, modifier = Modifier.fillMaxWidth()) {
                                    Text("Incluir dependente")
                                }
                            }
                        }
                    } else {
                        WebCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Resumo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Titular: ${nome.ifBlank { "Nao informado" }}")
                                Text("Empresa: ${cadastro.empresaNome ?: "Nao informada"}")
                                Text("Dependentes: ${dependentes.size}")
                                Text("Contatos: ${contatos.size}")
                                Text("Arquivo obrigatorio: ${if (state.cadastroWorkspace.config?.exigirArquivo == true) "Sim" else "Nao"}")
                            }
                        }

                        WebCard {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Documento", fontWeight = FontWeight.SemiBold)
                                if (arquivoNome.isNotBlank()) {
                                    Text("Arquivo atual: $arquivoNome")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            suppressBackgroundPersist = true
                                            filePicker.launch("*/*")
                                        },
                                        enabled = !uploading,
                                    ) {
                                        if (uploading) {
                                            CircularProgressIndicator(strokeWidth = 2.dp)
                                        } else {
                                            Text(if (arquivoNome.isBlank()) "Selecionar Arquivo" else "Trocar Arquivo")
                                        }
                                    }
                                    if (arquivoPath.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { viewModel.deleteTempFile(arquivoPath) }
                                                    arquivoPath = ""
                                                    arquivoNome = ""
                                                    viewModel.persistCadastroDraftSilently(
                                                        cadastro.id,
                                                        buildJsonObject { put("arquivo_path", "") },
                                                    )
                                                }
                                            },
                                        ) {
                                            Text("Remover")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    localMessage?.let { message ->
                        val tone = resolveCadastroMessageTone(message)
                        val (container, textColor) = messageToneColors(tone)
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
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.profile?.role in setOf("ADMINISTRADOR", "ADMIN") && currentStep == 1) {
                        TextButton(
                            onClick = {
                                viewModel.resolveCadastroOverlay(
                                    CadastroModalSignal(
                                        excluirCadastroId = cadastro.id,
                                        excluirCadastroTitular = cadastro.nome.orEmpty().ifBlank { cadastro.cpf.orEmpty() },
                                    ),
                                )
                            },
                        ) {
                            Text("Excluir")
                        }
                    }

                    TextButton(
                        onClick = { requestCloseEditor() },
                    ) {
                        Text("Cancelar")
                    }

                    if (currentStep == 1) {
                        Button(
                            onClick = {
                                scope.launch {
                                    if (uploading) {
                                        localMessage = "Aguarde o upload do arquivo finalizar antes de salvar."
                                        return@launch
                                    }
                                    val validation = validateStepOne()
                                    if (validation != null) {
                                        localMessage = validation
                                        return@launch
                                    }
                                    val payload = buildPayload(requireStatus = false) ?: return@launch
                                    saving = true
                                    runCatching { viewModel.updateCadastroRecord(cadastro.id, payload) }
                                        .onSuccess { localMessage = "Cadastro salvo com sucesso." }
                                        .onFailure { throwable ->
                                            localMessage = throwable.message ?: "Falha ao salvar cadastro."
                                        }
                                    saving = false
                                }
                            },
                            enabled = !saving && !uploading && !state.sendingCadastro,
                        ) {
                            Text("Salvar")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val validation = validateStepOne()
                                    if (validation != null) {
                                        localMessage = validation
                                        return@launch
                                    }
                                    val payload = buildPayload(requireStatus = false) ?: return@launch
                                    saving = true
                                    runCatching { viewModel.updateCadastroRecord(cadastro.id, payload) }
                                        .onSuccess { currentStep = 2 }
                                        .onFailure { throwable ->
                                            localMessage = throwable.message ?: "Falha ao salvar antes de avançar."
                                        }
                                    saving = false
                                }
                            },
                            enabled = !saving && !uploading && !state.sendingCadastro,
                        ) {
                            Text("Seguinte")
                        }
                    } else {
                        TextButton(
                            onClick = { currentStep = 1 },
                            enabled = !saving && !uploading && !state.sendingCadastro,
                        ) {
                            Text("Voltar")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    if (uploading) {
                                        localMessage = "Aguarde o upload do arquivo finalizar antes de cadastrar."
                                        return@launch
                                    }
                                    val validation = validateStepOne()
                                    if (validation != null) {
                                        localMessage = validation
                                        currentStep = 1
                                        return@launch
                                    }

                                    val payload = buildPayload(requireStatus = false) ?: return@launch
                                    saving = true
                                    runCatching {
                                        viewModel.updateCadastroRecord(cadastro.id, payload)
                                    }.onSuccess { updated ->
                                        viewModel.sendSelectedCadastro(updated, payload)
                                    }.onFailure { throwable ->
                                        localMessage = throwable.message ?: "Falha ao preparar envio."
                                    }
                                    saving = false
                                }
                            },
                            enabled = !saving && !uploading && !state.sendingCadastro,
                        ) {
                            Text(if (state.sendingCadastro) "Enviando..." else "Cadastrar")
                        }
                    }
                }
            }
        }
    }

    if (showSelectStatusOnClose) {
        AlertDialog(
            onDismissRequest = { showSelectStatusOnClose = false },
            title = { Text("Selecionar status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Antes de fechar, selecione o status da adesao.")
                    SelectionField(
                        label = "Status da Adesao",
                        value = state.statusAdesoes.firstOrNull { it.id == statusAdesaoId }?.nome ?: "Selecione",
                        options = listOf("" to "Selecione") + state.statusAdesoes.map { it.id to it.nome },
                        onSelected = { statusAdesaoId = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val payload = buildPayload(requireStatus = state.statusAdesoes.isNotEmpty()) ?: return@launch
                            saving = true
                            runCatching { viewModel.updateCadastroRecord(cadastro.id, payload) }
                                .onSuccess {
                                    showSelectStatusOnClose = false
                                    onDismiss()
                                }
                                .onFailure { throwable ->
                                    localMessage = throwable.message ?: "Falha ao salvar status antes de fechar."
                                }
                            saving = false
                        }
                    },
                    enabled = !saving,
                ) {
                    Text("Salvar e fechar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectStatusOnClose = false }, enabled = !saving) {
                    Text("Continuar editando")
                }
            },
        )
    }
}

private fun textFieldValueSaver(): Saver<TextFieldValue, Any> {
    return Saver(
        save = { listOf(it.text, it.selection.start, it.selection.end) },
        restore = {
            @Suppress("UNCHECKED_CAST")
            val values = it as List<Any>
            val text = values[0] as String
            val start = values[1] as Int
            val end = values[2] as Int
            TextFieldValue(text, TextRange(start, end))
        },
    )
}

private fun extractDateDigits(value: String): String {
    val normalized = value.trim()
    return when {
        Regex("""\d{4}-\d{2}-\d{2}""").matches(normalized) -> {
            val parts = normalized.split("-")
            "${parts[2]}${parts[1]}${parts[0]}"
        }

        Regex("""\d{2}/\d{2}/\d{4}""").matches(normalized) -> normalized.filter(Char::isDigit)
        else -> normalized.filter(Char::isDigit).take(8)
    }
}

private fun toIsoDateOrNull(value: String): String? {
    val digits = value.filter(Char::isDigit)
    if (digits.isEmpty()) return null
    if (digits.length != 8) return null
    val day = digits.substring(0, 2).toIntOrNull() ?: return null
    val month = digits.substring(2, 4).toIntOrNull() ?: return null
    val year = digits.substring(4, 8).toIntOrNull() ?: return null
    if (day !in 1..31 || month !in 1..12 || year !in 1900..2100) return null
    return "%04d-%02d-%02d".format(year, month, day)
}

private fun isUnder18(isoDate: String): Boolean {
    return runCatching {
        val birth = LocalDate.parse(isoDate)
        Period.between(birth, LocalDate.now()).years < 18
    }.getOrDefault(false)
}

private fun validateCpf(cpf: String): Boolean {
    if (cpf.length != 11) return false
    if (cpf.all { it == cpf[0] }) return false

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

private fun parseContatos(cadastro: CadastroDetalhe): List<ContatoFormState> {
    val contatosElement = cadastro.contatos ?: return emptyList()
    val contatosArray = when (contatosElement) {
        is JsonArray -> contatosElement
        is JsonObject -> {
            contatosElement["contatos"]?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: contatosElement["telefones"]?.let { runCatching { it.jsonArray }.getOrNull() }
                ?: contatosElement["items"]?.let { runCatching { it.jsonArray }.getOrNull() }
        }

        else -> runCatching { contatosElement.jsonArray }.getOrNull()
    } ?: return emptyList()

    return contatosArray.mapNotNull { item ->
        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
        val tipoRaw = obj.jsonString("tipo", "tipoContato", "tipo_contato", "kind")
            ?.lowercase(Locale.ROOT)
            ?.trim()
            .orEmpty()
        val valorRaw = obj.jsonString("valor", "telefone", "numero", "contato", "email", "value")
            ?.trim()
            .orEmpty()
        if (valorRaw.isBlank()) return@mapNotNull null

        val tipo = when (tipoRaw) {
            "cel", "cell", "celular" -> "celular"
            "fixo", "telefone", "residencial" -> "fixo"
            "whatsapp", "zap" -> "whatsapp"
            "email", "e-mail" -> "email"
            else -> if (valorRaw.contains('@')) "email" else "celular"
        }
        val valor = if (tipo == "email") valorRaw else valorRaw.filter(Char::isDigit)
        if (valor.isBlank()) return@mapNotNull null

        val principal = obj["principal"]?.jsonPrimitive?.booleanOrNull
            ?: obj["isPrincipal"]?.jsonPrimitive?.booleanOrNull
            ?: obj["prioritario"]?.jsonPrimitive?.booleanOrNull
            ?: obj["principal"]?.jsonPrimitive?.contentOrNull?.equals("true", ignoreCase = true)
            ?: false

        ContatoFormState(tipo = tipo, valor = valor, principal = principal)
    }
}

private fun parseDependentes(cadastro: CadastroDetalhe): List<CadastroDependenteFormState> {
    val dependentesArray = cadastro.dependentes?.jsonArray ?: return emptyList()
    return dependentesArray.mapIndexedNotNull { index, element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
        val nome = obj["nome"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: if (index == 0) cadastro.nome.orEmpty() else ""
        if (nome.isBlank()) return@mapIndexedNotNull null

        val tipo = obj["tipo"]?.jsonPrimitive?.intOrNull
            ?: obj["parentesco"]?.jsonPrimitive?.intOrNull
            ?: if (index == 0) 1 else 0
        val cpf = obj["cpf"]?.jsonPrimitive?.contentOrNull?.filter(Char::isDigit).orEmpty().take(11)
        val dataRaw = obj["dataNascimento"]?.jsonPrimitive?.contentOrNull
            ?: obj["data_nascimento"]?.jsonPrimitive?.contentOrNull
            ?: ""
        val dateDigits = extractDateDigits(dataRaw)

        val sexo = obj["sexo"]?.jsonPrimitive?.intOrNull
            ?: when (obj["sexo"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                "masculino" -> 1
                "feminino" -> 0
                else -> -1
            }

        val plano = obj.jsonInt(
            "plano",
            "plano_codigo",
            "planoCodigo",
            "codigoPlano",
            "codigo_plano",
            "Plano",
        ) ?: 0

        val planoValor = obj["planoValor"]?.jsonPrimitive?.contentOrNull
            ?: obj["plano_valor"]?.jsonPrimitive?.contentOrNull
            ?: "0,00"

        val nomeMae = obj["nomeMae"]?.jsonPrimitive?.contentOrNull
            ?: obj["nome_mae"]?.jsonPrimitive?.contentOrNull
            ?: ""

        CadastroDependenteFormState(
            tipo = tipo,
            nome = nome,
            dataNascimento = TextFieldValue(dateDigits, TextRange(dateDigits.length)),
            cpf = TextFieldValue(cpf, TextRange(cpf.length)),
            sexo = sexo,
            plano = plano,
            planoValor = planoValor,
            nomeMae = nomeMae,
            carenciaAtendimento = obj["carenciaAtendimento"]?.jsonPrimitive?.intOrNull
                ?: obj["carencia_atendimento"]?.jsonPrimitive?.intOrNull
                ?: 0,
        )
    }
}

private fun buildTitularDependente(cadastro: CadastroDetalhe): CadastroDependenteFormState {
    val cpf = cadastro.cpf.filter(Char::isDigit).take(11)
    val dateDigits = extractDateDigits(cadastro.dataNascimento.orEmpty())
    return CadastroDependenteFormState(
        tipo = 1,
        nome = cadastro.nome.orEmpty(),
        dataNascimento = TextFieldValue(dateDigits, TextRange(dateDigits.length)),
        cpf = TextFieldValue(cpf, TextRange(cpf.length)),
        sexo = cadastro.sexoCodigo ?: -1,
        plano = cadastro.planoCodigo ?: 0,
        planoValor = "0,00",
        nomeMae = cadastro.nomeMae.orEmpty(),
        carenciaAtendimento = 0,
    )
}

private fun extractPlanosFromCadastro(
    cadastro: CadastroDetalhe,
    hiddenPlanos: List<String>,
    planosMap: List<br.com.vendamais.mobile.data.models.PlanoMap>,
): List<CadastroPlanoOption> {
    val source = when (val raw = cadastro.planosRaw ?: cadastro.empresaRaw) {
        is JsonObject -> raw["precoPlano"]?.jsonArray
        is JsonArray -> raw
        else -> null
    }
    val hiddenSet = hiddenPlanos.toSet()

    if (source == null) {
        return planosMap
            .filter { it.ativo && !hiddenSet.contains(it.planoId.toString()) }
            .map {
                CadastroPlanoOption(
                    codigo = it.planoId,
                    nome = it.nomeExibicao,
                    label = it.nomeExibicao,
                )
            }
    }

    return source.mapNotNull { item ->
        val obj = item.jsonObject
        val codigo = extractPlanoCodigo(obj) ?: return@mapNotNull null
        if (hiddenSet.contains(codigo.toString())) return@mapNotNull null

        val nome = planosMap.firstOrNull { it.planoId == codigo }?.nomeExibicao
            ?: obj.jsonString("NomeANS", "nomeANS", "nome", "nomeExibicao")
            ?: "Plano $codigo"
        val valorTitular = obj.jsonMoney("ValorTitular", "valorTitular", "valor_titular")
        val valorDependente = obj.jsonMoney("ValorDependente", "valorDependente", "valor_dependente")

        CadastroPlanoOption(
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

private fun CadastroPlanoOption.valorFor(isTitular: Boolean): Double? {
    return if (isTitular) valorTitular else valorDependente
}

private fun formatPlanoValor(value: Double): String {
    return "%.2f".format(Locale.US, value).replace('.', ',')
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

private fun requiresVendedorSelection(profile: MobileProfile?): Boolean {
    return profile?.role in setOf(
        "ADMINISTRADOR",
        "ADMIN",
        "GERENTE",
        "GESTOR",
        "SUPERVISOR",
        "CADASTRO",
        "ADESIONISTA",
        "VENDEDOR",
    )
}

private fun resolveVendedor(
    profile: MobileProfile?,
    state: AppUiState,
    selectedVendedorId: String,
): TeamMemberOption? {
    return if (profile?.role == "VENDEDOR") {
        TeamMemberOption(
            id = profile.id,
            name = profile.name,
            email = profile.email,
            externalId = profile.externalId,
        )
    } else {
        state.vendedores.firstOrNull { it.id == selectedVendedorId }
    }
}

private fun resolveAdesionista(
    profile: MobileProfile?,
    state: AppUiState,
    selectedAdesionistaId: String,
): TeamMemberOption? {
    return if (profile?.role == "ADESIONISTA") {
        TeamMemberOption(
            id = profile.id,
            name = profile.name,
            email = profile.email,
            externalId = profile.externalId,
        )
    } else {
        state.adesionistas.firstOrNull { it.id == selectedAdesionistaId.takeIf { it.isNotBlank() } }
    }
}

private fun TeamMemberOption.toTeamSelectionLabel(): String {
    val codigo = externalId?.takeIf { it.isNotBlank() } ?: "-"
    return "$name - Codigo $codigo"
}

private fun MobileProfile.toTeamSelectionLabel(): String {
    val codigo = externalId?.takeIf { it.isNotBlank() } ?: "-"
    return "$name - Codigo $codigo"
}

private fun resolveCadastroMessageTone(message: String): CadastroMessageTone {
    val normalized = message.lowercase(Locale.ROOT)
    return when {
        normalized.contains("sucesso") -> CadastroMessageTone.SUCCESS
        normalized.contains("falha") || normalized.contains("erro") || normalized.contains("invalid input syntax") -> CadastroMessageTone.ERROR
        normalized.contains("obrigatorio") || normalized.contains("invalido") || normalized.contains("selecione") -> CadastroMessageTone.ALERT
        else -> CadastroMessageTone.WARNING
    }
}

private fun messageToneColors(tone: CadastroMessageTone): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (tone) {
        CadastroMessageTone.SUCCESS -> EmeraldSoft to EmeraldDark
        CadastroMessageTone.WARNING -> Amber100 to Amber500
        CadastroMessageTone.ALERT -> BrandOrange.copy(alpha = 0.18f) to BrandOrange
        CadastroMessageTone.ERROR -> Red100 to Red500
    }
}

private class ContatoPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(11)
        val originalToTransformed = IntArray(digits.length + 1)
        val isMobile = digits.length > 10
        val formatted = buildString {
            originalToTransformed[0] = 0
            digits.forEachIndexed { index, char ->
                if (index == 0) append('(')
                append(char)
                if (index == 1 && digits.length > 2) append(") ")
                if (!isMobile && index == 5 && digits.length > 6) append('-')
                if (isMobile && index == 6 && digits.length > 7) append('-')
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

private class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(8)
        val originalToTransformed = IntArray(digits.length + 1)
        val formatted = buildString {
            originalToTransformed[0] = 0
            digits.forEachIndexed { index, char ->
                append(char)
                if (index == 1 && digits.length > 2) append('/')
                if (index == 3 && digits.length > 4) append('/')
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

private class CadastroDependenteCpfVisualTransformation : VisualTransformation {
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

private fun formatCpf(value: String): String {
    val digits = value.filter(Char::isDigit).take(11)
    return when (digits.length) {
        in 0..3 -> digits
        in 4..6 -> "${digits.take(3)}.${digits.drop(3)}"
        in 7..9 -> "${digits.take(3)}.${digits.substring(3, 6)}.${digits.drop(6)}"
        else -> "${digits.take(3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.drop(9)}"
    }
}

private fun validateUpload(fileName: String, mimeType: String, size: Long) {
    val lower = fileName.lowercase()
    val acceptedName = lower.endsWith(".pdf") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
    val acceptedMime = mimeType in setOf("application/pdf", "image/jpeg", "image/png")
    if (!acceptedName && !acceptedMime) throw IllegalStateException("Arquivo invalido. Use PDF, JPG ou PNG.")
    if (size > MAX_UPLOAD_BYTES) throw IllegalStateException("Arquivo excede 10MB.")
}

private fun resolveFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) {
            return cursor.getString(index)
        }
    }
    return uri.lastPathSegment ?: "arquivo"
}




