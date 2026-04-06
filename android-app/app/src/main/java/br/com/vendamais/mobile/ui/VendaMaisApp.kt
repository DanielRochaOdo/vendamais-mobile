package br.com.vendamais.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.vendamais.mobile.AppConfig
import br.com.vendamais.mobile.ui.components.VendaBrandIcon
import br.com.vendamais.mobile.ui.components.ScreenBackground
import br.com.vendamais.mobile.ui.screens.CadastroDetailDialog
import br.com.vendamais.mobile.ui.screens.CadastroEditorDialog
import br.com.vendamais.mobile.ui.screens.CadastroOverlayDialogs
import br.com.vendamais.mobile.ui.screens.CadastrosScreen
import br.com.vendamais.mobile.ui.screens.DashboardScreen
import br.com.vendamais.mobile.ui.screens.InclusaoDependenteDialog
import br.com.vendamais.mobile.ui.screens.LoginScreen
import br.com.vendamais.mobile.ui.screens.ProfileScreen
import br.com.vendamais.mobile.ui.screens.SettingsScreen
import br.com.vendamais.mobile.ui.screens.TeamsScreen
import br.com.vendamais.mobile.ui.screens.UsersScreen
import br.com.vendamais.mobile.ui.screens.AuditoriaLemmitScreen
import br.com.vendamais.mobile.ui.screens.FilaUploadErpScreen
import br.com.vendamais.mobile.ui.screens.AdesoesExcluidasScreen
import br.com.vendamais.mobile.ui.screens.PublicAdesaoTokenScreen
import br.com.vendamais.mobile.ui.theme.Amber100
import br.com.vendamais.mobile.ui.theme.Amber500
import br.com.vendamais.mobile.ui.theme.BrandOrange
import br.com.vendamais.mobile.ui.theme.Emerald
import br.com.vendamais.mobile.ui.theme.EmeraldDark
import br.com.vendamais.mobile.ui.theme.EmeraldSoft
import br.com.vendamais.mobile.ui.theme.Red100
import br.com.vendamais.mobile.ui.theme.Red500
import br.com.vendamais.mobile.ui.theme.Slate100
import br.com.vendamais.mobile.ui.theme.Slate200
import br.com.vendamais.mobile.ui.theme.Slate500
import br.com.vendamais.mobile.ui.theme.White
import java.util.Locale

@Composable
fun VendaMaisApp(
    viewModel: AppViewModel,
    deepLinkToken: String? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isDrawerOpen by rememberSaveable { mutableStateOf(false) }
    val openWebApp = remember(context) {
        {
            if (AppConfig.publicAppUrl.isNotBlank()) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.publicAppUrl)))
            }
        }
    }

    LaunchedEffect(deepLinkToken) {
        deepLinkToken
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(viewModel::openPublicTokenFlow)
    }

    state.publicToken?.let { token ->
        PublicAdesaoTokenScreen(
            token = token,
            viewModel = viewModel,
            onClose = viewModel::closePublicTokenFlow,
        )
        return
    }

    if (!state.isAuthenticated) {
        LoginScreen(
            state = state,
            onEmailChange = viewModel::updateEmail,
            onPasswordChange = viewModel::updatePassword,
            onLogin = viewModel::login,
        )
        return
    }

    ScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 92.dp),
            ) {
                when (state.activeTab) {
                    MainTab.DASHBOARD -> DashboardScreen(
                        state = state,
                        onOpenDrilldown = viewModel::openDashboardDrilldown,
                        onCloseDrilldown = viewModel::closeDashboardDrilldown,
                    )
                    MainTab.CADASTROS -> CadastrosScreen(
                        state = state,
                        viewModel = viewModel,
                        onTabChange = viewModel::selectCadastroAreaTab,
                        onFilterChange = viewModel::selectCadastroFiltro,
                        onCadastroClick = viewModel::openCadastro,
                        onSearchTypeChange = viewModel::updateEmpresaSearchType,
                        onSearchValueChange = viewModel::updateEmpresaSearchValue,
                        onSearchEmpresa = viewModel::searchEmpresas,
                        onSelectEmpresa = viewModel::selectEmpresa,
                        onClearEmpresa = viewModel::clearSelectedEmpresa,
                        onCpfChange = viewModel::updateCpfValue,
                        onSelectedVendedorChange = viewModel::updateSelectedVendedor,
                        onSelectedAdesionistaChange = viewModel::updateSelectedAdesionista,
                        onConsultarCpf = viewModel::createDraftFromCpf,
                        onLinkSearchTypeChange = viewModel::updateLinkSearchType,
                        onLinkSearchValueChange = viewModel::updateLinkSearchValue,
                        onLinkSearchEmpresa = viewModel::searchEmpresasForLink,
                        onLinkSelectEmpresa = viewModel::selectLinkEmpresa,
                        onLinkClearEmpresa = viewModel::clearLinkEmpresa,
                        onGenerateLink = viewModel::createCadastroLink,
                        onRegenerateLink = viewModel::regenerateCadastroLink,
                        onDeleteLink = viewModel::deleteCadastroLink,
                        onOpenWebApp = if (AppConfig.publicAppUrl.isBlank()) null else openWebApp,
                    )
                    MainTab.USERS -> UsersScreen(
                        state = state,
                        viewModel = viewModel,
                    )
                    MainTab.TEAMS -> TeamsScreen(
                        state = state,
                        viewModel = viewModel,
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        state = state,
                        viewModel = viewModel,
                    )
                    MainTab.AUDITORIA_LEMMIT -> AuditoriaLemmitScreen(
                        state = state,
                        viewModel = viewModel,
                    )
                    MainTab.FILA_UPLOAD_ERP -> FilaUploadErpScreen(
                        state = state,
                        viewModel = viewModel,
                    )
                    MainTab.ADESOES_EXCLUIDAS -> AdesoesExcluidasScreen(
                        state = state,
                        viewModel = viewModel,
                    )
                    MainTab.PERFIL -> ProfileScreen(
                        state = state,
                        onLogout = viewModel::logout,
                    )
                }

                if (state.loading && state.profile == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .align(Alignment.TopCenter),
                shadowElevation = 2.dp,
                color = White,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopBarActionButton(onClick = { isDrawerOpen = true }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Abrir menu")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        VendaBrandIcon(
                            modifier = Modifier.size(30.dp),
                            showPlusBubble = false,
                        )
                        Text(
                            text = "Venda+",
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    TopBarActionButton(onClick = viewModel::logout) {
                        Icon(Icons.Outlined.Logout, contentDescription = "Sair")
                    }
                }
            }

            if (isDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f))
                        .clickable { isDrawerOpen = false },
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxSize()
                        .statusBarsPadding(),
                    color = White,
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TopBarActionButton(onClick = { isDrawerOpen = false }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Fechar menu")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VendaBrandIcon(
                                    modifier = Modifier.size(30.dp),
                                    showPlusBubble = false,
                                )
                                Text(
                                    text = "Venda+",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            TopBarActionButton(
                                onClick = {
                                    isDrawerOpen = false
                                    viewModel.logout()
                                },
                            ) {
                                Icon(Icons.Outlined.Logout, contentDescription = "Sair")
                            }
                        }

                        HorizontalDivider(color = Slate200)

                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = state.profile?.name.orEmpty(),
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = state.profile?.role.orEmpty(),
                                color = Slate500,
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            )
                        }

                        DrawerMenuItem(
                            label = "Dashboard",
                            selected = state.activeTab == MainTab.DASHBOARD,
                            icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
                            onClick = {
                                viewModel.selectTab(MainTab.DASHBOARD)
                                isDrawerOpen = false
                            },
                        )
                        DrawerMenuItem(
                            label = "Usuários",
                            selected = state.activeTab == MainTab.USERS,
                            icon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                            onClick = {
                                viewModel.selectTab(MainTab.USERS)
                                isDrawerOpen = false
                            },
                        )
                        DrawerMenuItem(
                            label = "Equipes",
                            selected = state.activeTab == MainTab.TEAMS,
                            icon = { Icon(Icons.Outlined.Workspaces, contentDescription = null) },
                            onClick = {
                                viewModel.selectTab(MainTab.TEAMS)
                                isDrawerOpen = false
                            },
                        )
                        DrawerMenuItem(
                            label = "Cadastro",
                            selected = state.activeTab == MainTab.CADASTROS,
                            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            onClick = {
                                viewModel.selectTab(MainTab.CADASTROS)
                                isDrawerOpen = false
                            },
                        )
                        DrawerMenuItem(
                            label = "Meu Perfil",
                            selected = state.activeTab == MainTab.PERFIL,
                            icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                            onClick = {
                                viewModel.selectTab(MainTab.PERFIL)
                                isDrawerOpen = false
                            },
                        )
                        DrawerMenuItem(
                            label = "Configurações",
                            selected = state.activeTab == MainTab.SETTINGS,
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = {
                                viewModel.selectTab(MainTab.SETTINGS)
                                isDrawerOpen = false
                            },
                        )
                        if (state.profile?.role in setOf("ADMINISTRADOR", "ADMIN")) {
                            DrawerMenuItem(
                                label = "Auditoria Lemmit",
                                selected = state.activeTab == MainTab.AUDITORIA_LEMMIT,
                                icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                onClick = {
                                    viewModel.selectTab(MainTab.AUDITORIA_LEMMIT)
                                    isDrawerOpen = false
                                },
                            )
                            DrawerMenuItem(
                                label = "Fila Upload ERP",
                                selected = state.activeTab == MainTab.FILA_UPLOAD_ERP,
                                icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                                onClick = {
                                    viewModel.selectTab(MainTab.FILA_UPLOAD_ERP)
                                    isDrawerOpen = false
                                },
                            )
                            DrawerMenuItem(
                                label = "Adesoes Excluidas",
                                selected = state.activeTab == MainTab.ADESOES_EXCLUIDAS,
                                icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                onClick = {
                                    viewModel.selectTab(MainTab.ADESOES_EXCLUIDAS)
                                    isDrawerOpen = false
                                },
                            )
                        }

                        DrawerMenuItem(
                            label = "Atualizar dados",
                            selected = false,
                            icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = {
                                viewModel.refresh()
                                isDrawerOpen = false
                            },
                        )
                    }
                }
            }
        }
    }

    state.errorMessage?.let { message ->
        val severity = resolveGlobalMessageSeverity(message)
        val (container, textColor) = globalMessageSeverityColors(severity)
        val title = when (severity) {
            GlobalMessageSeverity.SUCCESS -> "Sucesso"
            GlobalMessageSeverity.WARNING -> "Aviso"
            GlobalMessageSeverity.ALERT -> "Atenção"
            GlobalMessageSeverity.ERROR -> "Erro"
        }
        val titleColor = when (severity) {
            GlobalMessageSeverity.SUCCESS -> EmeraldDark
            GlobalMessageSeverity.ERROR -> Red500
            else -> BrandOrange
        }
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text(title, color = titleColor) },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = container,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = message,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("OK")
                }
            },
        )
    }

    state.noticeMessage?.let { message ->
        val severity = resolveGlobalMessageSeverity(message)
        val (container, textColor) = globalMessageSeverityColors(severity)
        val title = when (severity) {
            GlobalMessageSeverity.SUCCESS -> "Sucesso"
            GlobalMessageSeverity.WARNING -> "Aviso"
            GlobalMessageSeverity.ALERT -> "Atenção"
            GlobalMessageSeverity.ERROR -> "Erro"
        }
        val titleColor = when (severity) {
            GlobalMessageSeverity.SUCCESS -> EmeraldDark
            GlobalMessageSeverity.ERROR -> Red500
            else -> BrandOrange
        }
        AlertDialog(
            onDismissRequest = viewModel::clearNotice,
            title = { Text(title, color = titleColor) },
            text = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = container,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = message,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearNotice) {
                    Text("OK")
                }
            },
        )
    }

    state.pendingCadastroPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingCadastroPrompt,
            title = { Text("Cadastro pendente encontrado") },
            text = {
                Text(
                    buildString {
                        append("Ja existe um cadastro para este CPF")
                        prompt.empresaNome?.takeIf { it.isNotBlank() }?.let { append(" em $it") }
                        append(". Deseja continuar o rascunho existente?")
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingCadastroPrompt) {
                    Text("Cancelar")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::continuePendingCadastro) {
                    Text("Continuar")
                }
            },
        )
    }

    state.selectedCadastro?.let { cadastro ->
        if (cadastro.tipoCadastro == "cadastro") {
            CadastroEditorDialog(
                state = state,
                viewModel = viewModel,
                cadastro = cadastro,
                onDismiss = viewModel::closeCadastro,
            )
        } else if (cadastro.tipoCadastro == "inclusao_dependente" && cadastro.status == "incompleto") {
            InclusaoDependenteDialog(
                state = state,
                viewModel = viewModel,
                cadastro = cadastro,
                onDismiss = viewModel::closeCadastro,
                onSuccess = {
                    viewModel.selectTab(MainTab.CADASTROS)
                    viewModel.selectCadastroAreaTab(CadastroAreaTab.INCOMPLETOS)
                    viewModel.selectCadastroFiltro(CadastroFiltro.PENDENTES)
                },
            )
        } else {
            CadastroDetailDialog(
                cadastro = cadastro,
                sendingCadastro = state.sendingCadastro,
                onSendCadastro = null,
                onOpenWebApp = if (AppConfig.publicAppUrl.isBlank()) null else openWebApp,
                onDismiss = viewModel::closeCadastro,
            )
        }
    }

    CadastroOverlayDialogs(
        state = state,
        viewModel = viewModel,
    )
}

@Composable
private fun DrawerMenuItem(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldSoft else Slate100,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                color = if (selected) Emerald else Slate500,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private enum class GlobalMessageSeverity {
    WARNING,
    ALERT,
    ERROR,
    SUCCESS,
}

private fun resolveGlobalMessageSeverity(message: String): GlobalMessageSeverity {
    val normalized = message.lowercase(Locale.ROOT)
    return when {
        normalized.contains("sucesso") -> GlobalMessageSeverity.SUCCESS
        normalized.contains("falha") || normalized.contains("erro") || normalized.contains("invalid input syntax") -> GlobalMessageSeverity.ERROR
        normalized.contains("obrigatorio") || normalized.contains("invalido") || normalized.contains("selecione") -> GlobalMessageSeverity.ALERT
        else -> GlobalMessageSeverity.WARNING
    }
}

private fun globalMessageSeverityColors(severity: GlobalMessageSeverity): Pair<Color, Color> {
    return when (severity) {
        GlobalMessageSeverity.SUCCESS -> EmeraldSoft to EmeraldDark
        GlobalMessageSeverity.WARNING -> Amber100 to Amber500
        GlobalMessageSeverity.ALERT -> BrandOrange.copy(alpha = 0.18f) to BrandOrange
        GlobalMessageSeverity.ERROR -> Red100 to Red500
    }
}

@Composable
private fun TopBarActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Slate100,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
