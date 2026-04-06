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
}
