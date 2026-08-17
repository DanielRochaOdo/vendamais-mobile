package br.com.vendamais.mobile.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.data.models.AdminTeam
import br.com.vendamais.mobile.data.models.AdminUser
import br.com.vendamais.mobile.ui.AppUiState
import br.com.vendamais.mobile.ui.AppViewModel
import br.com.vendamais.mobile.ui.components.ScreenHeading
import br.com.vendamais.mobile.ui.components.WebCard
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Slate100
import br.com.vendamais.mobile.ui.theme.Slate500
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import br.com.vendamais.mobile.ui.components.bringIntoViewOnFocus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamsScreen(
    state: AppUiState,
    viewModel: AppViewModel,
) {
    val scope = rememberCoroutineScope()
    var creatingTeam by remember { mutableStateOf(false) }
    var editingTeam by remember { mutableStateOf<AdminTeam?>(null) }
    val usersByTeam = remember(state.adminUsers) { state.adminUsers.groupBy { it.teamId } }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeading(
                title = if (state.profile?.role == "SUPERVISOR") "Minha Equipe" else "Equipes",
                subtitle = if (state.profile?.role == "SUPERVISOR") {
                    "Gerencie os membros da sua equipe"
                } else {
                    "Gerencie as equipes do sistema"
                },
            )
        }

        item {
            WebCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.adminTeams.size} equipe(s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.profile?.role == "ADMINISTRADOR") {
                        Button(onClick = { creatingTeam = true }) {
                            Text("Nova Equipe")
                        }
                    }
                }
            }
        }

        if (state.adminLoading && state.adminTeams.isEmpty()) {
            item { AdminLoadingCard() }
        } else if (state.adminTeams.isEmpty()) {
            item { EmptyAdminCard("Nenhuma equipe encontrada.") }
        } else {
            items(state.adminTeams) { team ->
                val members = usersByTeam[team.id].orEmpty()
                WebCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = team.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${members.size} membro(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate500,
                                )
                            }
                            val canEditTeam = state.profile?.role in setOf("ADMINISTRADOR", "GERENTE") ||
                                (state.profile?.role == "SUPERVISOR" && state.profile?.teamId == team.id)
                            if (canEditTeam) {
                                TextButton(onClick = { editingTeam = team }) {
                                    Text(if (state.profile?.role == "SUPERVISOR") "Gerenciar Membros" else "Editar")
                                }
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AdminBadge(if (team.isActive) "Ativa" else "Inativa", EmeraldSoft, Emerald)
                            members.take(5).forEach { member ->
                                AdminBadge(member.name, Slate100, Slate500)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creatingTeam) {
        TeamEditorDialog(
            title = "Nova Equipe",
            allUsers = state.adminUsers,
            onDismiss = { creatingTeam = false },
            onSubmit = { name, _ ->
                scope.launch {
                    runCatching { viewModel.createTeam(name) }
                        .onSuccess { creatingTeam = false }
                }
            },
        )
    }

    editingTeam?.let { team ->
        TeamEditorDialog(
            title = "Editar Equipe",
            team = team,
            allUsers = state.adminUsers,
            onDismiss = { editingTeam = null },
            canEditName = state.profile?.role in setOf("ADMINISTRADOR", "GERENTE"),
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
        )
    }
}

@Composable
private fun TeamEditorDialog(
    title: String,
    allUsers: List<AdminUser>,
    team: AdminTeam? = null,
    canEditName: Boolean = true,
    onDismiss: () -> Unit,
    onSubmit: (String, List<AdminUser>) -> Unit,
) {
    var name by remember(team) { mutableStateOf(team?.name.orEmpty()) }
    val selectedIds = remember(team, allUsers) {
        mutableStateListOf<String>().apply {
            addAll(allUsers.filter { it.teamId == team?.id }.map { it.id })
        }
    }
    val eligibleUsers = remember(allUsers, team?.id) {
        allUsers.filter {
            it.role in setOf("VENDEDOR", "ADESIONISTA") &&
                it.isActive &&
                (it.teamId == team?.id || it.teamId.isNullOrBlank())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                    label = { Text("Nome da Equipe") },
                    enabled = canEditName,
                )
                if (team != null) {
                    Text(
                        text = "Membros",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(eligibleUsers) { user ->
                            val selected = selectedIds.contains(user.id)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selected) {
                                            selectedIds.remove(user.id)
                                        } else {
                                            selectedIds.add(user.id)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) EmeraldSoft else Slate100,
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(user.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = user.role.roleLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate500,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSubmit(
                        name.trim(),
                        eligibleUsers.filter { selectedIds.contains(it.id) },
                    )
                },
            ) {
                Text("Salvar", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
