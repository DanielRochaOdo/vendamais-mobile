package br.com.vendamais.mobile.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.PublicCadastroContato
import br.com.vendamais.mobile.data.models.PublicCadastroDependente
import br.com.vendamais.mobile.data.models.PublicCadastroEndereco
import br.com.vendamais.mobile.data.models.PublicCadastroLinkInfo
import br.com.vendamais.mobile.data.models.PublicCadastroPayload
import br.com.vendamais.mobile.data.remote.CadastroPayloadBuilder
import br.com.vendamais.mobile.domain.cadastro.CadastroApiErrorMapper
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus

private const val PUBLIC_CADASTRO_DRAFT_VERSION = 1
private const val PUBLIC_CADASTRO_DRAFT_TTL_MS = 24 * 60 * 60 * 1000L
private const val PUBLIC_CADASTRO_DRAFT_PREFS = "public_cadastro_draft"

private val publicDraftJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
private data class PublicCadastroDraftState(
    val version: Int = PUBLIC_CADASTRO_DRAFT_VERSION,
    val savedAt: Long,
    val cpf: String = "",
    val cpfLocked: Boolean = false,
    val nome: String = "",
    val dataNascimento: String = "",
    val sexo: String = "1",
    val nomeMae: String = "",
    val telefone: String = "",
    val cep: String = "",
    val logradouro: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val numeroMatricula: String = "",
    val selectedPlanoCodigo: String = "",
    val lookupMessage: String = "",
    val submissionId: String = "",
)

private data class PublicPlanoOption(
    val codigo: Int,
    val nome: String,
)

private data class CadastroValidationResult(
    val isValid: Boolean,
    val fieldErrors: Map<String, String>,
)

@Composable
fun PublicAdesaoTokenScreen(
    token: String,
    viewModel: AppViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val draftStorage = remember(context) {
        context.getSharedPreferences(PUBLIC_CADASTRO_DRAFT_PREFS, Context.MODE_PRIVATE)
    }
    val draftStorageKey = remember(token) { "public-cadastro-link-draft:${token.trim()}" }

    var loading by rememberSaveable { mutableStateOf(true) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var consultingCpf by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf<String?>(null) }
    var linkInfo by remember { mutableStateOf<PublicCadastroLinkInfo?>(null) }
    var cpfLocked by rememberSaveable(token) { mutableStateOf(false) }
    var selectedPlanoCodigo by rememberSaveable(token) { mutableStateOf("") }
    var submissionId by rememberSaveable(token) { mutableStateOf("") }
    var draftRestored by rememberSaveable(token) { mutableStateOf(false) }
    val submittingRef = remember(token) { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()

    var nome by rememberSaveable { mutableStateOf("") }
    var cpf by rememberSaveable { mutableStateOf("") }
    var dataNascimento by rememberSaveable { mutableStateOf("") }
    var sexo by rememberSaveable { mutableStateOf("1") }
    var nomeMae by rememberSaveable { mutableStateOf("") }
    var telefone by rememberSaveable { mutableStateOf("") }
    var cep by rememberSaveable { mutableStateOf("") }
    var logradouro by rememberSaveable { mutableStateOf("") }
    var numero by rememberSaveable { mutableStateOf("") }
    var complemento by rememberSaveable { mutableStateOf("") }
    var bairro by rememberSaveable { mutableStateOf("") }
    var cidade by rememberSaveable { mutableStateOf("") }
    var uf by rememberSaveable { mutableStateOf("") }
    var numeroMatricula by rememberSaveable { mutableStateOf("") }
    var consultingCep by rememberSaveable { mutableStateOf(false) }
    var cepLookupError by rememberSaveable { mutableStateOf<String?>(null) }
    var ultimoCepConsultado by rememberSaveable { mutableStateOf("") }
    val enderecoFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )

    LaunchedEffect(token) {
        loading = true
        linkInfo = null
        cpfLocked = false
        draftRestored = false
        error = null
        notice = null
        success = null
        runCatching { viewModel.resolvePublicCadastroLink(token) }
            .onSuccess { response ->
                if (!response.ok || response.link == null) {
                    error = CadastroApiErrorMapper.mapUserMessage(
                        response.error,
                        "Link invalido ou inativo.",
                    )
                } else {
                    linkInfo = response.link
                }
            }
            .onFailure { throwable ->
                error = CadastroApiErrorMapper.mapUserMessage(
                    throwable.message,
                    "Falha ao resolver link.",
                )
            }
        loading = false
    }

    val empresa = linkInfo
    val planoOptions = remember(empresa?.id, empresa?.planosRaw) {
        extractPlanoOptions(empresa?.planosRaw)
    }
    val planoNomePadrao = remember(planoOptions) { planoOptions.firstOrNull()?.nome }
    var fieldErrors by rememberSaveable(token) { mutableStateOf(emptyMap<String, String>()) }

    fun validatePublicCadastro(): CadastroValidationResult {
        val errors = linkedMapOf<String, String>()
        val nomeTrim = nome.trim()
        val cpfDigits = CadastroPayloadBuilder.normalizeDigits(cpf)
        val idade = runCatching { Period.between(LocalDate.parse(dataNascimento.trim()), LocalDate.now()).years }.getOrNull()
        val isMinor = idade != null && idade < 18
        val cpfObrigatorio = !isMinor
        val selectedPlano = planoOptions.firstOrNull { it.codigo.toString() == selectedPlanoCodigo }
        val empresaIdValida = empresa?.id != null

        if (nomeTrim.isBlank()) errors["nome"] = "Informe o nome completo."
        if (dataNascimento.trim().isBlank() || runCatching { LocalDate.parse(dataNascimento.trim()) }.isFailure) errors["dataNascimento"] = "Informe uma data de nascimento válida."
        if (sexo.trim() !in setOf("0", "1")) errors["sexo"] = "Selecione o sexo."
        if (empresaIdValida.not()) errors["empresa"] = "Selecione a empresa."
        if (planoOptions.isEmpty() || selectedPlano == null) errors["plano"] = "Selecione um plano."
        if (selectedPlano != null && selectedPlano.codigo <= 0) errors["plano"] = "Selecione um plano."
        if (selectedPlano != null && selectedPlano.nome.isBlank()) errors["plano"] = "Selecione um plano."
        if (!isMinor && cpfObrigatorio && !CadastroPayloadBuilder.validateCpf(cpfDigits)) errors["cpf"] = "Informe um CPF válido."
        if (nomeMae.trim().isBlank()) errors["nomeMae"] = "Informe o nome da mãe."
        if (telefone.filter(Char::isDigit).length < 10) errors["telefone"] = "Informe ao menos um telefone válido."
        if (cep.filter(Char::isDigit).length != 8) errors["cep"] = "Informe um CEP válido."
        if (logradouro.trim().isBlank()) errors["logradouro"] = "Informe o logradouro."
        if (numero.trim().isBlank()) errors["numero"] = "Informe o número."
        if (bairro.trim().isBlank()) errors["bairro"] = "Informe o bairro."
        if (cidade.trim().isBlank()) errors["cidade"] = "Informe a cidade."
        if (uf.trim().length != 2) errors["uf"] = "Informe a UF."
        if (empresa?.empresaExigeMatricula == 1 && numeroMatricula.trim().isBlank()) errors["numeroMatricula"] = "Informe a matrícula."
        return CadastroValidationResult(errors.isEmpty(), errors)
    }

    LaunchedEffect(planoOptions) {
        if (planoOptions.isEmpty()) {
            selectedPlanoCodigo = ""
            return@LaunchedEffect
        }
        val selectedValido = planoOptions.any { it.codigo.toString() == selectedPlanoCodigo }
        if (!selectedValido) {
            selectedPlanoCodigo = planoOptions.first().codigo.toString()
        }
    }

    LaunchedEffect(empresa?.id, draftStorageKey, draftRestored) {
        if (empresa == null || draftRestored) return@LaunchedEffect
        val rawDraft = draftStorage.getString(draftStorageKey, null)
        if (rawDraft.isNullOrBlank()) {
            draftRestored = true
            return@LaunchedEffect
        }

        val restored = runCatching {
            publicDraftJson.decodeFromString(PublicCadastroDraftState.serializer(), rawDraft)
        }.getOrNull()

        if (
            restored == null ||
            restored.version != PUBLIC_CADASTRO_DRAFT_VERSION ||
            (System.currentTimeMillis() - restored.savedAt) > PUBLIC_CADASTRO_DRAFT_TTL_MS
        ) {
            draftStorage.edit().remove(draftStorageKey).apply()
            draftRestored = true
            return@LaunchedEffect
        }

        cpf = restored.cpf
        cpfLocked = restored.cpfLocked
        nome = restored.nome
        dataNascimento = restored.dataNascimento
        sexo = restored.sexo
        nomeMae = restored.nomeMae
        telefone = restored.telefone
        cep = restored.cep
        logradouro = restored.logradouro
        numero = restored.numero
        complemento = restored.complemento
        bairro = restored.bairro
        cidade = restored.cidade
        uf = restored.uf
        numeroMatricula = restored.numeroMatricula
        selectedPlanoCodigo = restored.selectedPlanoCodigo
        submissionId = restored.submissionId
        notice = restored.lookupMessage.takeIf { it.isNotBlank() }
        draftRestored = true
    }

    LaunchedEffect(
        draftRestored,
        empresa?.id,
        success,
        cpf,
        cpfLocked,
        nome,
        dataNascimento,
        sexo,
        nomeMae,
        telefone,
        cep,
        logradouro,
        numero,
        complemento,
        bairro,
        cidade,
        uf,
        numeroMatricula,
        selectedPlanoCodigo,
        notice,
        submissionId,
    ) {
        if (!draftRestored || empresa == null) return@LaunchedEffect
        if (!success.isNullOrBlank()) {
            draftStorage.edit().remove(draftStorageKey).apply()
            return@LaunchedEffect
        }

        val isEmpty = cpf.isBlank() &&
            !cpfLocked &&
            nome.isBlank() &&
            dataNascimento.isBlank() &&
            nomeMae.isBlank() &&
            telefone.isBlank() &&
            cep.isBlank() &&
            logradouro.isBlank() &&
            numero.isBlank() &&
            complemento.isBlank() &&
            bairro.isBlank() &&
            cidade.isBlank() &&
            uf.isBlank() &&
            numeroMatricula.isBlank()

        if (isEmpty) {
            draftStorage.edit().remove(draftStorageKey).apply()
            return@LaunchedEffect
        }

        val draft = PublicCadastroDraftState(
            savedAt = System.currentTimeMillis(),
            cpf = cpf,
            cpfLocked = cpfLocked,
            nome = nome,
            dataNascimento = dataNascimento,
            sexo = sexo,
            nomeMae = nomeMae,
            telefone = telefone,
            cep = cep,
            logradouro = logradouro,
            numero = numero,
            complemento = complemento,
            bairro = bairro,
            cidade = cidade,
            uf = uf,
            numeroMatricula = numeroMatricula,
            selectedPlanoCodigo = selectedPlanoCodigo,
            lookupMessage = notice.orEmpty(),
            submissionId = submissionId,
        )

        draftStorage.edit()
            .putString(draftStorageKey, publicDraftJson.encodeToString(PublicCadastroDraftState.serializer(), draft))
            .apply()
    }

    LaunchedEffect(cep) {
        val cepNormalizado = cep.filter(Char::isDigit).take(8)
        if (cepNormalizado.length != 8) return@LaunchedEffect
        if (cepNormalizado == ultimoCepConsultado) return@LaunchedEffect

        ultimoCepConsultado = cepNormalizado
        consultingCep = true
        cepLookupError = null

        runCatching { viewModel.consultarEnderecoCep(cepNormalizado) }
            .onSuccess { endereco ->
                if (endereco.cep.isNotBlank()) {
                    cep = endereco.cep.filter(Char::isDigit).take(8)
                }
                if (endereco.logradouro.isNotBlank()) {
                    logradouro = endereco.logradouro
                }
                if (endereco.bairro.isNotBlank()) {
                    bairro = endereco.bairro
                }
                if (endereco.cidade.isNotBlank()) {
                    cidade = endereco.cidade
                }
                val ufNormalizada = endereco.uf
                    .ifBlank { endereco.ufSigla.orEmpty() }
                    .uppercase()
                    .take(2)
                if (ufNormalizada.isNotBlank()) {
                    uf = ufNormalizada
                }
            }
            .onFailure { throwable ->
                cepLookupError = CadastroApiErrorMapper.mapUserMessage(
                    throwable.message,
                    "Nao foi possivel consultar o CEP no S4E.",
                )
            }

        consultingCep = false
    }

    if (loading) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Validando link...", modifier = Modifier.padding(top = 12.dp))
            }
        }
        return
    }

    if (empresa == null) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScreenHeading(
                    title = "Adesao por link",
                    subtitle = "Nao foi possivel iniciar o fluxo publico.",
                )
                Text(error ?: "Link invalido.")
                Button(onClick = onClose) {
                    Text("Fechar")
                }
            }
        }
        return
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeading(
                title = "Adesao por link",
                subtitle = "Empresa ${empresa.empresaNome}",
            )

            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(empresa.empresaNome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("CNPJ: ${empresa.empresaCnpj ?: "-"}")
                    Text("Vendedor: ${empresa.vendedorNome ?: "-"}")
                    empresa.vendedorTelefone?.takeIf { it.isNotBlank() }?.let {
                        Text("Telefone vendedor: $it")
                    }
                    Text("Plano padrao: ${planoNomePadrao ?: "Nao identificado"}")
                }
            }

            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Nome completo") },
                    )
                    OutlinedTextField(
                        value = cpf,
                        onValueChange = { cpf = it.filter(Char::isDigit).take(11) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("CPF (somente numeros)") },
                        singleLine = true,
                        enabled = !cpfLocked && !consultingCpf && !submitting,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val cpfDigits = CadastroPayloadBuilder.normalizeDigits(cpf)
                                if (!CadastroPayloadBuilder.validateCpf(cpfDigits)) {
                                    error = "CPF invalido. Verifique os digitos."
                                    return@Button
                                }
                                consultingCpf = true
                                error = null
                                notice = null
                                scope.launch {
                                    runCatching {
                                        val cpfValidation = viewModel.checkPublicCadastroCpf(token, cpfDigits)
                                        if (!cpfValidation.ok) {
                                            throw IllegalStateException(
                                                CadastroApiErrorMapper.mapUserMessage(
                                                    cpfValidation.error,
                                                    "CPF nao permitido neste link.",
                                                ),
                                            )
                                        }
                                    }.onSuccess {
                                        cpf = cpfDigits
                                        cpfLocked = true
                                        notice = "CPF validado para este link."
                                    }.onFailure { throwable ->
                                        error = CadastroApiErrorMapper.mapUserMessage(
                                            throwable.message,
                                            "Falha ao validar CPF para este link.",
                                        )
                                    }
                                    consultingCpf = false
                                }
                            },
                            enabled = !consultingCpf && !submitting && !cpfLocked,
                        ) {
                            if (consultingCpf) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            } else {
                                Text("Consultar CPF")
                            }
                        }
                        if (cpfLocked) {
                            TextButton(
                                onClick = {
                                    cpfLocked = false
                                    notice = null
                                },
                                enabled = !consultingCpf && !submitting,
                            ) {
                                Text("Alterar CPF")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = dataNascimento,
                        onValueChange = { dataNascimento = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Data nascimento (YYYY-MM-DD)") },
                        singleLine = true,
                    )
                    SelectionField(
                        label = "Sexo",
                        value = if (sexo == "1") "Masculino" else "Feminino",
                        options = listOf("1" to "Masculino", "0" to "Feminino"),
                        onSelected = { sexo = it },
                    )
                    OutlinedTextField(
                        value = nomeMae,
                        onValueChange = { nomeMae = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Nome da mae") },
                    )
                    OutlinedTextField(
                        value = telefone,
                        onValueChange = { telefone = it.filter(Char::isDigit).take(11) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Telefone") },
                    )
                    if (empresa.empresaExigeMatricula == 1) {
                        OutlinedTextField(
                            value = numeroMatricula,
                            onValueChange = { numeroMatricula = it },
                            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                            label = { Text("Numero matricula") },
                        )
                    }
                    SelectionField(
                        label = "Plano do titular",
                        value = planoOptions.firstOrNull { it.codigo.toString() == selectedPlanoCodigo }?.nome ?: "Selecione",
                        options = listOf("" to "Selecione") + planoOptions.map { it.codigo.toString() to it.nome },
                        onSelected = { selectedPlanoCodigo = it },
                    )
                }
            }

            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Endereco", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = cep,
                        onValueChange = {
                            val cepNormalizado = it.filter(Char::isDigit).take(8)
                            cep = cepNormalizado
                            cepLookupError = null
                            if (cepNormalizado.length < 8) {
                                ultimoCepConsultado = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("CEP") },
                        colors = enderecoFieldColors,
                    )
                    if (consultingCep) {
                        Text(
                            text = "Consultando CEP no S4E...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    cepLookupError?.let { cepErro ->
                        Text(
                            text = cepErro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = logradouro,
                        onValueChange = { logradouro = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Logradouro") },
                        colors = enderecoFieldColors,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = numero,
                            onValueChange = { numero = it },
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus(),
                            label = { Text("Numero") },
                            colors = enderecoFieldColors,
                        )
                        OutlinedTextField(
                            value = uf,
                            onValueChange = { uf = it.uppercase().take(2) },
                            modifier = Modifier.weight(1f).bringIntoViewOnFocus(),
                            label = { Text("UF") },
                            colors = enderecoFieldColors,
                        )
                    }
                    OutlinedTextField(
                        value = complemento,
                        onValueChange = { complemento = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Complemento") },
                        colors = enderecoFieldColors,
                    )
                    OutlinedTextField(
                        value = bairro,
                        onValueChange = { bairro = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Bairro") },
                        colors = enderecoFieldColors,
                    )
                    OutlinedTextField(
                        value = cidade,
                        onValueChange = { cidade = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Cidade") },
                        colors = enderecoFieldColors,
                    )
                }
            }

            error?.let {
                WebCard {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            notice?.let {
                WebCard {
                    Text(it)
                }
            }

            success?.let {
                WebCard {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose, enabled = !submitting && !consultingCpf) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val planoCodigo = selectedPlanoCodigo.toIntOrNull() ?: 0
                        if (!cpfLocked) {
                            error = "Consulte o CPF antes de continuar."
                            return@Button
                        }
                        if (planoCodigo <= 0) {
                            error = "Selecione um plano valido para o titular."
                            return@Button
                        }
                        val cpfDigits = CadastroPayloadBuilder.normalizeDigits(cpf)
                        if (!CadastroPayloadBuilder.validateCpf(cpfDigits)) {
                            error = "CPF invalido."
                            return@Button
                        }
                        val dataNascimentoNormalizada = dataNascimento.trim()
                        if (!isIsoDateValid(dataNascimentoNormalizada)) {
                            error = "Data nascimento invalida. Use YYYY-MM-DD."
                            return@Button
                        }
                        if (nome.trim().isBlank()) {
                            error = "Campo obrigatorio: Nome Completo."
                            return@Button
                        }
                        if (nomeMae.trim().isBlank()) {
                            error = "Campo obrigatorio: Nome da Mae."
                            return@Button
                        }
                        val sexoCodigoValido = sexo.toIntOrNull() ?: -1
                        if (sexoCodigoValido !in setOf(0, 1)) {
                            error = "Campo obrigatorio: Sexo."
                            return@Button
                        }
                        val telefoneDigits = telefone.filter(Char::isDigit)
                        if (telefoneDigits.length < 10) {
                            error = "Adicione pelo menos um telefone valido antes de cadastrar."
                            return@Button
                        }
                        if (empresa.empresaExigeMatricula == 1 && numeroMatricula.trim().isBlank()) {
                            error = "Campo obrigatorio: Matricula."
                            return@Button
                        }
                        if (
                            cep.filter(Char::isDigit).length != 8 ||
                            logradouro.trim().isBlank() ||
                            numero.trim().isBlank() ||
                            bairro.trim().isBlank() ||
                            cidade.trim().isBlank() ||
                            uf.trim().length != 2
                        ) {
                            error = "Preencha todos os campos obrigatorios do endereco."
                            return@Button
                        }

                        val validation = validatePublicCadastro()
                        Log.d("CadastroTrace", "operationId=- stage=frontend_validation_started")
                        if (!validation.isValid) {
                            fieldErrors = validation.fieldErrors
                            Log.w(
                                "CadastroTrace",
                                "operationId=- stage=frontend_validation_failed invalidFields=${validation.fieldErrors.keys}",
                            )
                            error = "Revise os campos destacados antes de concluir a adesao."
                            return@Button
                        }
                        Log.d("CadastroTrace", "operationId=- stage=frontend_validation_passed")
                        if (!submittingRef.compareAndSet(false, true)) return@Button
                        val operationId = UUID.randomUUID().toString()
                        Log.d("CadastroTrace", "operationId=$operationId stage=ui_click_public_submit")
                        error = null
                        notice = null
                        success = null
                        submitting = true
                        fieldErrors = emptyMap()
                        if (submissionId.isBlank()) {
                            submissionId = UUID.randomUUID().toString()
                        }
                        val sexoCodigo = sexoCodigoValido
                        val sexoDescricao = if (sexoCodigo == 1) "Masculino" else "Feminino"
                        val dependentes = listOf(
                            PublicCadastroDependente(
                                tipo = 1,
                                nome = nome.trim(),
                                dataNascimento = dataNascimentoNormalizada,
                                cpf = cpfDigits,
                                sexo = sexoCodigo,
                                sexoDescricao = sexoDescricao,
                                plano = planoOptions.firstOrNull { it.codigo.toString() == selectedPlanoCodigo }?.codigo ?: 0,
                                planoValor = "0,00",
                                nomeMae = nomeMae.trim(),
                                carenciaAtendimento = 0,
                                funcionarioCadastro = 0,
                            ),
                        )

                        val payload = PublicCadastroPayload(
                            cpf = cpfDigits,
                            nome = nome.trim(),
                            dataNascimento = dataNascimentoNormalizada,
                            sexoCodigo = sexoCodigo,
                            contatos = listOf(
                                PublicCadastroContato(
                                    tipo = "celular",
                                    valor = telefoneDigits,
                                    principal = true,
                                ),
                            ),
                            endereco = PublicCadastroEndereco(
                                cep = cep,
                                logradouro = logradouro,
                                numero = numero,
                                complemento = complemento.takeIf { it.isNotBlank() },
                                bairro = bairro,
                                cidade = cidade,
                                uf = uf,
                            ),
                            nomeMae = nomeMae.trim(),
                            numeroMatricula = numeroMatricula.takeIf { it.isNotBlank() },
                            dependentes = dependentes,
                        )

                        scope.launch {
                            try {
                                runCatching {
                                    val cpfValidation = viewModel.checkPublicCadastroCpf(token, cpfDigits)
                                    if (!cpfValidation.ok) {
                                        throw IllegalStateException(
                                            CadastroApiErrorMapper.mapUserMessage(
                                                cpfValidation.error,
                                                "CPF nao permitido neste link.",
                                            ),
                                        )
                                    }
                                    val idempotencyKey = "public:${token.trim()}:${cpfDigits}:$submissionId"
                                    viewModel.submitPublicCadastro(token, payload, idempotencyKey, operationId)
                                }.onSuccess { response ->
                                    if (!response.ok) {
                                        error = CadastroApiErrorMapper.mapUserMessage(
                                            response.error,
                                            "Falha ao concluir adesao.",
                                        )
                                    } else {
                                        success = response.message ?: "Cadastro concluido com sucesso."
                                        notice = response.warning
                                        draftStorage.edit().remove(draftStorageKey).apply()
                                        submissionId = ""
                                    }
                                }.onFailure { throwable ->
                                    error = CadastroApiErrorMapper.mapUserMessage(
                                        throwable.message,
                                        "Falha ao enviar cadastro.",
                                    )
                                }
                            } finally {
                                submitting = false
                                submittingRef.set(false)
                            }
                        }
                    },
                    enabled = !submitting && !consultingCpf,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("Concluir adesao")
                    }
                }
            }
        }
    }
}

private fun extractPlanoOptions(planosRaw: kotlinx.serialization.json.JsonElement?): List<PublicPlanoOption> {
    val array = when (planosRaw) {
        is JsonArray -> planosRaw
        is JsonObject -> planosRaw["precoPlano"] as? JsonArray
        else -> null
    } ?: return emptyList()

    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val codigo = item["Plano"]?.jsonPrimitive?.intOrNull
            ?: item["plano"]?.jsonPrimitive?.intOrNull
            ?: item["codigoPlano"]?.jsonPrimitive?.intOrNull
            ?: return@mapNotNull null

        val nome = item["NomeANS"]?.jsonPrimitive?.contentOrNull
            ?: item["nomeANS"]?.jsonPrimitive?.contentOrNull
            ?: item["nome"]?.jsonPrimitive?.contentOrNull
            ?: "Plano $codigo"

        PublicPlanoOption(codigo = codigo, nome = nome)
    }.distinctBy { it.codigo }
}

private fun isIsoDateValid(value: String): Boolean {
    if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(value)) return false
    return runCatching { LocalDate.parse(value) }.isSuccess
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = content.takeIf { it.isNotBlank() }
