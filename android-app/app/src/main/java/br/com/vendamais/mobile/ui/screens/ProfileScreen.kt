package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.AppConfig
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.components.InfoRow
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard

@Composable
fun ProfileScreen(
    state: AppUiState,
    onLogout: () -> Unit,
) {
    val profile = state.profile ?: return

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(
            title = "Meu Perfil",
            subtitle = "Gerencie suas informações pessoais",
        )

        WebCard(title = "Informações Pessoais") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                InfoRow("Nome", profile.name)
                InfoRow("Email", profile.email)
                InfoRow("Telefone", profile.telefone ?: "-")
                InfoRow("Função", roleLabel(profile.role))
                InfoRow("Código do Usuário (ID Externo)", profile.externalId ?: "-")
                InfoRow("Equipe", state.team?.name ?: "-")
                InfoRow("Membro desde", profile.createdAt?.let(::formatDate) ?: "-")
            }
        }

        WebCard(title = "Permissões") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Como ${roleLabel(profile.role)}, você tem as seguintes permissões:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                rolePermissions(profile.role).forEach { permission ->
                    Text("• $permission", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        WebCard(title = "Configuração do App") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                InfoRow("Supabase URL", AppConfig.supabaseUrl.ifBlank { "não configurado" })
                InfoRow("App web público", AppConfig.publicAppUrl.ifBlank { "não configurado" })
            }
        }

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sair", fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun roleLabel(role: String): String {
    return when (role) {
        "ADMINISTRADOR" -> "Administrador"
        "GERENTE" -> "Gerente"
        "SUPERVISOR" -> "Supervisor"
        "VENDEDOR" -> "Vendedor"
        "ADESIONISTA" -> "Adesionista"
        else -> role
    }
}

private fun rolePermissions(role: String): List<String> {
    return when (role) {
        "ADMINISTRADOR" -> listOf(
            "Acesso total ao sistema",
            "Criar, editar e excluir usuários e equipes",
            "Visualizar todos os dados do sistema",
        )
        "GERENTE" -> listOf(
            "Visualizar todas as equipes e usuários",
            "Criar e editar usuários",
            "Acesso a relatórios e estatísticas",
        )
        "SUPERVISOR" -> listOf(
            "Visualizar e gerenciar sua equipe",
            "Criar e editar usuários da sua equipe",
            "Acesso aos dados da sua equipe",
        )
        else -> listOf(
            "Visualizar seu próprio perfil",
            "Editar suas informações pessoais",
            "Acesso às funcionalidades básicas do sistema",
        )
    }
}

private fun formatDate(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value)
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value)
}
