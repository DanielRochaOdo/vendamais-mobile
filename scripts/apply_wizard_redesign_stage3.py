from pathlib import Path

# Cadastro principal: manter o wizard existente e tornar progresso/nome das etapas explicitos.
cadastro_path = Path('android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastroEditorDialog.kt')
cadastro = cadastro_path.read_text(encoding='utf-8-sig')
old_header = '''                    Column {\n                        Text(\n                            text = "Cadastro",\n                            style = MaterialTheme.typography.headlineSmall,\n                            fontWeight = FontWeight.Bold,\n                        )\n                        Text(\n                            text = "Etapa $currentStep de 2",\n                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        )\n                        Text(\n                            text = cadastro.empresaNome ?: "Sem empresa vinculada",\n                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        )\n                    }'''
new_header = '''                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {\n                        Text(\n                            text = if (currentStep == 1) "Dados da adesao" else "Documento e confirmacao",\n                            style = MaterialTheme.typography.headlineSmall,\n                            fontWeight = FontWeight.Bold,\n                        )\n                        Text(\n                            text = if (currentStep == 1) "Etapa 1 de 2 · Titular, contatos, endereco e dependentes" else "Etapa 2 de 2 · Revise e conclua o envio",\n                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                            style = MaterialTheme.typography.bodySmall,\n                        )\n                        Text(\n                            text = cadastro.empresaNome ?: "Sem empresa vinculada",\n                            color = MaterialTheme.colorScheme.primary,\n                            style = MaterialTheme.typography.labelLarge,\n                            fontWeight = FontWeight.SemiBold,\n                        )\n                    }'''
if cadastro.count(old_header) != 1:
    raise SystemExit(f'Cadastro header match count={cadastro.count(old_header)}')
cadastro = cadastro.replace(old_header, new_header, 1)
old_divider = '''                HorizontalDivider(\n                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),\n                )\n\n                Column('''
new_divider = '''                HorizontalDivider(\n                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),\n                )\n                CadastroWizardProgress(currentStep)\n\n                Column('''
if cadastro.count(old_divider) != 1:
    raise SystemExit(f'Cadastro divider match count={cadastro.count(old_divider)}')
cadastro = cadastro.replace(old_divider, new_divider, 1)
helper = '''\n@Composable\nprivate fun CadastroWizardProgress(step: Int) {\n    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.spacedBy(8.dp),\n    ) {\n        listOf(1 to "Dados", 2 to "Finalizar").forEach { (number, label) ->\n            val active = step >= number\n            Surface(\n                modifier = Modifier.weight(1f),\n                shape = MaterialTheme.shapes.medium,\n                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,\n            ) {\n                Row(\n                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),\n                    horizontalArrangement = Arrangement.spacedBy(6.dp),\n                    verticalAlignment = Alignment.CenterVertically,\n                ) {\n                    Text(\n                        text = number.toString(),\n                        style = MaterialTheme.typography.labelLarge,\n                        fontWeight = FontWeight.Bold,\n                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,\n                    )\n                    Text(\n                        text = label,\n                        style = MaterialTheme.typography.labelMedium,\n                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,\n                    )\n                }\n            }\n        }\n    }\n}\n'''
if 'private fun CadastroWizardProgress' not in cadastro:
    cadastro += helper
cadastro_path.write_text(cadastro, encoding='utf-8')

# Inclusao de dependentes: usar o estado natural (responsavel escolhido ou nao) como progresso do wizard.
inclusao_path = Path('android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/InclusaoDependenteDialog.kt')
inclusao = inclusao_path.read_text(encoding='utf-8-sig')
anchor = '''    var empresaPlanosRaw by remember { mutableStateOf<JsonElement?>(cadastro?.planosRaw ?: cadastro?.empresaRaw) }\n    val resultados = remember { mutableStateListOf<ResponsavelFinanceiroResumo>() }'''
replacement = '''    var empresaPlanosRaw by remember { mutableStateOf<JsonElement?>(cadastro?.planosRaw ?: cadastro?.empresaRaw) }\n    val inclusaoStep = if (responsavelSelecionado == null) 1 else 2\n    val resultados = remember { mutableStateListOf<ResponsavelFinanceiroResumo>() }'''
if inclusao.count(anchor) != 1:
    raise SystemExit(f'Inclusao stage anchor count={inclusao.count(anchor)}')
inclusao = inclusao.replace(anchor, replacement, 1)
old_title = '''                    Text(if (isContinuacao) "Continuar Inclusao de Dependentes" else "Inclusao de Dependente", style = MaterialTheme.typography.headlineSmall)\n\n                if (profile?.role != "VENDEDOR") {'''
new_title = '''                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {\n                        Text(\n                            if (isContinuacao) "Continuar inclusao de dependentes" else "Inclusao de dependente",\n                            style = MaterialTheme.typography.headlineSmall,\n                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,\n                        )\n                        Text(\n                            text = if (inclusaoStep == 1) "Etapa 1 de 2 · Localize o responsavel financeiro" else "Etapa 2 de 2 · Dependentes, documentos e envio",\n                            style = MaterialTheme.typography.bodySmall,\n                            color = MaterialTheme.colorScheme.onSurfaceVariant,\n                        )\n                        InclusaoWizardProgress(inclusaoStep)\n                    }\n\n                if (profile?.role != "VENDEDOR") {'''
if inclusao.count(old_title) != 1:
    raise SystemExit(f'Inclusao title match count={inclusao.count(old_title)}')
inclusao = inclusao.replace(old_title, new_title, 1)
inclusao_helper = '''\n@Composable\nprivate fun InclusaoWizardProgress(step: Int) {\n    Row(\n        modifier = Modifier.fillMaxWidth(),\n        horizontalArrangement = Arrangement.spacedBy(8.dp),\n    ) {\n        listOf(1 to "Responsavel", 2 to "Dependentes").forEach { (number, label) ->\n            val active = step >= number\n            Surface(\n                modifier = Modifier.weight(1f),\n                shape = MaterialTheme.shapes.medium,\n                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,\n            ) {\n                Text(\n                    text = "$number. $label",\n                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),\n                    style = MaterialTheme.typography.labelMedium,\n                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,\n                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,\n                )\n            }\n        }\n    }\n}\n'''
if 'private fun InclusaoWizardProgress' not in inclusao:
    inclusao += inclusao_helper
inclusao_path.write_text(inclusao, encoding='utf-8')

# Fluxo publico: dividir a pagina longa em 3 etapas sem mudar validacoes/payload final.
public_path = Path('android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/PublicAdesaoParityScreen.kt')
public = public_path.read_text(encoding='utf-8')
state_anchor = '    var draftRestored by rememberSaveable(token) { mutableStateOf(false) }\n'
if public.count(state_anchor) != 1:
    raise SystemExit('Public state anchor not found')
public = public.replace(state_anchor, state_anchor + '    var currentStep by rememberSaveable(token) { mutableStateOf(1) }\n', 1)

start_marker = '    Surface(modifier = Modifier.fillMaxSize()) {\n        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {'
end_marker = '\n}\n\n@Composable\nprivate fun ContactsCard'
start = public.find(start_marker)
end = public.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit(f'Public UI markers not found start={start} end={end}')

new_ui = r'''    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ScreenHeading("Nova Adesao", "Preencha em etapas para concluir seu cadastro com seguranca.")
                PublicWizardProgress(currentStep)

                WebCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(currentLink.empresaNome, fontWeight = FontWeight.SemiBold)
                        Text("CNPJ: ${currentLink.empresaCnpj ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        Text("Atendimento: ${currentLink.vendedorNome ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        currentLink.vendedorTelefone?.let { Text("Telefone vendedor: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }

                when (currentStep) {
                    1 -> WebCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Titular", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Primeiro valide o CPF; os dados disponiveis serao preenchidos automaticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(cpf, { cpf = it.filter(Char::isDigit).take(11); if (cpfLocked) cpfLocked = false }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("CPF") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !checkingCpf && !submitting)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val digits = cpf.filter(Char::isDigit)
                                    if (!CadastroPayloadBuilder.validateCpf(digits)) { error = "CPF invalido. Verifique os digitos."; return@Button }
                                    checkingCpf = true; error = null; notice = null
                                    scope.launch {
                                        runCatching { viewModel.checkPublicCadastroCpf(token, digits) }
                                            .onSuccess { result ->
                                                if (!result.ok) {
                                                    error = result.error ?: "CPF indisponivel para este link."
                                                } else {
                                                    cpf = digits
                                                    cpfLocked = true
                                                    result.prefill?.let { prefill ->
                                                        prefill.nome?.takeIf { it.isNotBlank() }?.let { nome = it }
                                                        prefill.dataNascimento?.takeIf { it.isNotBlank() }?.let { dataNascimento = it }
                                                        prefill.sexoCodigo?.takeIf { it in setOf(0, 1) }?.let { sexo = it }
                                                        prefill.nomeMae?.takeIf { it.isNotBlank() }?.let { nomeMae = it }
                                                        if (prefill.contatos.isNotEmpty()) {
                                                            contatos.clear()
                                                            prefill.contatos.forEachIndexed { index, contact ->
                                                                contatos.add(PublicContactDraft(tipo = contact.tipo, valor = contact.valor, principal = contact.principal || (index == 0 && prefill.contatos.none { it.principal })))
                                                            }
                                                        }
                                                        prefill.endereco?.let { address ->
                                                            if (address.cep.isNotBlank()) cep = address.cep.filter(Char::isDigit).take(8)
                                                            if (address.logradouro.isNotBlank()) logradouro = address.logradouro
                                                            if (address.numero.isNotBlank()) numero = address.numero
                                                            if (!address.complemento.isNullOrBlank()) complemento = address.complemento
                                                            if (address.bairro.isNotBlank()) bairro = address.bairro
                                                            if (address.cidade.isNotBlank()) cidade = address.cidade
                                                            if (address.uf.isNotBlank()) uf = address.uf.uppercase().take(2)
                                                        }
                                                    }
                                                    if (contatos.isEmpty()) contatos.add(PublicContactDraft(principal = true))
                                                    notice = result.message ?: "CPF validado. Continue o cadastro."
                                                }
                                            }
                                            .onFailure { error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao validar CPF.") }
                                        checkingCpf = false
                                    }
                                }, enabled = !checkingCpf && !submitting && !cpfLocked, modifier = Modifier.weight(1f)) {
                                    if (checkingCpf) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Consultar CPF")
                                }
                                if (cpfLocked) TextButton(onClick = { cpfLocked = false }, enabled = !submitting) { Text("Alterar") }
                            }
                            OutlinedTextField(nome, { nome = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Nome completo") })
                            OutlinedTextField(dataNascimento, { dataNascimento = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Data nascimento (YYYY-MM-DD)") })
                            PublicChoiceField("Sexo", if (sexo == 1) "Masculino" else if (sexo == 0) "Feminino" else "Selecione", listOf(1 to "Masculino", 0 to "Feminino")) { sexo = it }
                            OutlinedTextField(nomeMae, { nomeMae = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Nome da mae") })
                            if (currentLink.empresaExigeMatricula == 1) OutlinedTextField(numeroMatricula, { numeroMatricula = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Numero matricula") })
                            PublicChoiceField("Plano do titular", plans.firstOrNull { it.codigo == titularPlano }?.nome ?: "Selecione", plans.map { it.codigo to it.nome }) { titularPlano = it }
                        }
                    }

                    2 -> {
                        ContactsCard(contatos, submitting)
                        DependentsCard(dependentes, relationships.map { it.parentescoId to it.label }, plans, submitting)
                    }

                    else -> WebCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Endereco e confirmacao", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Revise o endereco antes de concluir a adesao.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(cep, { cep = it.filter(Char::isDigit).take(8) }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("CEP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            if (checkingCep) Text("Consultando CEP no S4E...", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(logradouro, { logradouro = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Logradouro") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(numero, { numero = it }, modifier = Modifier.weight(1f).bringIntoViewOnFocus(), label = { Text("Numero") })
                                OutlinedTextField(uf, { uf = it.uppercase().take(2) }, modifier = Modifier.weight(1f).bringIntoViewOnFocus(), label = { Text("UF") })
                            }
                            OutlinedTextField(complemento, { complemento = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Complemento") })
                            OutlinedTextField(bairro, { bairro = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Bairro") })
                            OutlinedTextField(cidade, { cidade = it }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(), label = { Text("Cidade") })
                        }
                    }
                }

                error?.let { WebCard { Text(it, color = MaterialTheme.colorScheme.error) } }
                notice?.let { WebCard { Text(it) } }
                success?.let { WebCard { Text(it, color = MaterialTheme.colorScheme.primary) } }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (currentStep == 1) {
                    TextButton(onClick = onClose, enabled = !submitting, modifier = Modifier.weight(0.8f)) { Text("Sair") }
                } else {
                    TextButton(onClick = { currentStep-- ; error = null }, enabled = !submitting, modifier = Modifier.weight(0.8f)) { Text("Voltar") }
                }

                if (currentStep < 3) {
                    Button(
                        onClick = {
                            val validation = if (currentStep == 1) {
                                validatePublicIdentityStep(cpfLocked, cpf, nome, dataNascimento, sexo, nomeMae, titularPlano, currentLink.empresaExigeMatricula, numeroMatricula)
                            } else {
                                validatePublicHouseholdStep(contatos.toList(), dependentes.toList())
                            }
                            if (validation != null) error = validation else { error = null; currentStep++ }
                        },
                        enabled = !submitting && !checkingCpf,
                        modifier = Modifier.weight(1.2f),
                    ) { Text("Continuar") }
                } else {
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
                        scope.launch {
                            runCatching { viewModel.submitPublicCadastro(token, payload, "public:${token.trim()}:$cpfDigits:$submissionId") }
                                .onSuccess { response ->
                                    if (!response.ok) error = response.error ?: "Falha ao concluir adesao."
                                    else {
                                        success = response.message ?: "Cadastro concluido com sucesso."
                                        notice = response.warning
                                        prefs.edit().remove(draftKey).apply()
                                        submissionId = ""
                                    }
                                }
                                .onFailure { error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao concluir adesao.") }
                            submitting = false
                        }
                    }, enabled = !submitting && !checkingCpf, modifier = Modifier.weight(1.2f)) {
                        if (submitting) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Concluir adesao")
                    }
                }
            }
        }
    }
}'''
public = public[:start] + new_ui + public[end+2:]  # remove old function closing; new_ui includes it

helpers = r'''

@Composable
private fun PublicWizardProgress(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(1 to "Titular", 2 to "Familia", 3 to "Endereco").forEach { (number, label) ->
            val active = step >= number
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)) {
                    Text(number.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun validatePublicIdentityStep(cpfLocked: Boolean, cpf: String, nome: String, data: String, sexo: Int, nomeMae: String, titularPlano: Int, exigeMatricula: Int?, matricula: String): String? {
    val cpfDigits = cpf.filter(Char::isDigit)
    if (!cpfLocked) return "Consulte o CPF antes de continuar."
    if (!CadastroPayloadBuilder.validateCpf(cpfDigits)) return "CPF invalido."
    if (nome.trim().isBlank()) return "Campo obrigatorio: Nome Completo."
    if (!isValidPublicIsoDate(data)) return "Campo obrigatorio: Data de Nascimento valida."
    if (sexo !in setOf(0, 1)) return "Campo obrigatorio: Sexo."
    if (nomeMae.trim().isBlank()) return "Campo obrigatorio: Nome da Mae."
    if (titularPlano <= 0) return "Selecione um plano para o titular."
    if (exigeMatricula == 1 && matricula.trim().isBlank()) return "Campo obrigatorio: Matricula."
    return null
}

private fun validatePublicHouseholdStep(contatos: List<PublicContactDraft>, dependentes: List<PublicDependentDraft>): String? {
    if (contatos.none { it.tipo in setOf("celular", "fixo", "whatsapp") && it.valor.filter(Char::isDigit).length >= 10 }) {
        return "Adicione pelo menos um telefone valido antes de continuar."
    }
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
'''
insert_before = '\nprivate fun validatePublicParityForm('
if public.count(insert_before) != 1:
    raise SystemExit('Public validate insertion marker not unique')
public = public.replace(insert_before, helpers + insert_before, 1)
public_path.write_text(public, encoding='utf-8')

print('Wizard redesign applied to Cadastro, Inclusao and Public flow')
