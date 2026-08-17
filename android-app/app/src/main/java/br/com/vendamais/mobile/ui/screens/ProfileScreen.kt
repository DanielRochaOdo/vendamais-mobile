package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.BuildConfig
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.InfoRow
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onCheckAndInstallUpdate: () -> Unit,
) {
    val profile = state.profile ?: return
    val scope = rememberCoroutineScope()
    var editing by remember(profile.id) { mutableStateOf(false) }
    var saving by remember(profile.id) { mutableStateOf(false) }
    var error by remember(profile.id) { mutableStateOf<String?>(null) }
    var name by remember(profile.id, profile.name) { mutableStateOf(profile.name) }
    var telefone by remember(profile.id, profile.telefone) { mutableStateOf(formatProfilePhone(profile.telefone.orEmpty())) }
    var externalId by remember(profile.id, profile.externalId) { mutableStateOf(profile.externalId.orEmpty()) }

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

        WebCard(title = "Informacoes Pessoais") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (editing) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Nome") },
                    )
                    InfoRow("Email", profile.email)
                    OutlinedTextField(
                        value = telefone,
                        onValueChange = { telefone = formatProfilePhone(it) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Telefone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    InfoRow("Funcao", roleLabel(profile.role))
                    OutlinedTextField(
                        value = externalId,
                        onValueChange = { externalId = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Codigo do Usuario (ID Externo)") },
                    )
                    InfoRow("Equipe", state.team?.name ?: "-")
                    InfoRow("Membro desde", profile.createdAt?.let(::formatDate) ?: "-")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                editing = false
                                error = null
                                name = profile.name
                                telefone = formatProfilePhone(profile.telefone.orEmpty())
                                externalId = profile.externalId.orEmpty()
                            },
                            enabled = !saving,
                        ) { Text("Cancelar") }
                        Button(
                            onClick = {
                                val digits = telefone.filter(Char::isDigit)
                                when {
                                    name.trim().isBlank() -> error = "Nome e obrigatorio."
                                    digits.isNotBlank() && digits.length != 11 -> error = "Telefone deve estar no formato (XX) XXXXX XXXX."
                                    else -> {
                                        saving = true
                                        error = null
                                        scope.launch {
                                            runCatching {
                                                viewModel.updateOwnProfile(
                                                    name = name.trim(),
                                                    telefone = digits.takeIf { it.isNotBlank() },
                                                    externalId = externalId.trim().takeIf { it.isNotBlank() },
                                                )
                                            }.onSuccess {
                                                editing = false
                                            }.onFailure { throwable ->
                                                error = throwable.message ?: "Erro ao atualizar perfil."
                                            }
                                            saving = false
                                        }
                                    }
                                }
                            },
                            enabled = !saving,
                        ) {
                            if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Salvar Alteracoes")
                        }
                    }
                } else {
                    InfoRow("Nome", profile.name)
                    InfoRow("Email", profile.email)
                    InfoRow("Telefone", formatProfilePhone(profile.telefone.orEmpty()).ifBlank { "-" })
                    InfoRow("Funcao", roleLabel(profile.role))
                    InfoRow("Codigo do Usuario (ID Externo)", profile.externalId ?: "-")
                    InfoRow("Equipe", state.team?.name ?: "-")
                    InfoRow("Membro desde", profile.createdAt?.let(::formatDate) ?: "-")
                    Button(onClick = { editing = true }) { Text("Editar Perfil") }
                }
            }
        }

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
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Atualizar dados")
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
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Modo escuro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ativa tema escuro para todo o aplicativo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.darkModeEnabled, onCheckedChange = onToggleDarkMode)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoRow("Versao atual", BuildConfig.VERSION_NAME, modifier = Modifier.weight(1f))
                    IconButton(onClick = onCheckAndInstallUpdate) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = "Verificar atualizacao")
                    }
                }
            }
        }
    }
}

private fun formatProfilePhone(value: String): String {
    val digits = value.filter(Char::isDigit).take(11)
    return when (digits.length) {
        in 0..2 -> digits
        in 3..6 -> "(${digits.take(2)}) ${digits.drop(2)}"
        in 7..10 -> "(${digits.take(2)}) ${digits.substring(2, 6)} ${digits.drop(6)}"
        else -> "(${digits.take(2)}) ${digits.substring(2, 7)} ${digits.drop(7)}"
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
