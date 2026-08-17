from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8-sig")


def write(path: str, content: str) -> None:
    target = ROOT / path
    original = target.read_text(encoding="utf-8-sig")
    if content != original:
        target.write_text(content, encoding="utf-8")
        print(f"updated {path}")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def replace_all(text: str, old: str, new: str, label: str, min_count: int = 1) -> str:
    if old not in text:
        if new in text:
            return text
        raise RuntimeError(f"{label}: expected at least {min_count} matches")
    count = text.count(old)
    if count < min_count:
        raise RuntimeError(f"{label}: expected at least {min_count} matches, found {count}")
    return text.replace(old, new)


# 1) Administrative user updates must use the same Edge Function as Adesart.
repo_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/SupabaseRepository.kt"
text = read(repo_path)
text = replace_once(
    text,
    "import kotlinx.serialization.json.JsonArray\n",
    "import kotlinx.serialization.json.JsonArray\nimport kotlinx.serialization.json.JsonNull\n",
    "SupabaseRepository JsonNull import",
)
old_update_user = '''    suspend fun updateUser(session: SavedSession, id: String, payload: JsonObject): AdminUser {
        return client.safePatch<List<AdminUser>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/profiles?id=eq.$id",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar usuario.")
    }
'''
new_update_user = '''    suspend fun updateUser(session: SavedSession, id: String, payload: JsonObject): AdminUser {
        val requestBody = buildJsonObject {
            put("user_id", id)
            payload.forEach { (key, value) -> put(key, value) }
        }
        val response: JsonObject = client.safePost(
            url = "${AppConfig.supabaseUrl}/functions/v1/update-user",
            json = json,
            body = requestBody,
        ) {
            applyAuthHeaders(session)
        }
        val success = response["success"]
            ?.let { it as? JsonPrimitive }
            ?.content
            ?.toBooleanStrictOrNull()
            ?: false
        if (!success) {
            val message = response["error"]
                ?.let { it as? JsonPrimitive }
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: "Falha ao atualizar usuario."
            throw IllegalStateException(message)
        }
        val user = response["user"] ?: throw IllegalStateException("Usuario atualizado sem retorno do backend.")
        return json.decodeFromString(AdminUser.serializer(), user.toString())
    }

    suspend fun updateOwnProfile(
        session: SavedSession,
        userId: String,
        name: String,
        telefone: String?,
        externalId: String?,
    ): MobileProfile {
        val payload = buildJsonObject {
            put("name", name.trim())
            if (telefone.isNullOrBlank()) put("telefone", JsonNull) else put("telefone", telefone)
            externalId?.trim()?.takeIf { it.isNotBlank() }?.let { put("external_id", it) }
        }
        return client.safePatch<List<MobileProfile>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/profiles?id=eq.$userId",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar perfil.")
    }

    suspend fun updateProfileTeamAssignment(
        session: SavedSession,
        userId: String,
        teamId: String?,
    ): AdminUser {
        val payload = buildJsonObject {
            teamId?.takeIf { it.isNotBlank() }?.let { put("team_id", it) } ?: put("team_id", JsonNull)
        }
        return client.safePatch<List<AdminUser>>(
            url = "${AppConfig.supabaseUrl}/rest/v1/profiles?id=eq.$userId",
            json = json,
            body = payload,
        ) {
            applyAuthHeaders(session)
            header("Prefer", "return=representation")
        }.firstOrNull() ?: throw IllegalStateException("Falha ao atualizar equipe do usuario.")
    }
'''
text = replace_once(text, old_update_user, new_update_user, "SupabaseRepository updateUser")
text = replace_once(
    text,
    '"id,status,tipo_cadastro,nome,cpf,empresa_nome,empresa_cnpj,empresa_codigo,status_adesao_id,vendedor_id,vendedor_nome,adesionista_nome,dependentes,created_at,updated_at"',
    '"id,status,tipo_cadastro,nome,cpf,empresa_nome,empresa_cnpj,empresa_codigo,status_adesao_id,vendedor_id,vendedor_nome,adesionista_nome,dependentes,data_envio,created_at,updated_at"',
    "fetchCadastros data_envio",
)
write(repo_path, text)

# 2) ViewModel exposes faithful team assignment and self-profile update paths.
vm_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/AppViewModel.kt"
text = read(vm_path)
anchor = '''    suspend fun updateUser(id: String, payload: JsonObject): AdminUser {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateUser(activeSession, id, payload)
        refreshAdminData(activeSession)
        return updated
    }
'''
insert = anchor + '''
    suspend fun updateOwnProfile(
        name: String,
        telefone: String?,
        externalId: String?,
    ): MobileProfile {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val profile = _uiState.value.profile ?: throw IllegalStateException("Perfil nao encontrado.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateOwnProfile(
            session = activeSession,
            userId = profile.id,
            name = name,
            telefone = telefone,
            externalId = externalId,
        )
        val updatedTeam = updated.teamId?.takeIf { it.isNotBlank() }?.let { teamId ->
            repository.fetchTeam(activeSession, teamId)
        }
        _uiState.update {
            it.copy(
                profile = updated,
                team = updatedTeam,
                noticeMessage = "Perfil atualizado com sucesso.",
            )
        }
        return updated
    }

    suspend fun updateTeamMemberAssignment(userId: String, teamId: String?) {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        repository.updateProfileTeamAssignment(activeSession, userId, teamId)
        refreshAdminData(activeSession)
    }
'''
text = replace_once(text, anchor, insert, "AppViewModel admin/profile methods")
write(vm_path, text)

# 3) Dashboard permissions match Adesart (GESTOR is not elevated to overview/drilldown).
dashboard_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/DashboardScreen.kt"
text = read(dashboard_path)
text = replace_once(
    text,
    '    val canViewSystemOverview = profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR")\n    val canOpenDrilldown = profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR", "SUPERVISOR")',
    '    val canViewSystemOverview = profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE")\n    val canOpenDrilldown = profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "SUPERVISOR")',
    "Dashboard role parity",
)
write(dashboard_path, text)

# 4) Attachment requirement obeys cadastro_config.exigir_arquivo.
workflow_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt"
text = read(workflow_path)
text = replace_once(
    text,
    '''        if (cadastro.arquivoPath.isNullOrBlank()) {
            throw IllegalStateException("Anexo obrigatorio. Selecione um arquivo antes de finalizar.")
        }
''',
    '''        if (config?.exigirArquivo == true && cadastro.arquivoPath.isNullOrBlank()) {
            throw IllegalStateException("Anexo obrigatorio. Selecione um arquivo antes de finalizar.")
        }
''',
    "exigir_arquivo parity",
)
write(workflow_path, text)

# 5) Team management follows Adesart: VENDEDOR/ADESIONISTA only, unassigned users only,
# removal -> NULL, supervisor only own team, team metadata only admin/manager.
teams_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/TeamsScreen.kt"
text = read(teams_path)
text = replace_once(
    text,
    '''                            if (state.profile?.role in setOf("ADMINISTRADOR", "GERENTE", "SUPERVISOR")) {
                                TextButton(onClick = { editingTeam = team }) {
                                    Text("Editar")
                                }
                            }
''',
    '''                            val canEditTeam = state.profile?.role in setOf("ADMINISTRADOR", "GERENTE") ||
                                (state.profile?.role == "SUPERVISOR" && state.profile?.teamId == team.id)
                            if (canEditTeam) {
                                TextButton(onClick = { editingTeam = team }) {
                                    Text(if (state.profile?.role == "SUPERVISOR") "Gerenciar Membros" else "Editar")
                                }
                            }
''',
    "Teams edit permission",
)
old_submit = '''            onSubmit = { name, selectedUsers ->
                scope.launch {
                    runCatching {
                        viewModel.updateTeam(
                            team.id,
                            buildJsonObject {
                                put("name", name)
                                put("is_active", team.isActive)
                            },
                        )
                        selectedUsers.forEach { member ->
                            viewModel.updateUser(
                                member.id,
                                buildJsonObject {
                                    put("team_id", team.id)
                                },
                            )
                        }
                        state.adminUsers
                            .filter { it.teamId == team.id && selectedUsers.none { selected -> selected.id == it.id } }
                            .forEach { removed ->
                                viewModel.updateUser(
                                    removed.id,
                                    buildJsonObject {
                                        put("team_id", "")
                                    },
                                )
                            }
                    }.onSuccess {
                        editingTeam = null
                    }
                }
            },
'''
new_submit = '''            canEditName = state.profile?.role in setOf("ADMINISTRADOR", "GERENTE"),
            onSubmit = { name, selectedUsers ->
                scope.launch {
                    runCatching {
                        if (state.profile?.role in setOf("ADMINISTRADOR", "GERENTE")) {
                            viewModel.updateTeam(
                                team.id,
                                buildJsonObject {
                                    put("name", name)
                                    put("is_active", team.isActive)
                                },
                            )
                        }
                        val selectedIds = selectedUsers.map { it.id }.toSet()
                        val currentMembers = state.adminUsers.filter {
                            it.teamId == team.id && it.role in setOf("VENDEDOR", "ADESIONISTA")
                        }
                        selectedUsers
                            .filter { it.teamId != team.id }
                            .forEach { member ->
                                viewModel.updateTeamMemberAssignment(member.id, team.id)
                            }
                        currentMembers
                            .filter { it.id !in selectedIds }
                            .forEach { removed ->
                                viewModel.updateTeamMemberAssignment(removed.id, null)
                            }
                    }.onSuccess {
                        editingTeam = null
                    }
                }
            },
'''
text = replace_once(text, old_submit, new_submit, "Teams submit")
text = replace_once(
    text,
    '''    team: AdminTeam? = null,
    onDismiss: () -> Unit,
''',
    '''    team: AdminTeam? = null,
    canEditName: Boolean = true,
    onDismiss: () -> Unit,
''',
    "TeamEditorDialog canEditName parameter",
)
text = replace_once(
    text,
    '''                    label = { Text("Nome da Equipe") },
                )
''',
    '''                    label = { Text("Nome da Equipe") },
                    enabled = canEditName,
                )
''',
    "Team name permission",
)
text = replace_once(
    text,
    '''    val eligibleUsers = remember(allUsers) {
        allUsers.filter { it.role in setOf("SUPERVISOR", "VENDEDOR", "ADESIONISTA", "CADASTRO") && it.isActive }
    }
''',
    '''    val eligibleUsers = remember(allUsers, team?.id) {
        allUsers.filter {
            it.role in setOf("VENDEDOR", "ADESIONISTA") &&
                it.isActive &&
                (it.teamId == team?.id || it.teamId.isNullOrBlank())
        }
    }
''',
    "Team eligible users",
)
write(teams_path, text)

# 6) Sent list uses data_envio fallback updated_at, Link tab is reachable, and delete roles match parent.
models_path = "android-app/app/src/main/java/br/com/vendamais/mobile/data/models/AppModels.kt"
text = read(models_path)
text = replace_once(
    text,
    '''    val dependentes: JsonElement? = null,
    @SerialName("created_at")
''',
    '''    val dependentes: JsonElement? = null,
    @SerialName("data_envio")
    val dataEnvio: String? = null,
    @SerialName("created_at")
''',
    "CadastroResumo dataEnvio",
)
write(models_path, text)

cadastros_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastrosScreen.kt"
text = read(cadastros_path)
text = replace_once(
    text,
    '''    val useSupervisorGroupedView = state.cadastroTab == CadastroAreaTab.INCOMPLETOS && profileRole == "SUPERVISOR"
    val useGerenteGroupedView = state.cadastroTab == CadastroAreaTab.INCOMPLETOS && profileRole == "GERENTE"
    LaunchedEffect(state.cadastroTab) {
        if (state.cadastroTab == CadastroAreaTab.LINK) {
            onTabChange(CadastroAreaTab.NOVO)
        }
    }
''',
    '''    val isListTab = state.cadastroTab in setOf(CadastroAreaTab.INCOMPLETOS, CadastroAreaTab.COMPLETOS)
    val useSupervisorGroupedView = isListTab && profileRole == "SUPERVISOR"
    val useGerenteGroupedView = isListTab && profileRole == "GERENTE"
''',
    "Cadastros link redirect/grouping",
)
text = replace_all(
    text,
    'val cadastroData = parseCadastroDate(cadastro.createdAt)',
    'val cadastroData = parseCadastroDate(resolveCadastroDateForFilter(cadastro, state.cadastroFiltro))',
    "Cadastros date semantics",
    min_count=2,
)
old_tabbar = '''        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CadastroTabButton(
                label = "Nova Adesao",
                selected = selectedTab == CadastroAreaTab.NOVO,
                onClick = { onTabSelected(CadastroAreaTab.NOVO) },
                modifier = Modifier.weight(1f),
            )
        }
'''
new_tabbar = '''        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CadastroTabButton(
                label = "Nova Adesao",
                selected = selectedTab == CadastroAreaTab.NOVO,
                onClick = { onTabSelected(CadastroAreaTab.NOVO) },
                modifier = Modifier.weight(1f),
            )
            CadastroTabButton(
                label = "Link",
                selected = selectedTab == CadastroAreaTab.LINK,
                onClick = { onTabSelected(CadastroAreaTab.LINK) },
                modifier = Modifier.weight(0.62f),
            )
        }
'''
text = replace_once(text, old_tabbar, new_tabbar, "Cadastro Link tab")
text = replace_once(
    text,
    '''private fun canDeleteCadastroByRole(role: String?): Boolean {
    return role in setOf("ADMINISTRADOR", "ADMIN", "VENDEDOR")
}
''',
    '''private fun canDeleteCadastroByRole(role: String?): Boolean {
    return role in setOf("VENDEDOR", "ADESIONISTA")
}
''',
    "Cadastro delete roles",
)
text = replace_once(
    text,
    '''private fun parseCadastroDate(value: String): LocalDate? {
    return runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
}
''',
    '''private fun resolveCadastroDateForFilter(cadastro: CadastroResumo, filtro: CadastroFiltro): String {
    return if (filtro == CadastroFiltro.ENVIADOS) {
        cadastro.dataEnvio?.takeIf { it.isNotBlank() } ?: cadastro.updatedAt
    } else {
        cadastro.createdAt
    }
}

private fun parseCadastroDate(value: String): LocalDate? {
    return runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
}
''',
    "Cadastro date helper",
)
write(cadastros_path, text)

# 7) Profile editing follows Adesart while keeping mobile-only preferences/update controls.
profile_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/ProfileScreen.kt"
profile_content = r'''package br.com.vendamais.mobile.ui.screens

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
'''
write(profile_path, profile_content)

app_path = "android-app/app/src/main/java/br/com/vendamais/mobile/ui/VendaMaisApp.kt"
text = read(app_path)
text = replace_once(
    text,
    '''                    MainTab.PERFIL -> ProfileScreen(
                        state = state,
                        onLogout = viewModel::logout,
''',
    '''                    MainTab.PERFIL -> ProfileScreen(
                        state = state,
                        viewModel = viewModel,
                        onLogout = viewModel::logout,
''',
    "VendaMaisApp profile ViewModel",
)
write(app_path, text)

print("Adesart parity patch applied successfully")
