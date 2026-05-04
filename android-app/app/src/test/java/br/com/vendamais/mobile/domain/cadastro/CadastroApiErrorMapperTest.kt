package br.com.vendamais.mobile.domain.cadastro

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CadastroApiErrorMapperTest {

    @Test
    fun `mapErpError should map parceiro invalido message`() {
        val error = CadastroApiErrorMapper.mapErpError("Parceiro inválido para envio.")

        assertThat(error).isInstanceOf(CadastroErpError.ParceiroInvalido::class.java)
    }

    @Test
    fun `mapErpError should map dependente ativo message`() {
        val error = CadastroApiErrorMapper.mapErpError("Dependente(s) ja cadastrado(s) e ativo no contrato.")

        assertThat(error).isInstanceOf(CadastroErpError.DependenteAtivo::class.java)
    }

    @Test
    fun `mapErpError should return null for unknown message`() {
        val error = CadastroApiErrorMapper.mapErpError("timeout na rede")
        assertThat(error).isNull()
    }

    @Test
    fun `mapUserMessage should map only pending cadastro constraint`() {
        val mapped = CadastroApiErrorMapper.mapUserMessage(
            "duplicate key value violates unique constraint \"cadastros_cadastro_incompleto_cpf_unique_idx\"",
            "Falha ao enviar cadastro.",
        )

        assertThat(mapped).isEqualTo("Ja existe um cadastro pendente para este CPF. Abra o pendente e continue por ele.")
    }

    @Test
    fun `mapUserMessage should keep non pending unique constraint raw message`() {
        val raw = "duplicate key value violates unique constraint \"cadastros_public_link_cpf_unique_idx\""
        val mapped = CadastroApiErrorMapper.mapUserMessage(
            raw,
            "Falha ao enviar cadastro.",
        )

        assertThat(mapped).isEqualTo(raw)
    }
}
