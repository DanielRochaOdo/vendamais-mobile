package br.com.vendamais.mobile.data.remote

import com.google.common.truth.Truth.assertThat
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class CadastroPayloadBuilderTest {

    @Test
    fun `normalizeDigits should keep only numbers`() {
        val normalized = CadastroPayloadBuilder.normalizeDigits("529.982.247-25")
        assertThat(normalized).isEqualTo("52998224725")
    }

    @Test
    fun `validateCpf should return true for valid cpf`() {
        val isValid = CadastroPayloadBuilder.validateCpf("52998224725")
        assertThat(isValid).isTrue()
    }

    @Test
    fun `validateCpf should return false for repeated digits`() {
        val isValid = CadastroPayloadBuilder.validateCpf("11111111111")
        assertThat(isValid).isFalse()
    }

    @Test
    fun `validateCpf should return false for wrong check digits`() {
        val isValid = CadastroPayloadBuilder.validateCpf("12345678900")
        assertThat(isValid).isFalse()
    }

    @Test
    fun `detailToBaseData should parse endereco when API returns JSON string`() {
        val cadastro = cadastroDetalheBase(
            endereco = JsonPrimitive("""{"cep":"60110140","logradouro":"Rua A","bairro":"Centro","cidade":"Fortaleza","uf":"CE"}"""),
        )

        val base = CadastroPayloadBuilder.detailToBaseData(JSON, cadastro)

        assertThat(base.endereco).isNotNull()
        assertThat(base.endereco?.cep).isEqualTo("60110140")
        assertThat(base.endereco?.logradouro).isEqualTo("Rua A")
        assertThat(base.endereco?.cidade).isEqualTo("Fortaleza")
        assertThat(base.endereco?.uf).isEqualTo("CE")
    }

    @Test
    fun `detailToBaseData should parse contatos when API returns JSON string`() {
        val cadastro = cadastroDetalheBase(
            contatos = JsonPrimitive("""[{"tipo":"celular","valor":"85999999999","principal":true}]"""),
        )

        val base = CadastroPayloadBuilder.detailToBaseData(JSON, cadastro)

        assertThat(base.contatos).hasSize(1)
        assertThat(base.contatos.first().tipo).isEqualTo("celular")
        assertThat(base.contatos.first().valor).isEqualTo("85999999999")
        assertThat(base.contatos.first().principal).isTrue()
    }

    private fun cadastroDetalheBase(
        contatos: kotlinx.serialization.json.JsonElement? = null,
        endereco: kotlinx.serialization.json.JsonElement? = null,
    ): CadastroDetalhe {
        return CadastroDetalhe(
            id = "cad-1",
            status = "incompleto",
            nome = "Cliente Teste",
            cpf = "52998224725",
            dataNascimento = "1990-01-01",
            sexoCodigo = 1,
            nomeMae = "Mae Teste",
            contatos = contatos,
            endereco = endereco,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
