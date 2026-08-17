package br.com.vendamais.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.Period
import java.util.Locale
import java.util.UUID

private const val PUBLIC_PARITY_DRAFT_VERSION = 2
private const val PUBLIC_PARITY_DRAFT_TTL_MS = 24 * 60 * 60 * 1000L

private val publicParityJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class PublicContactDraft(
    val key: String = UUID.randomUUID().toString(),
    val tipo: String = "celular",
    val valor: String = "",
    val principal: Boolean = false,
)

@Serializable
private data class PublicDependentDraft(
    val key: String = UUID.randomUUID().toString(),
    val tipo: Int = 0,
    val nome: String = "",
    val dataNascimento: String = "",
    val cpf: String = "",
    val sexo: Int = -1,
    val plano: Int = 0,
    val planoValor: String = "0,00",
    val nomeMae: String = "",
)

@Serializable
private data class PublicParityDraft(
    val version: Int = PUBLIC_PARITY_DRAFT_VERSION,
    val savedAt: Long,
    val cpf: String = "",
    val cpfLocked: Boolean = false,
    val nome: String = "",
    val dataNascimento: String = "",
    val sexo: Int = -1,
    val nomeMae: String = "",
    val numeroMatricula: String = "",
    val titularPlano: Int = 0,
    val contatos: List<PublicContactDraft> = emptyList(),
    val dependentes: List<PublicDependentDraft> = emptyList(),
    val cep: String = "",
    val logradouro: String = "",
    val numero: String = "",
    val complemento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val uf: String = "",
    val submissionId: String = "",
)

private data class PublicParityPlan(
    val codigo: Int,
    val nome: String,
    val valorTitular: Double? = null,
    val valorDependente: Double? = null,
) {
    fun titularValor(): String = formatPlanValue(valorTitular ?: valorDependente ?: 0.0)
    fun dependenteValor(): String = formatPlanValue(valorDependente ?: valorTitular ?: 0.0)
}

@Composable
fun PublicAdesaoParityScreen(
    token: String,
    viewModel: AppViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("public_cadastro_parity_draft", Context.MODE_PRIVATE) }
    val draftKey = remember(token) { "public-cadastro-link-draft-v2:${token.trim()}" }
    val scope = rememberCoroutineScope()

    var loading by rememberSaveable(token) { mutableStateOf(true) }
    var submitting by rememberSaveable(token) { mutableStateOf(false) }
    var checkingCpf by rememberSaveable(token) { mutableStateOf(false) }
    var checkingCep by rememberSaveable(token) { mutableStateOf(false) }
    var link by remember { mutableStateOf<PublicCadastroLinkInfo?>(null) }
    var error by rememberSaveable(token) { mutableStateOf<String?>(null) }
    var notice by rememberSaveable(token) { mutableStateOf<String?>(null) }
    var success by rememberSaveable(token) { mutableStateOf<String?>(null) }
    var draftRestored by rememberSaveable(token) { mutableStateOf(false) }

    var cpf by rememberSaveable(token) { mutableStateOf("") }
    var cpfLocked by rememberSaveable(token) { mutableStateOf(false) }
    var nome by rememberSaveable(token) { mutableStateOf("") }
    var dataNascimento by rememberSaveable(token) { mutableStateOf("") }
    var sexo by rememberSaveable(token) { mutableStateOf(-1) }
    var nomeMae by rememberSaveable(token) { mutableStateOf("") }
    var numeroMatricula by rememberSaveable(token) { mutableStateOf("") }
    var titularPlano by rememberSaveable(token) { mutableStateOf(0) }
    var cep by rememberSaveable(token) { mutableStateOf("") }
    var logradouro by rememberSaveable(token) { mutableStateOf("") }
    var numero by rememberSaveable(token) { mutableStateOf("") }
    var complemento by rememberSaveable(token) { mutableStateOf("") }
    var bairro by rememberSaveable(token) { mutableStateOf("") }
    var cidade by rememberSaveable(token) { mutableStateOf("") }
    var uf by rememberSaveable(token) { mutableStateOf("") }
    var submissionId by rememberSaveable(token) { mutableStateOf("") }
    val contatos = remember(token) { mutableStateListOf<PublicContactDraft>() }
    val dependentes = remember(token) { mutableStateListOf<PublicDependentDraft>() }

    LaunchedEffect(token) {
        loading = true
        runCatching { viewModel.resolvePublicCadastroLink(token) }
            .onSuccess { result ->
                if (result.ok && result.link != null) link = result.link
                else error = result.error ?: "Link invalido ou inativo."
            }
            .onFailure { error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao carregar link.") }
        loading = false
    }

    val plans = remember(link?.id, link?.planosRaw, link?.planosOcultos) {
        extractPublicParityPlans(link?.planosRaw, link?.planosOcultos.orEmpty())
    }
    val relationships = remember(link?.id, link?.parentescos) {
        link?.parentescos.orEmpty().filter { it.ativo && it.parentescoId != 1 }
    }

    LaunchedEffect(link?.id, draftRestored) {
        if (link == null || draftRestored) return@LaunchedEffect
        val raw = prefs.getString(draftKey, null)
        val draft = raw?.let { runCatching { publicParityJson.decodeFromString(PublicParityDraft.serializer(), it) }.getOrNull() }
        if (draft != null && draft.version == PUBLIC_PARITY_DRAFT_VERSION && System.currentTimeMillis() - draft.savedAt <= PUBLIC_PARITY_DRAFT_TTL_MS) {
            cpf = draft.cpf; cpfLocked = draft.cpfLocked; nome = draft.nome; dataNascimento = draft.dataNascimento; sexo = draft.sexo
            nomeMae = draft.nomeMae; numeroMatricula = draft.numeroMatricula; titularPlano = draft.titularPlano
            cep = draft.cep; logradouro = draft.logradouro; numero = draft.numero; complemento = draft.complemento; bairro = draft.bairro; cidade = draft.cidade; uf = draft.uf
            submissionId = draft.submissionId
            contatos.clear(); contatos.addAll(draft.contatos)
            dependentes.clear(); dependentes.addAll(draft.dependentes)
        } else if (raw != null) {
            prefs.edit().remove(draftKey).apply()
        }
        if (contatos.isEmpty()) contatos.add(PublicContactDraft(principal = true))
        draftRestored = true
    }

    LaunchedEffect(
        draftRestored, success, cpf, cpfLocked, nome, dataNascimento, sexo, nomeMae, numeroMatricula,
        titularPlano, cep, logradouro, numero, complemento, bairro, cidade, uf, submissionId,
        contatos.toList(), dependentes.toList(),
    ) {
        if (!draftRestored || link == null) return@LaunchedEffect
        if (!success.isNullOrBlank()) {
            prefs.edit().remove(draftKey).apply(); return@LaunchedEffect
        }
        val draft = PublicParityDraft(
            savedAt = System.currentTimeMillis(), cpf = cpf, cpfLocked = cpfLocked, nome = nome,
            dataNascimento = dataNascimento, sexo = sexo, nomeMae = nomeMae, numeroMatricula = numeroMatricula,
            titularPlano = titularPlano, contatos = contatos.toList(), dependentes = dependentes.toList(),
            cep = cep, logradouro = logradouro, numero = numero, complemento = complemento,
            bairro = bairro, cidade = cidade, uf = uf, submissionId = submissionId,
        )
        prefs.edit().putString(draftKey, publicParityJson.encodeToString(PublicParityDraft.serializer(), draft)).apply()
    }

    LaunchedEffect(cep) {
        val digits = cep.filter(Char::isDigit).take(8)
        if (digits.length != 8) return@LaunchedEffect
        checkingCep = true
        runCatching { viewModel.consultarEnderecoCepPublic(digits) }
            .onSuccess { address ->
                cep = address.cep.filter(Char::isDigit).take(8)
                if (address.logradouro.isNotBlank()) logradouro = address.logradouro
                if (address.bairro.isNotBlank()) bairro = address.bairro
                if (address.cidade.isNotBlank()) cidade = address.cidade
                if (address.uf.isNotBlank()) uf = address.uf.uppercase().take(2)
            }
            .onFailure { notice = CadastroApiErrorMapper.mapUserMessage(it.message, "CEP nao localizado automaticamente; preencha manualmente.") }
        checkingCep = false
    }

    if (loading) {
        Surface(modifier = Modifier.fillMaxSize()) { Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) { CircularProgressIndicator(); Text("Carregando link...", modifier = Modifier.padding(top = 12.dp)) } }
        return
    }
    val currentLink = link
    if (currentLink == null) {
        Surface(modifier = Modifier.fillMaxSize()) { Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { ScreenHeading("Link indisponivel", error ?: "Nao foi possivel carregar o link."); Button(onClick = onClose) { Text("Fechar") } } }
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScreenHeading("Nova Adesao", "Empresa vinculada: ${currentLink.empresaNome}")
            WebCard { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(currentLink.empresaNome, fontWeight = FontWeight.SemiBold); Text("CNPJ: ${currentLink.empresaCnpj ?: "-"}"); Text("Atendimento: ${currentLink.vendedorNome ?: "-"}"); currentLink.vendedorTelefone?.let { Text("Telefone vendedor: $it") } } }

            WebCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Titular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(cpf, { cpf = it.filter(Char::isDigit).take(11); if (cpfLocked) cpfLocked = false }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("CPF") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !checkingCpf && !submitting)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val digits = cpf.filter(Char::isDigit)
                        if (!CadastroPayloadBuilder.validateCpf(digits)) { error = "CPF invalido. Verifique os digitos."; return@Button }
                        checkingCpf = true; error = null; notice = null
                        scope.launch { runCatching { viewModel.checkPublicCadastroCpf(token, digits) }.onSuccess { result -> if (!result.ok) error = result.error ?: "CPF indisponivel para este link." else { cpf = digits; cpfLocked = true; notice = "CPF validado. Continue o cadastro." } }.onFailure { error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao validar CPF.") }; checkingCpf = false }
                    }, enabled = !checkingCpf && !submitting && !cpfLocked) { if (checkingCpf) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Consultar CPF") }
                    if (cpfLocked) TextButton(onClick = { cpfLocked = false }, enabled = !submitting) { Text("Alterar CPF") }
                }
                OutlinedTextField(nome, { nome = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Nome completo") })
                OutlinedTextField(dataNascimento, { dataNascimento = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Data nascimento (YYYY-MM-DD)") })
                PublicChoiceField("Sexo", if (sexo == 1) "Masculino" else if (sexo == 0) "Feminino" else "Selecione", listOf(1 to "Masculino", 0 to "Feminino")) { sexo = it }
                OutlinedTextField(nomeMae, { nomeMae = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Nome da mae") })
                if (currentLink.empresaExigeMatricula == 1) OutlinedTextField(numeroMatricula, { numeroMatricula = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Numero matricula") })
                PublicChoiceField("Plano do titular", plans.firstOrNull { it.codigo == titularPlano }?.nome ?: "Selecione", plans.map { it.codigo to it.nome }) { titularPlano = it }
            } }

            ContactsCard(contatos, submitting)
            DependentsCard(dependentes, relationships.map { it.parentescoId to it.label }, plans, submitting)

            WebCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Endereco", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(cep, { cep = it.filter(Char::isDigit).take(8) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("CEP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (checkingCep) Text("Consultando CEP no S4E...", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(logradouro, { logradouro = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Logradouro") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(numero, { numero = it }, modifier = Modifier.weight(1f).bringIntoViewOnFocus(), label = { Text("Numero") }); OutlinedTextField(uf, { uf = it.uppercase().take(2) }, modifier = Modifier.weight(1f).bringIntoViewOnFocus(), label = { Text("UF") }) }
                OutlinedTextField(complemento, { complemento = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Complemento") })
                OutlinedTextField(bairro, { bairro = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Bairro") })
                OutlinedTextField(cidade, { cidade = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Cidade") })
            } }

            error?.let { WebCard { Text(it, color = MaterialTheme.colorScheme.error) } }
            notice?.let { WebCard { Text(it) } }
            success?.let { WebCard { Text(it, color = MaterialTheme.colorScheme.primary) } }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose, enabled = !submitting, modifier = Modifier.weight(0.8f)) { Text("Cancelar") }
                Button(onClick = {
                    val validation = validatePublicParityForm(cpfLocked, cpf, nome, dataNascimento, sexo, nomeMae, titularPlano, currentLink.empresaExigeMatricula, numeroMatricula, contatos.toList(), dependentes.toList(), cep, logradouro, numero, bairro, cidade, uf)
                    if (validation != null) { error = validation; return@Button }
                    val cpfDigits = cpf.filter(Char::isDigit)
                    val titularPlan = plans.first { it.codigo == titularPlano }
                    val titular = PublicCadastroDependente(1, nome.trim(), dataNascimento.trim(), cpfDigits, sexo, if (sexo == 1) "Masculino" else "Feminino", titularPlano, titularPlan.titularValor(), nomeMae.trim(), 0, 0)
                    val extras = dependentes.map { dep -> PublicCadastroDependente(dep.tipo, dep.nome.trim(), dep.dataNascimento.trim(), dep.cpf.filter(Char::isDigit), dep.sexo, if (dep.sexo == 1) "Masculino" else "Feminino", dep.plano, dep.planoValor, dep.nomeMae.trim(), 0, 0) }
                    val normalizedContacts = contatos.mapIndexed { index, contact -> PublicCadastroContato(contact.tipo, if (contact.tipo == "email") contact.valor.trim() else contact.valor.filter(Char::isDigit), contact.principal || (index == 0 && contatos.none { it.principal })) }
                    val payload = PublicCadastroPayload(cpfDigits, nome.trim(), dataNascimento.trim(), sexo, normalizedContacts, PublicCadastroEndereco(cep.filter(Char::isDigit), logradouro = logradouro.trim(), numero = numero.trim(), complemento = complemento.trim().takeIf { it.isNotBlank() }, bairro = bairro.trim(), cidade = cidade.trim(), uf = uf.uppercase()), nomeMae.trim(), numeroMatricula.trim().takeIf { it.isNotBlank() }, listOf(titular) + extras)
                    if (submissionId.isBlank()) submissionId = UUID.randomUUID().toString()
                    submitting = true; error = null; success = null
                    scope.launch { runCatching { viewModel.submitPublicCadastro(token, payload, "public:${token.trim()}:$cpfDigits:$submissionId") }.onSuccess { response -> if (!response.ok) error = response.error ?: "Falha ao concluir adesao." else { success = response.message ?: "Cadastro concluido com sucesso."; notice = response.warning; prefs.edit().remove(draftKey).apply(); submissionId = "" } }.onFailure { error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao concluir adesao.") }; submitting = false }
                }, enabled = !submitting && !checkingCpf, modifier = Modifier.weight(1.2f)) { if (submitting) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Concluir adesao") }
            }
        }
    }
}

@Composable
private fun ContactsCard(items: MutableList<PublicContactDraft>, disabled: Boolean) {
    WebCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Contatos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        items.forEachIndexed { index, item -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PublicChoiceField("Tipo", item.tipo.replaceFirstChar { it.uppercase() }, listOf("celular" to "Celular", "fixo" to "Fixo", "whatsapp" to "WhatsApp", "email" to "Email")) { value -> items[index] = item.copy(tipo = value) }
            OutlinedTextField(item.valor, { value -> items[index] = item.copy(valor = if (item.tipo == "email") value else value.filter(Char::isDigit).take(11)) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Contato ${index + 1}") }, keyboardOptions = KeyboardOptions(keyboardType = if (item.tipo == "email") KeyboardType.Email else KeyboardType.Phone), enabled = !disabled)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { items.indices.forEach { i -> items[i] = items[i].copy(principal = i == index) } }, enabled = !disabled) { Text(if (item.principal) "Principal ✓" else "Tornar principal") }; if (items.size > 1) TextButton(onClick = { items.removeAt(index) }, enabled = !disabled) { Text("Remover") } }
        } }
        Button(onClick = { items.add(PublicContactDraft(principal = items.isEmpty())) }, enabled = !disabled) { Text("Adicionar contato") }
    } }
}

@Composable
private fun DependentsCard(items: MutableList<PublicDependentDraft>, relationships: List<Pair<Int, String>>, plans: List<PublicParityPlan>, disabled: Boolean) {
    WebCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Dependentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (relationships.isEmpty()) Text("Nenhum parentesco ativo foi disponibilizado pelo link.", color = MaterialTheme.colorScheme.error)
        items.forEachIndexed { index, dep -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Dependente ${index + 1}", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(dep.nome, { items[index] = dep.copy(nome = it) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Nome") }, enabled = !disabled)
            OutlinedTextField(dep.cpf, { items[index] = dep.copy(cpf = it.filter(Char::isDigit).take(11)) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("CPF") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !disabled)
            OutlinedTextField(dep.dataNascimento, { items[index] = dep.copy(dataNascimento = it) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Data nascimento (YYYY-MM-DD)") }, enabled = !disabled)
            PublicChoiceField("Sexo", if (dep.sexo == 1) "Masculino" else if (dep.sexo == 0) "Feminino" else "Selecione", listOf(1 to "Masculino", 0 to "Feminino")) { items[index] = dep.copy(sexo = it) }
            PublicChoiceField("Grau de parentesco", relationships.firstOrNull { it.first == dep.tipo }?.second ?: "Selecione", relationships) { items[index] = dep.copy(tipo = it) }
            PublicChoiceField("Plano", plans.firstOrNull { it.codigo == dep.plano }?.nome ?: "Selecione", plans.map { it.codigo to it.nome }) { code -> val plan = plans.first { it.codigo == code }; items[index] = dep.copy(plano = code, planoValor = plan.dependenteValor()) }
            OutlinedTextField(dep.nomeMae, { items[index] = dep.copy(nomeMae = it) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Nome da mae") }, enabled = !disabled)
            TextButton(onClick = { items.removeAt(index) }, enabled = !disabled) { Text("Remover dependente") }
        } }
        Button(onClick = { items.add(PublicDependentDraft()) }, enabled = !disabled && relationships.isNotEmpty() && plans.isNotEmpty()) { Text("Adicionar dependente") }
    } }
}

@Composable
private fun <T> PublicChoiceField(label: String, value: String, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = FontWeight.Medium); options.chunked(3).forEach { row -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { (key, display) -> TextButton(onClick = { onSelected(key) }, modifier = Modifier.weight(1f)) { Text(display, maxLines = 1) } }; repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) } } } }
}

private fun validatePublicParityForm(cpfLocked: Boolean, cpf: String, nome: String, data: String, sexo: Int, nomeMae: String, titularPlano: Int, exigeMatricula: Int?, matricula: String, contatos: List<PublicContactDraft>, dependentes: List<PublicDependentDraft>, cep: String, logradouro: String, numero: String, bairro: String, cidade: String, uf: String): String? {
    val cpfDigits = cpf.filter(Char::isDigit)
    if (!cpfLocked) return "Consulte o CPF antes de continuar."
    if (!CadastroPayloadBuilder.validateCpf(cpfDigits)) return "CPF invalido."
    if (nome.trim().isBlank()) return "Campo obrigatorio: Nome Completo."
    if (!isValidPublicIsoDate(data)) return "Campo obrigatorio: Data de Nascimento valida."
    if (sexo !in setOf(0, 1)) return "Campo obrigatorio: Sexo."
    if (nomeMae.trim().isBlank()) return "Campo obrigatorio: Nome da Mae."
    if (titularPlano <= 0) return "Selecione um plano para o titular."
    if (exigeMatricula == 1 && matricula.trim().isBlank()) return "Campo obrigatorio: Matricula."
    if (contatos.none { it.tipo in setOf("celular", "fixo", "whatsapp") && it.valor.filter(Char::isDigit).length >= 10 }) return "Adicione pelo menos um telefone valido antes de cadastrar."
    if (cep.filter(Char::isDigit).length != 8 || logradouro.isBlank() || numero.isBlank() || bairro.isBlank() || cidade.isBlank() || uf.length != 2) return "Preencha todos os campos obrigatorios do endereco."
    dependentes.forEachIndexed { index, dep ->
        if (dep.nome.isBlank()) return "Dependente ${index + 1}: Nome e obrigatorio."
        if (!isValidPublicIsoDate(dep.dataNascimento)) return "Dependente ${index + 1}: Data de nascimento invalida."
        if (publicAge(dep.dataNascimento) >= 18 && dep.cpf.filter(Char::isDigit).length != 11) return "Dependente ${index + 1}: CPF e obrigatorio para maiores de 18 anos."
        if (dep.cpf.isNotBlank() && !CadastroPayloadBuilder.validateCpf(dep.cpf)) return "Dependente ${index + 1}: CPF invalido."
        if (dep.sexo !in setOf(0, 1)) return "Dependente ${index + 1}: Sexo e obrigatorio."
        if (dep.tipo <= 0 || dep.tipo == 1) return "Dependente ${index + 1}: Selecione o grau de parentesco."
        if (dep.plano <= 0) return "Dependente ${index + 1}: Selecione um plano."
        if (dep.nomeMae.isBlank()) return "Dependente ${index + 1}: Nome da Mae e obrigatorio."
    }
    return null
}

private fun extractPublicParityPlans(raw: JsonElement?, hidden: List<String>): List<PublicParityPlan> {
    val array = when (raw) { is JsonArray -> raw; is JsonObject -> raw["precoPlano"] as? JsonArray; else -> null } ?: return emptyList()
    val hiddenIds = hidden.mapNotNull { it.toIntOrNull() }.toSet()
    return array.mapNotNull { element -> val obj = element as? JsonObject ?: return@mapNotNull null; val code = obj.valueInt("Plano", "plano", "codigoPlano") ?: return@mapNotNull null; if (code in hiddenIds) return@mapNotNull null; PublicParityPlan(code, obj.valueString("NomeANS", "nomeANS", "nome") ?: "Plano $code", obj.valueMoney("ValorTitular", "valorTitular"), obj.valueMoney("ValorDependente", "valorDependente")) }.distinctBy { it.codigo }
}

private fun JsonObject.valueInt(vararg keys: String): Int? { keys.forEach { key -> this[key]?.jsonPrimitive?.intOrNull?.let { return it } }; return null }
private fun JsonObject.valueString(vararg keys: String): String? { keys.forEach { key -> this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it } }; return null }
private fun JsonObject.valueMoney(vararg keys: String): Double? { keys.forEach { key -> val raw = this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(); if (raw.isNotBlank()) { val normalized = if (raw.contains(',') && raw.contains('.')) raw.replace(".", "").replace(',', '.') else raw.replace(',', '.'); normalized.toDoubleOrNull()?.let { return it }; this[key]?.jsonPrimitive?.doubleOrNull?.let { return it } } }; return null }
private fun formatPlanValue(value: Double): String = "%.2f".format(Locale.US, value).replace('.', ',')
private fun isValidPublicIsoDate(value: String): Boolean = runCatching { LocalDate.parse(value.trim()) }.isSuccess
private fun publicAge(value: String): Int = runCatching { Period.between(LocalDate.parse(value.trim()), LocalDate.now()).years }.getOrDefault(0)
