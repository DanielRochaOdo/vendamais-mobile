package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    onRefresh: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
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
            subtitle = "Gerencie suas informacoes pessoais",
        )

        WebCard(title = "Preferencias") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Atualizar dados", fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Modo escuro",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Ativa tema escuro para todo o aplicativo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.darkModeEnabled,
                        onCheckedChange = onToggleDarkMode,
                    )
                }
            }
        }

        WebCard(title = "Informacoes Pessoais") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                InfoRow("Nome", profile.name)
                InfoRow("Email", profile.email)
                InfoRow("Telefone", profile.telefone ?: "-")
                InfoRow("Funcao", roleLabel(profile.role))
                InfoRow("Codigo do Usuario (ID Externo)", profile.externalId ?: "-")
                InfoRow("Equipe", state.team?.name ?: "-")
                InfoRow("Membro desde", profile.createdAt?.let(::formatDate) ?: "-")
            }
        }

        WebCard(title = "Permissoes") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Como ${roleLabel(profile.role)}, voce tem as seguintes permissoes:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                rolePermissions(profile.role).forEach { permission ->
                    Text("- $permission", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        WebCard(title = "Configuracao do App") {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                InfoRow("Supabase URL", AppConfig.supabaseUrl.ifBlank { "nao configurado" })
                InfoRow("App web publico", AppConfig.publicAppUrl.ifBlank { "nao configurado" })
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
            "Criar, editar e excluir usuarios e equipes",
            "Visualizar todos os dados do sistema",
        )
        "GERENTE" -> listOf(
            "Visualizar todas as equipes e usuarios",
            "Criar e editar usuarios",
            "Acesso a relatorios e estatisticas",
        )
        "SUPERVISOR" -> listOf(
            "Visualizar e gerenciar sua equipe",
            "Criar e editar usuarios da sua equipe",
            "Acesso aos dados da sua equipe",
        )
        else -> listOf(
            "Visualizar seu proprio perfil",
            "Editar suas informacoes pessoais",
            "Acesso as funcionalidades basicas do sistema",
        )
    }
}

private fun formatDate(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value)
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value)
}
