package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.PublicCadastroContato
import br.com.vendamais.mobile.data.models.PublicCadastroDependente
import br.com.vendamais.mobile.data.models.PublicCadastroEndereco
import br.com.vendamais.mobile.data.models.PublicCadastroLinkInfo
import br.com.vendamais.mobile.data.models.PublicCadastroPayload
import br.com.vendamais.mobile.data.remote.CadastroPayloadBuilder
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

@Composable
fun PublicAdesaoTokenScreen(
    token: String,
    viewModel: AppViewModel,
    onClose: () -> Unit,
) {
    var loading by rememberSaveable { mutableStateOf(true) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var success by rememberSaveable { mutableStateOf<String?>(null) }
    var linkInfo by remember { mutableStateOf<PublicCadastroLinkInfo?>(null) }
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

    LaunchedEffect(token) {
        loading = true
        error = null
        notice = null
        success = null
        runCatching { viewModel.resolvePublicCadastroLink(token) }
            .onSuccess { response ->
                if (!response.ok || response.link == null) {
                    error = response.error ?: "Link invalido ou inativo."
                } else {
                    linkInfo = response.link
                }
            }
            .onFailure { throwable ->
                error = throwable.message ?: "Falha ao resolver link."
            }
        loading = false
    }

    if (loading) {
        Surface(modifier = Modifier.fillMaxSize()) {
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

    if (linkInfo == null) {
        Surface(modifier = Modifier.fillMaxSize()) {
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

    val empresa = linkInfo!!
    val planoCodigo = remember(empresa.id) { extractPlanoCode(empresa.planosRaw) }
    val planoNome = remember(empresa.id) { extractPlanoName(empresa.planosRaw) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    Text("Plano padrao: ${planoNome ?: "Nao identificado"}")
                }
            }

            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nome,
                        onValueChange = { nome = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome completo") },
                    )
                    OutlinedTextField(
                        value = cpf,
                        onValueChange = { cpf = it.filter(Char::isDigit).take(11) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("CPF (somente numeros)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = dataNascimento,
                        onValueChange = { dataNascimento = it },
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome da mae") },
                    )
                    OutlinedTextField(
                        value = telefone,
                        onValueChange = { telefone = it.filter(Char::isDigit).take(11) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Telefone") },
                    )
                    if (empresa.empresaExigeMatricula == 1) {
                        OutlinedTextField(
                            value = numeroMatricula,
                            onValueChange = { numeroMatricula = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Numero matricula") },
                        )
                    }
                }
            }

            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Endereco", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = cep, onValueChange = { cep = it.filter(Char::isDigit).take(8) }, modifier = Modifier.fillMaxWidth(), label = { Text("CEP") })
                    OutlinedTextField(value = logradouro, onValueChange = { logradouro = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Logradouro") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = numero, onValueChange = { numero = it }, modifier = Modifier.weight(1f), label = { Text("Numero") })
                        OutlinedTextField(value = uf, onValueChange = { uf = it.uppercase().take(2) }, modifier = Modifier.weight(1f), label = { Text("UF") })
                    }
                    OutlinedTextField(value = complemento, onValueChange = { complemento = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Complemento") })
                    OutlinedTextField(value = bairro, onValueChange = { bairro = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Bairro") })
                    OutlinedTextField(value = cidade, onValueChange = { cidade = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cidade") })
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
                TextButton(onClick = onClose, enabled = !submitting) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (planoCodigo == null || planoCodigo <= 0) {
                            error = "Nao foi possivel determinar o plano do link."
                            return@Button
                        }
                        val cpfDigits = CadastroPayloadBuilder.normalizeDigits(cpf)
                        if (!CadastroPayloadBuilder.validateCpf(cpfDigits)) {
                            error = "CPF invalido."
                            return@Button
                        }
                        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(dataNascimento)) {
                            error = "Data nascimento invalida. Use YYYY-MM-DD."
                            return@Button
                        }
                        error = null
                        notice = null
                        success = null
                        submitting = true
                        val sexoCodigo = sexo.toIntOrNull() ?: 1
                        val sexoDescricao = if (sexoCodigo == 1) "Masculino" else "Feminino"

                        val payload = PublicCadastroPayload(
                            cpf = cpfDigits,
                            nome = nome.trim(),
                            dataNascimento = dataNascimento.trim(),
                            sexoCodigo = sexoCodigo,
                            contatos = listOf(
                                PublicCadastroContato(
                                    tipo = "celular",
                                    valor = telefone.filter(Char::isDigit),
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
                            dependentes = listOf(
                                PublicCadastroDependente(
                                    tipo = 1,
                                    nome = nome.trim(),
                                    dataNascimento = dataNascimento.trim(),
                                    cpf = cpfDigits,
                                    sexo = sexoCodigo,
                                    sexoDescricao = sexoDescricao,
                                    plano = planoCodigo,
                                    planoValor = "0,00",
                                    nomeMae = nomeMae.trim(),
                                    carenciaAtendimento = 0,
                                    funcionarioCadastro = 0,
                                ),
                            ),
                        )

                        scope.launch {
                            runCatching {
                                val cpfValidation = viewModel.checkPublicCadastroCpf(token, cpfDigits)
                                if (!cpfValidation.ok) {
                                    throw IllegalStateException(cpfValidation.error ?: "CPF nao permitido neste link.")
                                }
                                viewModel.submitPublicCadastro(token, payload)
                            }.onSuccess { response ->
                                if (!response.ok) {
                                    error = response.error ?: "Falha ao concluir adesao."
                                } else {
                                    success = response.message ?: "Cadastro concluido com sucesso."
                                    notice = response.warning
                                }
                            }.onFailure { throwable ->
                                error = throwable.message ?: "Falha ao enviar cadastro."
                            }
                            submitting = false
                        }
                    },
                    enabled = !submitting,
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

private fun extractPlanoCode(planosRaw: kotlinx.serialization.json.JsonElement?): Int? {
    val array = when (planosRaw) {
        is JsonArray -> planosRaw
        is JsonObject -> planosRaw["precoPlano"] as? JsonArray
        else -> null
    } ?: return null

    val first = array.firstOrNull()?.let { it as? JsonObject } ?: return null
    return first["Plano"]?.jsonPrimitive?.intOrNull
}

private fun extractPlanoName(planosRaw: kotlinx.serialization.json.JsonElement?): String? {
    val array = when (planosRaw) {
        is JsonArray -> planosRaw
        is JsonObject -> planosRaw["precoPlano"] as? JsonArray
        else -> null
    } ?: return null
    val first = array.firstOrNull()?.let { it as? JsonObject } ?: return null
    return first["NomeANS"]?.jsonPrimitive?.contentOrNull ?: first["nomeANS"]?.jsonPrimitive?.contentOrNull
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = content.takeIf { it.isNotBlank() }
