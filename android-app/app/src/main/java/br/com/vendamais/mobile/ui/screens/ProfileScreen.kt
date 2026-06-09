package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.BuildConfig
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
    onCheckAndInstallUpdate: () -> Unit,
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
                val refreshing = state.loading
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Atualizar dados",
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sair",
                            tint = Color(0xFFFF9800),
                        )
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoRow(
                        label = "Versao atual",
                        value = BuildConfig.VERSION_NAME,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCheckAndInstallUpdate) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = "Verificar atualizacao",
                        )
                    }
                }
            }
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

private fun formatDate(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value)
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value)
}
