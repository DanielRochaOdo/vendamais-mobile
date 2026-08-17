package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Red100
import br.com.vendamais.mobile.ui.theme.Red500
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
    var telefone by remember(profile.id, profile.telefone) {
        mutableStateOf(formatProfilePhone(profile.telefone.orEmpty()))
    }
    var externalId by remember(profile.id, profile.externalId) { mutableStateOf(profile.externalId.orEmpty()) }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeading(
            title = "Meu Perfil",
            subtitle = "Identidade, preferencias e manutencao do aplicativo.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = EmeraldSoft,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Emerald,
                )
                Text(
                    text = roleLabel(profile.role),
                    style = MaterialTheme.typography.labelLarge,
                    color = Emerald,
                )
                Text(
                    text = state.team?.name ?: "Sem equipe vinculada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        WebCard(title = "Informacoes pessoais") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (editing) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Nome") },
                        singleLine = true,
                    )
                    InfoRow("Email", profile.email)
                    OutlinedTextField(
                        value = telefone,
                        onValueChange = { telefone = formatProfilePhone(it) },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Telefone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                    )
                    InfoRow("Funcao", roleLabel(profile.role))
                    OutlinedTextField(
                        value = externalId,
                        onValueChange = { externalId = it },
                        modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                        label = { Text("Codigo do usuario (ID Externo)") },
                        singleLine = true,
                    )
                    InfoRow("Equipe", state.team?.name ?: "-")
                    InfoRow("Membro desde", profile.createdAt?.let(::formatProfileDate) ?: "-")

                    error?.let { message ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Red100,
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                color = Red500,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                editing = false
                                error = null
                                name = profile.name
                                telefone = formatProfilePhone(profile.telefone.orEmpty())
                                externalId = profile.externalId.orEmpty()
                            },
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                val digits = telefone.filter(Char::isDigit)
                                when {
                                    name.trim().isBlank() -> error = "Nome e obrigatorio."
                                    digits.isNotBlank() && digits.length != 11 -> {
                                        error = "Telefone deve estar no formato (XX) XXXXX XXXX."
                                    }
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
                            modifier = Modifier.weight(1f),
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Salvar")
                            }
                        }
                    }
                } else {
                    InfoRow("Nome", profile.name)
                    InfoRow("Email", profile.email)
                    InfoRow("Telefone", formatProfilePhone(profile.telefone.orEmpty()).ifBlank { "-" })
                    InfoRow("Funcao", roleLabel(profile.role))
                    InfoRow("Codigo do usuario (ID Externo)", profile.externalId ?: "-")
                    InfoRow("Equipe", state.team?.name ?: "-")
                    InfoRow("Membro desde", profile.createdAt?.let(::formatProfileDate) ?: "-")
                    Button(
                        onClick = { editing = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Editar perfil")
                    }
                }
            }
        }

        WebCard(title = "Preferencias") {
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
                        text = "Ajusta o tema visual em todas as telas do app.",
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

        WebCard(title = "Aplicativo") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow("Versao instalada", BuildConfig.VERSION_NAME)

                Button(
                    onClick = onCheckAndInstallUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(" Verificar atualizacao")
                }

                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(" Atualizar dados")
                }

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = " Sair da conta",
                        color = MaterialTheme.colorScheme.error,
                    )
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

private fun formatProfileDate(value: String): String {
    return runCatching {
        java.time.OffsetDateTime.parse(value)
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(value)
}
