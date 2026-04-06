package br.com.vendamais.mobile.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}
