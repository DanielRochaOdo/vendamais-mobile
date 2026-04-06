package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.AdminTeam
import br.com.vendamais.mobile.data.models.AdminUser
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Amber100
import br.com.vendamais.mobile.ui.theme.Amber500
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Slate100
import br.com.vendamais.mobile.ui.theme.Slate500
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsersScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    var searchTerm by rememberSaveable { mutableStateOf("") }
    var editingUser by remember { mutableStateOf<AdminUser?>(null) }
    var creatingUser by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val teamNames = remember(state.adminTeams) { state.adminTeams.associateBy({ it.id }, { it.name }) }
    val filteredUsers = remember(state.adminUsers, searchTerm) {
        val normalized = searchTerm.trim().lowercase()
        state.adminUsers.filter { user ->
            normalized.isBlank() ||
                user.name.lowercase().contains(normalized) ||
                user.email.lowercase().contains(normalized)
        }
    }
    val canCreate = state.profile?.role in setOf("ADMINISTRADOR", "GERENTE", "SUPERVISOR")
    val canEditRole = state.profile?.role == "ADMINISTRADOR"

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = "Usuários",
                subtitle = "Gerencie os usuários do sistema",
            )
        }

        item {
            WebCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { searchTerm = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pesquisar por nome") },
                        singleLine = true,
                    )
                    if (canCreate) {
                        Button(onClick = { creatingUser = true }) {
                            Text("Novo Usuário")
                        }
                    }
                }
            }
        }

        if (state.adminLoading && state.adminUsers.isEmpty()) {
            item { AdminLoadingCard() }
        } else if (filteredUsers.isEmpty()) {
            item { EmptyAdminCard("Nenhum usuário encontrado.") }
        } else {
            items(filteredUsers) { user ->
                WebCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = user.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate500,
                                )
                            }
                            TextButton(onClick = { editingUser = user }) {
                                Text("Editar")
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AdminBadge(user.role.roleLabel(), EmeraldSoft, Emerald)
                            AdminBadge(if (user.isActive) "Ativo" else "Inativo", Slate100, Slate500)
                            teamNames[user.teamId]?.let { teamName ->
                                AdminBadge(teamName, Amber100, Amber500)
                            }
                            user.externalId?.takeIf { it.isNotBlank() }?.let { code ->
                                AdminBadge("ID $code", Slate100, Slate500)
                            }
                        }
                        user.telefone?.takeIf { it.isNotBlank() }?.let { phone ->
                            Text("Telefone: ${formatPhone(phone)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (creatingUser) {
        UserEditorDialog(
            title = "Novo Usuário",
            teams = state.adminTeams,
            canEditRole = canEditRole,
            onDismiss = { creatingUser = false },
            onSubmit = { form ->
                scope.launch {
                    runCatching { viewModel.createUser(form.toCreatePayload()) }
                        .onSuccess { creatingUser = false }
                }
            },
        )
    }

    editingUser?.let { user ->
        UserEditorDialog(
            title = "Editar Usuário",
            teams = state.adminTeams,
            canEditRole = canEditRole,
            initialUser = user,
            onDismiss = { editingUser = null },
            onSubmit = { form ->
                scope.launch {
                    runCatching { viewModel.updateUser(user.id, form.toUpdatePayload(canEditRole)) }
                        .onSuccess { editingUser = null }
                }
            },
        )
    }
}

@Composable
private fun UserEditorDialog(
    title: String,
    teams: List<AdminTeam>,
    canEditRole: Boolean,
    initialUser: AdminUser? = null,
    onDismiss: () -> Unit,
    onSubmit: (UserFormState) -> Unit,
) {
    var form by remember(initialUser) { mutableStateOf(UserFormState.from(initialUser)) }
    val requiresTeam = form.role in setOf("CADASTRO", "SUPERVISOR", "VENDEDOR", "ADESIONISTA")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome") },
                    )
                }
                item {
                    OutlinedTextField(
                        value = form.email,
                        onValueChange = { form = form.copy(email = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                }
                if (initialUser == null) {
                    item {
                        OutlinedTextField(
                            value = form.password,
                            onValueChange = { form = form.copy(password = it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Senha") },
                        )
                    }
                }
                item {
                    SelectionField(
                        label = "Função",
                        value = form.role.roleLabel(),
                        options = roleOptions(canEditRole),
                        enabled = initialUser == null || canEditRole,
                        onSelected = { form = form.copy(role = it) },
                    )
                }
                item {
                    OutlinedTextField(
                        value = form.telefone,
                        onValueChange = { form = form.copy(telefone = formatPhone(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Telefone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
                item {
                    OutlinedTextField(
                        value = form.lemmitLimite,
                        onValueChange = { form = form.copy(lemmitLimite = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Limite Lemmit (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                if (requiresTeam) {
                    item {
                        OutlinedTextField(
                            value = form.externalId,
                            onValueChange = { form = form.copy(externalId = it.filter(Char::isDigit)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("ID Externo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    item {
                        SelectionField(
                            label = "Equipe",
                            value = teams.firstOrNull { it.id == form.teamId }?.name ?: "Sem equipe",
                            options = listOf("" to "Sem equipe") + teams.map { it.id to it.name },
                            onSelected = { form = form.copy(teamId = it.ifBlank { null }) },
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Usuário ativo", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Ative ou desative o acesso ao sistema.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                        }
                        Switch(
                            checked = form.isActive,
                            onCheckedChange = { form = form.copy(isActive = it) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = form.isValid(initialUser == null),
                onClick = { onSubmit(form) },
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private data class UserFormState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val telefone: String = "",
    val role: String = "VENDEDOR",
    val externalId: String = "",
    val teamId: String? = null,
    val isActive: Boolean = true,
    val lemmitLimite: String = "",
) {
    fun isValid(isCreate: Boolean): Boolean {
        if (name.isBlank() || email.isBlank()) return false
        if (isCreate && password.length < 6) return false
        return true
    }

    fun toCreatePayload() = buildJsonObject {
        put("name", name.trim())
        put("email", email.trim())
        put("password", password)
        put("role", role)
        if (role in setOf("CADASTRO", "SUPERVISOR", "VENDEDOR", "ADESIONISTA")) {
            put("external_id", externalId)
            teamId?.takeIf { it.isNotBlank() }?.let { put("team_id", it) }
        }
    }

    fun toUpdatePayload(canEditRole: Boolean) = buildJsonObject {
        put("name", name.trim())
        put("email", email.trim())
        put("telefone", onlyDigits(telefone).ifBlank { "" })
        put("is_active", isActive)
        lemmitLimite.toDoubleOrNull()?.let { put("lemmit_limite_consultas", it) }
        if (canEditRole) {
            put("role", role)
        }
        if (role in setOf("CADASTRO", "SUPERVISOR", "VENDEDOR", "ADESIONISTA")) {
            put("external_id", externalId.ifBlank { "" })
            put("team_id", teamId ?: "")
        } else {
            put("external_id", "")
            put("team_id", "")
        }
    }

    companion object {
        fun from(user: AdminUser?) = UserFormState(
            name = user?.name.orEmpty(),
            email = user?.email.orEmpty(),
            telefone = formatPhone(user?.telefone.orEmpty()),
            role = user?.role ?: "VENDEDOR",
            externalId = user?.externalId.orEmpty(),
            teamId = user?.teamId,
            isActive = user?.isActive ?: true,
            lemmitLimite = user?.lemmitLimiteConsultas?.toString().orEmpty(),
        )
    }
}
