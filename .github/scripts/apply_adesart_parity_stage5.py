from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8-sig")


def write(path: str, content: str) -> None:
    target = ROOT / path
    current = target.read_text(encoding="utf-8-sig")
    if current != content:
        target.write_text(content, encoding="utf-8")
        print(f"updated {path}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


models_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/models/AdminFeatureModels.kt"
models = read(models_path)
models = replace_once(
    models,
    '''@Serializable
data class PublicCadastroCheckCpfResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val code: String? = null,
)
''',
    '''@Serializable
data class PublicCadastroPrefill(
    val nome: String? = null,
    @SerialName("dataNascimento")
    val dataNascimento: String? = null,
    @SerialName("sexoCodigo")
    val sexoCodigo: Int? = null,
    val contatos: List<PublicCadastroContato> = emptyList(),
    val endereco: PublicCadastroEndereco? = null,
    @SerialName("nomeMae")
    val nomeMae: String? = null,
)

@Serializable
data class PublicCadastroCheckCpfResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val code: String? = null,
    val prefill: PublicCadastroPrefill? = null,
    val message: String? = null,
)
''',
    "public CPF prefill model",
)
write(models_path, models)

screen_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/PublicAdesaoParityScreen.kt"
screen = read(screen_path)
old = '''                        scope.launch { runCatching { viewModel.checkPublicCadastroCpf(token, digits) }.onSuccess { result -> if (!result.ok) error = result.error ?: "CPF indisponivel para este link." else { cpf = digits; cpfLocked = true; notice = "CPF validado. Continue o cadastro." } }.onFailure { error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao validar CPF.") }; checkingCpf = false }
'''
new = '''                        scope.launch {
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
                                                    contatos.add(
                                                        PublicContactDraft(
                                                            tipo = contact.tipo,
                                                            valor = contact.valor,
                                                            principal = contact.principal || (index == 0 && prefill.contatos.none { it.principal }),
                                                        ),
                                                    )
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
                                .onFailure {
                                    error = CadastroApiErrorMapper.mapUserMessage(it.message, "Falha ao validar CPF.")
                                }
                            checkingCpf = false
                        }
'''
screen = replace_once(screen, old, new, "public CPF prefill application")
write(screen_path, screen)

print("Stage 5 public CPF prefill parity applied")
