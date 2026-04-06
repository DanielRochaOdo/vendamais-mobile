package br.com.vendamais.mobile.domain.cadastro

object CadastroApiErrorMapper {
    private val parceiroRegex = Regex("parceiro.*invalido", RegexOption.IGNORE_CASE)
    private val dependenteAtivoRegex = Regex(
        "(cadastrado\\s+e\\s+ativo\\s+no\\s+contrato|dependente\\(s\\)\\s+ja\\s+cadastrado\\(s\\))",
        RegexOption.IGNORE_CASE,
    )

    fun mapErpError(message: String?): CadastroErpError? {
        val normalized = normalize(message)
        if (normalized.isBlank()) return null

        if (parceiroRegex.containsMatchIn(normalized)) {
            return CadastroErpError.ParceiroInvalido(message.orEmpty().ifBlank { "Parceiro invalido." })
        }

        if (dependenteAtivoRegex.containsMatchIn(normalized)) {
            return CadastroErpError.DependenteAtivo(
                details = listOf(message.orEmpty().ifBlank { "Dependente ativo no contrato." }),
            )
        }

        return null
    }

    private fun normalize(value: String?): String {
        val raw = value.orEmpty().trim()
        if (raw.isBlank()) return ""
        val from = "áàãâäéèêëíìîïóòõôöúùûüçÁÀÃÂÄÉÈÊËÍÌÎÏÓÒÕÔÖÚÙÛÜÇ"
        val to = "aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC"
        val builder = StringBuilder(raw.length)
        for (char in raw) {
            val index = from.indexOf(char)
            builder.append(if (index >= 0) to[index] else char)
        }
        return builder.toString()
    }
}
