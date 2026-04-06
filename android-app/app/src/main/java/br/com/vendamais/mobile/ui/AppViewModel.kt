package br.com.vendamais.mobile.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.vendamais.mobile.AppConfig
import br.com.vendamais.mobile.data.auth.SavedSession
import br.com.vendamais.mobile.data.auth.SessionStore
import br.com.vendamais.mobile.data.auth.SupabaseAuthService
import br.com.vendamais.mobile.data.models.AdminTeam
import br.com.vendamais.mobile.data.models.AdminUser
import br.com.vendamais.mobile.data.models.AuditLemmitResponse
import br.com.vendamais.mobile.data.models.CadastroConfig
import br.com.vendamais.mobile.data.models.CadastroDetalhe
import br.com.vendamais.mobile.data.models.CadastroExcluidoItem
import br.com.vendamais.mobile.data.models.CadastroLinkItem
import br.com.vendamais.mobile.data.models.CadastroResumo
import br.com.vendamais.mobile.data.models.CadastroStats
import br.com.vendamais.mobile.data.models.CpfConsultInput
import br.com.vendamais.mobile.data.models.ErpUploadQueueItem
import br.com.vendamais.mobile.data.models.EmpresaResumo
import br.com.vendamais.mobile.data.models.EmpresaSearchType
import br.com.vendamais.mobile.data.models.MobileProfile
import br.com.vendamais.mobile.data.models.MobileTeam
import br.com.vendamais.mobile.data.models.ParentescoMap
import br.com.vendamais.mobile.data.models.PlanoMap
import br.com.vendamais.mobile.data.models.ProcessUploadQueueResponse
import br.com.vendamais.mobile.data.models.PublicCadastroCheckCpfResponse
import br.com.vendamais.mobile.data.models.PublicCadastroLinkResolveResponse
import br.com.vendamais.mobile.data.models.PublicCadastroPayload
import br.com.vendamais.mobile.data.models.PublicCadastroSubmitResponse
import br.com.vendamais.mobile.data.models.ResetStuckQueueResult
import br.com.vendamais.mobile.data.models.StatusAdesao
import br.com.vendamais.mobile.data.models.SystemOverview
import br.com.vendamais.mobile.data.models.TeamMemberOption
import br.com.vendamais.mobile.data.models.VendedorStats
import br.com.vendamais.mobile.domain.cadastro.CadastroApiErrorMapper
import br.com.vendamais.mobile.domain.cadastro.CadastroErpError
import br.com.vendamais.mobile.domain.cadastro.CadastroModalSignal
import br.com.vendamais.mobile.domain.cadastro.CadastroModalStateMachine
import br.com.vendamais.mobile.domain.cadastro.CadastroOverlayIntent
import br.com.vendamais.mobile.data.remote.CadastroPayloadBuilder
import br.com.vendamais.mobile.data.remote.CadastroExistenteException
import br.com.vendamais.mobile.data.remote.CadastroWorkflowRepository
import br.com.vendamais.mobile.data.remote.InclusaoBuscaTipo
import br.com.vendamais.mobile.data.remote.ResponsavelFinanceiroResumo
import br.com.vendamais.mobile.data.remote.SupabaseRepository
import br.com.vendamais.mobile.data.remote.UploadedTempFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class MainTab {
    DASHBOARD,
    CADASTROS,
    USERS,
    TEAMS,
    SETTINGS,
    AUDITORIA_LEMMIT,
    FILA_UPLOAD_ERP,
    ADESOES_EXCLUIDAS,
    PERFIL,
}

enum class CadastroAreaTab {
    NOVO,
    LINK,
    DEPENDENTE,
    INCOMPLETOS,
    COMPLETOS,
}

enum class CadastroFiltro {
    PENDENTES,
    ENVIADOS,
}

enum class DashboardMetricType {
    TOTAL,
    PENDENTES,
    CADASTRADOS,
}

data class DashboardDrilldown(
    val title: String,
    val metricType: DashboardMetricType,
    val items: List<VendedorStats>,
)

data class PendingCadastroPrompt(
    val cadastroId: String,
    val cpf: String,
    val empresaNome: String? = null,
)

data class CadastroWorkspaceState(
    val config: CadastroConfig? = null,
    val empresaSearchType: EmpresaSearchType = EmpresaSearchType.CODIGO,
    val empresaSearchValue: String = "",
    val empresaSearchResults: List<EmpresaResumo> = emptyList(),
    val selectedEmpresa: EmpresaResumo? = null,
    val selectedVendedorId: String = "",
    val selectedAdesionistaId: String = "",
    val cpfValue: String = "",
    val operationLoading: Boolean = false,
)

data class LinkWorkspaceState(
    val empresaSearchType: EmpresaSearchType = EmpresaSearchType.CODIGO,
    val empresaSearchValue: String = "",
    val empresaSearchResults: List<EmpresaResumo> = emptyList(),
    val selectedEmpresa: EmpresaResumo? = null,
    val operationLoading: Boolean = false,
    val links: List<CadastroLinkItem> = emptyList(),
)

data class AppUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val configurationMissing: Boolean = !AppConfig.isConfigured(),
    val profile: MobileProfile? = null,
    val team: MobileTeam? = null,
    val systemOverview: SystemOverview = SystemOverview(),
    val cadastroStats: CadastroStats = CadastroStats(),
    val cadastros: List<CadastroResumo> = emptyList(),
    val cadastrosLoading: Boolean = false,
    val cadastrosLoaded: Boolean = false,
    val cadastroSupportLoading: Boolean = false,
    val cadastroSupportLoaded: Boolean = false,
    val selectedCadastro: CadastroDetalhe? = null,
    val detailLoading: Boolean = false,
    val dashboardDrilldown: DashboardDrilldown? = null,
    val dashboardDrilldownLoading: Boolean = false,
    val vendedores: List<TeamMemberOption> = emptyList(),
    val adesionistas: List<TeamMemberOption> = emptyList(),
    val planosMap: List<PlanoMap> = emptyList(),
    val parentescosMap: List<ParentescoMap> = emptyList(),
    val statusAdesoes: List<StatusAdesao> = emptyList(),
    val cadastroWorkspace: CadastroWorkspaceState = CadastroWorkspaceState(),
    val linkWorkspace: LinkWorkspaceState = LinkWorkspaceState(),
    val sendingCadastro: Boolean = false,
    val adminUsers: List<AdminUser> = emptyList(),
    val adminTeams: List<AdminTeam> = emptyList(),
    val adminLoading: Boolean = false,
    val adminLoaded: Boolean = false,
    val auditLemmit: AuditLemmitResponse = AuditLemmitResponse(),
    val uploadQueue: List<ErpUploadQueueItem> = emptyList(),
    val uploadQueueOperation: ProcessUploadQueueResponse? = null,
    val resetQueueResult: ResetStuckQueueResult? = null,
    val cadastrosExcluidos: List<CadastroExcluidoItem> = emptyList(),
    val adminFeatureLoading: Boolean = false,
    val publicToken: String? = null,
    val cadastroOverlay: CadastroOverlayIntent? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val pendingCadastroPrompt: PendingCadastroPrompt? = null,
    val activeTab: MainTab = MainTab.DASHBOARD,
    val cadastroTab: CadastroAreaTab = CadastroAreaTab.NOVO,
    val cadastroFiltro: CadastroFiltro = CadastroFiltro.PENDENTES,
)

class AppViewModel(
    private val sessionStore: SessionStore,
    private val authService: SupabaseAuthService,
    private val repository: SupabaseRepository,
    private val workflowRepository: CadastroWorkflowRepository,
) : ViewModel() {
    private val logTag = "VendaMaisApp"
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var currentSession: SavedSession? = null

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collectLatest { session ->
                currentSession = session
                if (session == null) {
                    _uiState.update {
                        AppUiState(
                            email = it.email,
                            password = "",
                            loading = false,
                            configurationMissing = !AppConfig.isConfigured(),
                        )
                    }
                } else {
                    bootstrapSession(session)
                }
            }
        }
    }

    fun updateEmail(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null, noticeMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null, noticeMessage = null) }
    }

    fun updateEmpresaSearchType(type: EmpresaSearchType) {
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(
                    empresaSearchType = type,
                    empresaSearchValue = "",
                    empresaSearchResults = emptyList(),
                ),
            )
        }
    }

    fun updateEmpresaSearchValue(value: String) {
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(
                    empresaSearchValue = value,
                    empresaSearchResults = emptyList(),
                ),
            )
        }
    }

    fun updateCpfValue(value: String) {
        val digits = value.filter(Char::isDigit).take(11)
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(cpfValue = digits),
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    fun updateLinkSearchType(type: EmpresaSearchType) {
        _uiState.update {
            it.copy(
                linkWorkspace = it.linkWorkspace.copy(
                    empresaSearchType = type,
                    empresaSearchValue = "",
                    empresaSearchResults = emptyList(),
                ),
            )
        }
    }

    fun updateLinkSearchValue(value: String) {
        _uiState.update {
            it.copy(
                linkWorkspace = it.linkWorkspace.copy(
                    empresaSearchValue = value,
                    empresaSearchResults = emptyList(),
                ),
            )
        }
    }

    fun selectLinkEmpresa(empresa: EmpresaResumo) {
        _uiState.update {
            it.copy(
                linkWorkspace = it.linkWorkspace.copy(
                    selectedEmpresa = empresa,
                    empresaSearchResults = emptyList(),
                    empresaSearchValue = "",
                ),
            )
        }
    }

    fun clearLinkEmpresa() {
        _uiState.update {
            it.copy(
                linkWorkspace = it.linkWorkspace.copy(
                    selectedEmpresa = null,
                    empresaSearchResults = emptyList(),
                    empresaSearchValue = "",
                ),
            )
        }
    }

    fun updateSelectedVendedor(value: String) {
        _uiState.update {
            it.copy(cadastroWorkspace = it.cadastroWorkspace.copy(selectedVendedorId = value))
        }
    }

    fun updateSelectedAdesionista(value: String) {
        _uiState.update {
            it.copy(cadastroWorkspace = it.cadastroWorkspace.copy(selectedAdesionistaId = value))
        }
    }

    fun selectEmpresa(empresa: EmpresaResumo) {
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(
                    selectedEmpresa = empresa,
                    empresaSearchResults = emptyList(),
                    empresaSearchValue = "",
                ),
            )
        }

        empresa.observacoesResolvidas
            ?.takeIf { it.isNotBlank() }
            ?.let { observacoes ->
                resolveCadastroOverlay(
                    CadastroModalSignal(
                        empresaObservacaoNome = empresa.nomeFantasia.ifBlank {
                            empresa.razaoSocial.ifBlank { "Empresa sem nome" }
                        },
                        empresaObservacaoTexto = observacoes,
                    ),
                )
            }
    }

    fun clearSelectedEmpresa() {
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(
                    selectedEmpresa = null,
                    empresaSearchResults = emptyList(),
                    empresaSearchValue = "",
                ),
            )
        }
    }

    fun login() {
        val current = _uiState.value
        if (current.configurationMissing) {
            _uiState.update { it.copy(errorMessage = "Configure o Supabase em android-app/local.properties.") }
            return
        }
        if (current.email.isBlank() || current.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe email e senha.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            runCatching { authService.login(current.email.trim(), current.password) }
                .onSuccess { session ->
                    sessionStore.save(session)
                    _uiState.update { it.copy(password = "") }
                }
                .onFailure { throwable ->
                    Log.e(logTag, "Falha no login", throwable)
                    _uiState.update {
                        it.copy(
                            loading = false,
                            errorMessage = throwable.message ?: "Erro inesperado ao autenticar.",
                        )
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch { sessionStore.clear() }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab, errorMessage = null, noticeMessage = null) }
        when (tab) {
            MainTab.CADASTROS -> ensureCadastroResourcesLoaded()
            MainTab.USERS,
            MainTab.TEAMS,
            MainTab.SETTINGS,
            -> ensureAdminResourcesLoaded()
            MainTab.AUDITORIA_LEMMIT -> loadAuditLemmit()
            MainTab.FILA_UPLOAD_ERP -> loadUploadQueue()
            MainTab.ADESOES_EXCLUIDAS -> loadCadastrosExcluidos()
            else -> Unit
        }
    }

    fun selectCadastroFiltro(filtro: CadastroFiltro) {
        _uiState.update { it.copy(cadastroFiltro = filtro) }
    }

    fun selectCadastroAreaTab(tab: CadastroAreaTab) {
        _uiState.update {
            it.copy(
                cadastroTab = tab,
                cadastroFiltro = when (tab) {
                    CadastroAreaTab.INCOMPLETOS -> CadastroFiltro.PENDENTES
                    CadastroAreaTab.COMPLETOS -> CadastroFiltro.ENVIADOS
                    else -> it.cadastroFiltro
                },
            )
        }

        if (tab in setOf(CadastroAreaTab.INCOMPLETOS, CadastroAreaTab.COMPLETOS)) {
            ensureCadastroResourcesLoaded()
        }
    }

    fun refresh() {
        currentSession?.let { session ->
            viewModelScope.launch { refreshAll(session) }
        }
    }

    fun searchEmpresas() {
        val session = currentSession ?: return
        val state = _uiState.value
        val query = state.cadastroWorkspace.empresaSearchValue.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Digite um valor para buscar a empresa.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cadastroWorkspace = it.cadastroWorkspace.copy(operationLoading = true),
                    errorMessage = null,
                    noticeMessage = null,
                )
            }

            runCatching {
                val activeSession = ensureFreshSession(session)
                workflowRepository.searchEmpresa(
                    session = activeSession,
                    value = query,
                    type = state.cadastroWorkspace.empresaSearchType,
                )
            }.onSuccess { empresas ->
                _uiState.update {
                    it.copy(
                        cadastroWorkspace = it.cadastroWorkspace.copy(
                            operationLoading = false,
                            empresaSearchResults = empresas,
                        ),
                        errorMessage = if (empresas.isEmpty()) "Nenhuma empresa encontrada." else null,
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao buscar empresas", throwable)
                _uiState.update {
                    it.copy(
                        cadastroWorkspace = it.cadastroWorkspace.copy(operationLoading = false),
                        errorMessage = throwable.message ?: "Falha ao buscar empresa.",
                    )
                }
            }
        }
    }

    fun searchEmpresasForLink() {
        val session = currentSession ?: return
        val state = _uiState.value
        val query = state.linkWorkspace.empresaSearchValue.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Digite um valor para buscar a empresa do link.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    linkWorkspace = it.linkWorkspace.copy(operationLoading = true),
                    errorMessage = null,
                )
            }

            runCatching {
                val activeSession = ensureFreshSession(session)
                workflowRepository.searchEmpresa(
                    session = activeSession,
                    value = query,
                    type = state.linkWorkspace.empresaSearchType,
                )
            }.onSuccess { empresas ->
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(
                            operationLoading = false,
                            empresaSearchResults = empresas,
                        ),
                        errorMessage = if (empresas.isEmpty()) "Nenhuma empresa encontrada para o link." else null,
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao buscar empresas para link", throwable)
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(operationLoading = false),
                        errorMessage = throwable.message ?: "Falha ao buscar empresa para link.",
                    )
                }
            }
        }
    }

    fun createCadastroLink() {
        val session = currentSession ?: return
        val profile = _uiState.value.profile ?: return
        val empresa = _uiState.value.linkWorkspace.selectedEmpresa
        if (empresa == null) {
            _uiState.update { it.copy(errorMessage = "Selecione uma empresa antes de gerar o link.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    linkWorkspace = it.linkWorkspace.copy(operationLoading = true),
                    errorMessage = null,
                )
            }

            runCatching {
                val activeSession = ensureFreshSession(session)
                workflowRepository.createCadastroLink(activeSession, profile, empresa)
                workflowRepository.fetchActiveLinks(activeSession)
            }.onSuccess { links ->
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(
                            operationLoading = false,
                            links = links,
                            selectedEmpresa = null,
                            empresaSearchResults = emptyList(),
                        ),
                        errorMessage = "Link gerado com sucesso.",
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao gerar link", throwable)
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(operationLoading = false),
                        errorMessage = throwable.message ?: "Falha ao gerar link.",
                    )
                }
            }
        }
    }

    fun regenerateCadastroLink(linkId: String) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    linkWorkspace = it.linkWorkspace.copy(operationLoading = true),
                    errorMessage = null,
                )
            }
            runCatching {
                val activeSession = ensureFreshSession(session)
                workflowRepository.regenerateCadastroLink(activeSession, linkId)
                workflowRepository.fetchActiveLinks(activeSession)
            }.onSuccess { links ->
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(
                            operationLoading = false,
                            links = links,
                        ),
                        errorMessage = "Link regerado com sucesso.",
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao regerar link", throwable)
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(operationLoading = false),
                        errorMessage = throwable.message ?: "Falha ao regerar link.",
                    )
                }
            }
        }
    }

    fun deleteCadastroLink(linkId: String) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    linkWorkspace = it.linkWorkspace.copy(operationLoading = true),
                    errorMessage = null,
                )
            }
            runCatching {
                val activeSession = ensureFreshSession(session)
                workflowRepository.deleteCadastroLink(activeSession, linkId)
                workflowRepository.fetchActiveLinks(activeSession)
            }.onSuccess { links ->
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(
                            operationLoading = false,
                            links = links,
                        ),
                        errorMessage = "Link excluido com sucesso.",
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao excluir link", throwable)
                _uiState.update {
                    it.copy(
                        linkWorkspace = it.linkWorkspace.copy(operationLoading = false),
                        errorMessage = throwable.message ?: "Falha ao excluir link.",
                    )
                }
            }
        }
    }

    fun createDraftFromCpf() {
        val session = currentSession ?: return
        val state = _uiState.value
        val profile = state.profile ?: return
        val workspace = state.cadastroWorkspace
        val empresa = workspace.selectedEmpresa
        if (empresa == null) {
            _uiState.update { it.copy(errorMessage = "Selecione uma empresa antes de consultar o CPF.") }
            return
        }

        val invalidCodes = workspace.config?.codigosEmpresaInvalidos.orEmpty()
        val situacaoCode = empresa.codigoSituacao?.toString()
        if (!situacaoCode.isNullOrBlank() && invalidCodes.contains(situacaoCode)) {
            resolveCadastroOverlay(
                CadastroModalSignal(
                    empresaCanceladaNome = empresa.nomeFantasia.ifBlank {
                        empresa.razaoSocial.ifBlank { "Empresa sem nome" }
                    },
                ),
            )
            return
        }

        val cpf = CadastroPayloadBuilder.normalizeDigits(workspace.cpfValue)
        if (!CadastroPayloadBuilder.validateCpf(cpf)) {
            _uiState.update { it.copy(errorMessage = "CPF invÃ¡lido. Verifique os dÃ­gitos.") }
            return
        }

        val vendedorSelecionado = state.vendedores.firstOrNull { it.id == workspace.selectedVendedorId }
        if (requiresVendedorSelection(profile) && profile.role != "VENDEDOR" && state.vendedores.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = "Nenhum vendedor disponível. Entre em contato com o administrador.")
            }
            return
        }
        val vendedorVinculado = when (profile.role) {
            "VENDEDOR" -> !profile.externalId.isNullOrBlank()
            else -> !vendedorSelecionado?.externalId.isNullOrBlank()
        }
        if (requiresVendedorSelection(profile) && !vendedorVinculado) {
            val message = if (profile.role == "VENDEDOR") {
                "Seu usuário não possui código de vendedor. Contate o administrador."
            } else {
                "Selecione um vendedor antes de consultar."
            }
            _uiState.update { it.copy(errorMessage = message) }
            return
        }

        val adesionistaSelecionado = state.adesionistas.firstOrNull { it.id == workspace.selectedAdesionistaId }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    cadastroWorkspace = it.cadastroWorkspace.copy(operationLoading = true),
                    errorMessage = null,
                    pendingCadastroPrompt = null,
                )
            }

            runCatching {
                val activeSession = ensureFreshSession(session)
                withContext(Dispatchers.IO) {
                    workflowRepository.createDraftFromCpf(
                        session = activeSession,
                        profile = profile,
                        config = state.cadastroWorkspace.config,
                        input = CpfConsultInput(
                            cpf = cpf,
                            empresa = empresa,
                            vendedorSelecionado = vendedorSelecionado,
                            adesionistaSelecionado = adesionistaSelecionado,
                        ),
                    )
                }
            }.onSuccess { result ->
                val activeSession = ensureFreshSession(session)
                val currentCadastros = _uiState.value.cadastros
                val cadastrosRefreshResult = runCatching {
                    withContext(Dispatchers.IO) { repository.fetchCadastros(activeSession) }
                }
                val cadastrosAtualizados = cadastrosRefreshResult.getOrElse {
                    Log.w(logTag, "Rascunho criado, mas falhou o refresh da lista", it)
                    currentCadastros
                }
                val postSuccessNotice = buildList {
                    result.warningMessage?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (cadastrosRefreshResult.isFailure) {
                        add("Rascunho criado, mas houve falha ao atualizar a lista de cadastros.")
                    }
                }.joinToString("\n\n").ifBlank { null }
                _uiState.update {
                    it.copy(
                        cadastros = cadastrosAtualizados,
                        selectedCadastro = result.draft,
                        cadastroWorkspace = it.cadastroWorkspace.copy(
                            operationLoading = false,
                            cpfValue = "",
                            empresaSearchResults = emptyList(),
                        ),
                        errorMessage = null,
                        noticeMessage = postSuccessNotice,
                        pendingCadastroPrompt = null,
                    )
                }
                if (result.warningMessage?.contains("Limite mensal da Lemmit atingido", ignoreCase = true) == true) {
                    resolveCadastroOverlay(
                        CadastroModalSignal(
                            lemmitLimit = CadastroOverlayIntent.LemmitLimit(),
                        ),
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao consultar CPF e criar rascunho", throwable)
                if (throwable is CadastroExistenteException && throwable.canContinue && !throwable.cadastroId.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            cadastroWorkspace = it.cadastroWorkspace.copy(operationLoading = false),
                            errorMessage = null,
                            pendingCadastroPrompt = PendingCadastroPrompt(
                                cadastroId = throwable.cadastroId,
                                cpf = cpf,
                                empresaNome = throwable.empresaNome,
                            ),
                        )
                    }
                } else if (throwable is CadastroExistenteException && !throwable.canContinue) {
                    resolveCadastroOverlay(
                        CadastroModalSignal(
                            alreadyExistsCpf = cpf,
                            alreadyExistsSummary = throwable.message.orEmpty().ifBlank {
                                "Ja existe cadastro para este CPF."
                            },
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            cadastroWorkspace = it.cadastroWorkspace.copy(operationLoading = false),
                            pendingCadastroPrompt = null,
                            errorMessage = null,
                        )
                    }
                } else {
                    val erpError = CadastroApiErrorMapper.mapErpError(throwable.message)
                    if (erpError != null) {
                        resolveCadastroOverlay(CadastroModalSignal(erpError = erpError))
                    }
                    _uiState.update {
                        it.copy(
                            cadastroWorkspace = it.cadastroWorkspace.copy(operationLoading = false),
                            pendingCadastroPrompt = null,
                            errorMessage = mapCadastroFlowErrorMessage(
                                throwable.message,
                                "Falha ao criar rascunho.",
                            ),
                        )
                    }
                }
            }
        }
    }

    fun openCadastro(id: String) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                workflowRepository.fetchCadastroDetalhe(activeSession, id)
            }.onSuccess { detalhe ->
                _uiState.update {
                    it.copy(detailLoading = false, selectedCadastro = detalhe)
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao carregar detalhe do cadastro", throwable)
                _uiState.update {
                    it.copy(
                        detailLoading = false,
                        errorMessage = throwable.message ?: "Falha ao carregar cadastro.",
                    )
                }
            }
        }
    }

    fun sendSelectedCadastro(
        cadastroSnapshot: CadastroDetalhe? = null,
        payloadHint: JsonObject? = null,
    ) {
        val session = currentSession
        if (session == null) {
            _uiState.update { it.copy(errorMessage = "Sua sessão expirou. Faça login novamente para continuar.") }
            return
        }
        val profile = _uiState.value.profile
        if (profile == null) {
            _uiState.update { it.copy(errorMessage = "Sua sessão expirou. Faça login novamente para continuar.") }
            return
        }
        val cadastro = cadastroSnapshot ?: _uiState.value.selectedCadastro ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(sendingCadastro = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                val nomeFromPayload = payloadHint
                    ?.get("nome")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val dataNascimentoFromPayload = payloadHint
                    ?.get("data_nascimento")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val nomeMaeFromPayload = payloadHint
                    ?.get("nome_mae")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val contatosFromPayload = payloadHint?.get("contatos")
                val enderecoFromPayload = payloadHint?.get("endereco")
                val dependentesFromPayload = payloadHint?.get("dependentes")
                val titularPlanoFromPayload = runCatching {
                    dependentesFromPayload
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("plano")
                        ?.jsonPrimitive
                        ?.intOrNull
                }.getOrNull()
                val empresaIdFromPayload = payloadHint
                    ?.get("empresa_id")
                    ?.jsonPrimitive
                    ?.intOrNull
                val empresaCodigoFromPayload = payloadHint
                    ?.get("empresa_codigo")
                    ?.jsonPrimitive
                    ?.intOrNull
                val empresaNomeFromPayload = payloadHint
                    ?.get("empresa_nome")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val empresaCnpjFromPayload = payloadHint
                    ?.get("empresa_cnpj")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val empresaRawFromPayload = payloadHint?.get("empresa_raw")
                val planosRawFromPayload = payloadHint?.get("planos_raw")
                val arquivoPathFromPayload = payloadHint
                    ?.get("arquivo_path")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                Log.i(
                    logTag,
                    "sendSelectedCadastro preflight id=${cadastro.id} arquivoPathPayload=${!arquivoPathFromPayload.isNullOrBlank()} arquivoPathSnapshot=${!cadastro.arquivoPath.isNullOrBlank()} titularPlanoPayload=${titularPlanoFromPayload ?: 0}",
                )

                val cadastroBase = workflowRepository.updateCadastro(
                    session = activeSession,
                    id = cadastro.id,
                    payload = buildJsonObject {
                        put("created_by", profile.id)
                        profile.teamId?.takeIf { it.isNotBlank() }?.let { put("team_id", it) }
                        nomeFromPayload
                            ?.let { put("nome", it) }
                            ?: cadastro.nome
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("nome", it) }
                        dataNascimentoFromPayload
                            ?.let { put("data_nascimento", it) }
                            ?: cadastro.dataNascimento
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("data_nascimento", it) }
                        nomeMaeFromPayload
                            ?.let { put("nome_mae", it) }
                            ?: cadastro.nomeMae
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("nome_mae", it) }
                        contatosFromPayload?.let { put("contatos", it) } ?: cadastro.contatos?.let { put("contatos", it) }
                        enderecoFromPayload?.let { put("endereco", it) } ?: cadastro.endereco?.let { put("endereco", it) }
                        dependentesFromPayload?.let { put("dependentes", it) } ?: cadastro.dependentes?.let { put("dependentes", it) }
                        empresaIdFromPayload?.let { put("empresa_id", it) } ?: cadastro.empresaId?.let { put("empresa_id", it) }
                        empresaCodigoFromPayload?.let { put("empresa_codigo", it) } ?: cadastro.empresaCodigo?.let { put("empresa_codigo", it) }
                        empresaNomeFromPayload
                            ?.let { put("empresa_nome", it) }
                            ?: cadastro.empresaNome
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("empresa_nome", it) }
                        empresaCnpjFromPayload
                            ?.let { put("empresa_cnpj", it) }
                            ?: cadastro.empresaCnpj
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("empresa_cnpj", it) }
                        empresaRawFromPayload?.let { put("empresa_raw", it) } ?: cadastro.empresaRaw?.let { put("empresa_raw", it) }
                        planosRawFromPayload?.let { put("planos_raw", it) } ?: cadastro.planosRaw?.let { put("planos_raw", it) }
                        arquivoPathFromPayload?.let { put("arquivo_path", it) } ?: cadastro.arquivoPath
                            ?.takeIf { it.isNotBlank() }
                            ?.let { put("arquivo_path", it) }
                    },
                )
                Log.i(
                    logTag,
                    "sendSelectedCadastro afterUpdate id=${cadastro.id} arquivoPathPersisted=${!cadastroBase.arquivoPath.isNullOrBlank()}",
                )
                val cadastroComEmpresa = ensureCadastroEmpresaBeforeSend(
                    session = activeSession,
                    cadastroId = cadastro.id,
                    fallbackEmpresa = _uiState.value.cadastroWorkspace.selectedEmpresa,
                    cachedCadastro = cadastroBase,
                )
                val detalheAtualizado = workflowRepository.sendCadastroToErp(
                    session = activeSession,
                    profile = profile,
                    config = _uiState.value.cadastroWorkspace.config,
                    cadastroId = cadastro.id,
                    cadastroPrefetched = cadastroComEmpresa,
                    arquivoPathHint = arquivoPathFromPayload ?: cadastroComEmpresa.arquivoPath,
                    dependentesHint = dependentesFromPayload ?: cadastroComEmpresa.dependentes,
                    nomeHint = nomeFromPayload ?: cadastroComEmpresa.nome,
                    dataNascimentoHint = dataNascimentoFromPayload ?: cadastroComEmpresa.dataNascimento,
                    nomeMaeHint = nomeMaeFromPayload ?: cadastroComEmpresa.nomeMae,
                )
                val cadastrosAtualizados = repository.fetchCadastros(activeSession)
                val statsAtualizadas = repository.fetchCadastroStats(activeSession)
                Triple(detalheAtualizado, cadastrosAtualizados, statsAtualizadas)
            }.onSuccess { (detalhe, cadastrosAtualizados, statsAtualizadas) ->
                _uiState.update {
                    it.copy(
                        sendingCadastro = false,
                        selectedCadastro = null,
                        cadastros = cadastrosAtualizados,
                        cadastroStats = statsAtualizadas,
                        errorMessage = null,
                        noticeMessage = "Cadastro enviado com sucesso ao ERP.",
                        cadastroOverlay = null,
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao enviar cadastro ao ERP", throwable)
                val erpError = CadastroApiErrorMapper.mapErpError(throwable.message)
                if (erpError != null) {
                    resolveCadastroOverlay(CadastroModalSignal(erpError = erpError))
                }
                _uiState.update {
                    it.copy(
                        sendingCadastro = false,
                        errorMessage = mapCadastroFlowErrorMessage(
                            throwable.message,
                            "Falha ao enviar cadastro.",
                        ),
                    )
                }
            }
        }
    }

    fun retrySendSelectedCadastroWithVendedor(
        vendedorCodigo: String,
        vendedorNome: String,
    ) {
        val session = currentSession
        if (session == null) {
            _uiState.update { it.copy(errorMessage = "Sua sessão expirou. Faça login novamente para continuar.") }
            return
        }
        val profile = _uiState.value.profile
        if (profile == null) {
            _uiState.update { it.copy(errorMessage = "Sua sessão expirou. Faça login novamente para continuar.") }
            return
        }
        val cadastro = _uiState.value.selectedCadastro ?: return
        if (vendedorCodigo.isBlank() || vendedorNome.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe codigo e nome do vendedor para reenviar.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(sendingCadastro = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                val cadastroComVendedor = workflowRepository.updateCadastro(
                    session = activeSession,
                    id = cadastro.id,
                    payload = buildJsonObject {
                        put("vendedor_codigo", vendedorCodigo)
                        put("vendedor_nome", vendedorNome)
                    },
                )
                val cadastroComEmpresa = ensureCadastroEmpresaBeforeSend(
                    session = activeSession,
                    cadastroId = cadastro.id,
                    fallbackEmpresa = _uiState.value.cadastroWorkspace.selectedEmpresa,
                    cachedCadastro = cadastroComVendedor,
                )
                val detalheAtualizado = workflowRepository.sendCadastroToErp(
                    session = activeSession,
                    profile = profile,
                    config = _uiState.value.cadastroWorkspace.config,
                    cadastroId = cadastro.id,
                    cadastroPrefetched = cadastroComEmpresa,
                    arquivoPathHint = cadastroComEmpresa.arquivoPath,
                    dependentesHint = cadastroComEmpresa.dependentes,
                    nomeHint = cadastroComEmpresa.nome,
                    dataNascimentoHint = cadastroComEmpresa.dataNascimento,
                    nomeMaeHint = cadastroComEmpresa.nomeMae,
                )
                val cadastrosAtualizados = repository.fetchCadastros(activeSession)
                val statsAtualizadas = repository.fetchCadastroStats(activeSession)
                Triple(detalheAtualizado, cadastrosAtualizados, statsAtualizadas)
            }.onSuccess { (detalhe, cadastrosAtualizados, statsAtualizadas) ->
                dismissCadastroOverlay()
                _uiState.update {
                    it.copy(
                        sendingCadastro = false,
                        selectedCadastro = detalhe,
                        cadastros = cadastrosAtualizados,
                        cadastroStats = statsAtualizadas,
                        noticeMessage = "Cadastro reenviado com sucesso.",
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao reenviar cadastro com vendedor ajustado", throwable)
                val erpError = CadastroApiErrorMapper.mapErpError(throwable.message)
                if (erpError != null) {
                    resolveCadastroOverlay(CadastroModalSignal(erpError = erpError))
                }
                _uiState.update {
                    it.copy(
                        sendingCadastro = false,
                        errorMessage = mapCadastroFlowErrorMessage(
                            throwable.message,
                            "Falha ao reenviar cadastro.",
                        ),
                    )
                }
            }
        }
    }

    fun deleteCadastroByOverlay(cadastroId: String, motivoExclusao: String) {
        if (cadastroId.isBlank()) return
        viewModelScope.launch {
            runCatching { deleteCadastroRecord(cadastroId, motivoExclusao) }
                .onSuccess {
                    dismissCadastroOverlay()
                    _uiState.update { it.copy(noticeMessage = "Cadastro excluido com sucesso.") }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message ?: "Falha ao excluir cadastro.") }
                }
        }
    }

    fun closeCadastro() {
        _uiState.update { it.copy(selectedCadastro = null, detailLoading = false, sendingCadastro = false) }
    }

    fun openDashboardDrilldown(tipoCadastro: String, metricType: DashboardMetricType) {
        val session = currentSession ?: return
        val profile = _uiState.value.profile ?: return
        if (profile.role !in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR", "SUPERVISOR")) return

        val titlePrefix = when (metricType) {
            DashboardMetricType.TOTAL -> "Total"
            DashboardMetricType.PENDENTES -> "Pendentes"
            DashboardMetricType.CADASTRADOS -> "Enviados"
        }
        val titleSuffix = if (tipoCadastro == "cadastro") "Cadastro" else "InclusÃ£o de Dependente"

        viewModelScope.launch {
            _uiState.update { it.copy(dashboardDrilldownLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.fetchStatsByVendedor(activeSession, tipoCadastro)
            }.onSuccess { stats ->
                _uiState.update {
                    it.copy(
                        dashboardDrilldownLoading = false,
                        dashboardDrilldown = DashboardDrilldown(
                            title = "$titlePrefix por vendedor - $titleSuffix",
                            metricType = metricType,
                            items = stats,
                        ),
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao carregar drilldown do dashboard", throwable)
                _uiState.update {
                    it.copy(
                        dashboardDrilldownLoading = false,
                        errorMessage = throwable.message ?: "Falha ao carregar detalhamento.",
                    )
                }
            }
        }
    }

    fun closeDashboardDrilldown() {
        _uiState.update { it.copy(dashboardDrilldown = null, dashboardDrilldownLoading = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun dismissPendingCadastroPrompt() {
        _uiState.update { it.copy(pendingCadastroPrompt = null) }
    }

    fun continuePendingCadastro() {
        val prompt = _uiState.value.pendingCadastroPrompt ?: return
        dismissPendingCadastroPrompt()
        openCadastro(prompt.cadastroId)
    }

    fun openPublicTokenFlow(token: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) return
        _uiState.update {
            it.copy(
                publicToken = normalized,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    fun closePublicTokenFlow() {
        _uiState.update { it.copy(publicToken = null, errorMessage = null, noticeMessage = null) }
    }

    fun resolveCadastroOverlay(signal: CadastroModalSignal) {
        val overlay = CadastroModalStateMachine.resolve(signal)
        _uiState.update { it.copy(cadastroOverlay = overlay) }
    }

    fun dismissCadastroOverlay() {
        _uiState.update { it.copy(cadastroOverlay = null) }
    }

    fun loadAuditLemmit(
        startIso: String? = null,
        endIso: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                val periodStart = startIso ?: java.time.LocalDate.now()
                    .withDayOfMonth(1)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toString()
                val periodEnd = endIso ?: java.time.Instant.now().toString()
                repository.fetchAuditLemmit(
                    session = activeSession,
                    startIso = periodStart,
                    endIso = periodEnd,
                    limit = limit,
                    offset = offset,
                )
            }.onSuccess { audit ->
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        auditLemmit = audit,
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao carregar auditoria lemmit", throwable)
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        errorMessage = throwable.message ?: "Falha ao carregar auditoria Lemmit.",
                    )
                }
            }
        }
    }

    fun loadUploadQueue(statuses: List<String> = emptyList()) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.fetchErpUploadQueue(activeSession, statuses = statuses)
            }.onSuccess { queue ->
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        uploadQueue = queue,
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao carregar fila upload ERP", throwable)
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        errorMessage = throwable.message ?: "Falha ao carregar fila de upload ERP.",
                    )
                }
            }
        }
    }

    fun processUploadQueue() {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.processUploadQueue(activeSession)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        uploadQueueOperation = result,
                        noticeMessage = result.message ?: "Processamento da fila iniciado.",
                    )
                }
                loadUploadQueue()
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao iniciar processamento de fila", throwable)
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        errorMessage = throwable.message ?: "Falha ao processar fila de upload ERP.",
                    )
                }
            }
        }
    }

    fun resetStuckQueue(minutes: Int = 15) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.resetStuckQueue(activeSession, minutes)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        resetQueueResult = result,
                        noticeMessage = if (result.resetCount > 0) {
                            "${result.resetCount} item(ns) travado(s) foram resetados."
                        } else {
                            "Nenhum item travado encontrado."
                        },
                    )
                }
                loadUploadQueue()
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao resetar itens travados da fila", throwable)
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        errorMessage = throwable.message ?: "Falha ao resetar itens travados da fila.",
                    )
                }
            }
        }
    }

    fun loadCadastrosExcluidos(limit: Int = 100) {
        val session = currentSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminFeatureLoading = true, errorMessage = null) }
            runCatching {
                val activeSession = ensureFreshSession(session)
                repository.fetchCadastrosExcluidos(activeSession, limit)
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        cadastrosExcluidos = items,
                    )
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Falha ao carregar adesoes excluidas", throwable)
                _uiState.update {
                    it.copy(
                        adminFeatureLoading = false,
                        errorMessage = throwable.message ?: "Falha ao carregar adesoes excluidas.",
                    )
                }
            }
        }
    }

    suspend fun resolvePublicCadastroLink(token: String): PublicCadastroLinkResolveResponse {
        return workflowRepository.resolvePublicCadastroLink(token)
    }

    suspend fun checkPublicCadastroCpf(token: String, cpf: String): PublicCadastroCheckCpfResponse {
        return workflowRepository.checkPublicCadastroCpf(token, cpf)
    }

    suspend fun submitPublicCadastro(token: String, payload: PublicCadastroPayload): PublicCadastroSubmitResponse {
        return workflowRepository.submitPublicCadastro(token, payload)
    }

    suspend fun searchEmpresaDirect(value: String, type: EmpresaSearchType): List<EmpresaResumo> {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        return workflowRepository.searchEmpresa(activeSession, value, type)
    }

    suspend fun createUser(payload: JsonObject) {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        repository.createUser(activeSession, payload)
        refreshAdminData(activeSession)
    }

    suspend fun updateUser(id: String, payload: JsonObject): AdminUser {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateUser(activeSession, id, payload)
        refreshAdminData(activeSession)
        return updated
    }

    suspend fun createTeam(name: String): AdminTeam {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        val created = repository.createTeam(activeSession, name)
        refreshAdminData(activeSession)
        return created
    }

    suspend fun updateTeam(id: String, payload: JsonObject): AdminTeam {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateTeam(activeSession, id, payload)
        refreshAdminData(activeSession)
        return updated
    }

    suspend fun updateCadastroConfig(payload: JsonObject): CadastroConfig {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        val config = repository.updateCadastroConfig(activeSession, payload)
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(config = config),
            )
        }
        return config
    }
    suspend fun updateCadastroRecord(id: String, payload: kotlinx.serialization.json.JsonObject): CadastroDetalhe {
        return runCatching {
            val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
            val profile = _uiState.value.profile
                ?: throw IllegalStateException("Sua sessão expirou. Faça login novamente para continuar.")
            val activeSession = ensureFreshSession(session)
            val payloadPersistencia = buildJsonObject {
                payload.forEach { (key, value) -> put(key, value) }
                put("created_by", profile.id)
                profile.teamId?.takeIf { it.isNotBlank() }?.let { put("team_id", it) }
            }
            val updated = workflowRepository.updateCadastro(activeSession, id, payloadPersistencia)
            val cadastrosAtualizados = repository.fetchCadastros(activeSession)
            _uiState.update {
                it.copy(
                    selectedCadastro = updated,
                    cadastros = cadastrosAtualizados,
                )
            }
            updated
        }.getOrElse { throwable ->
            throw IllegalStateException(
                mapCadastroFlowErrorMessage(
                    throwable.message,
                    "Falha ao atualizar cadastro.",
                ),
            )
        }
    }

    suspend fun createCadastroRecord(payload: kotlinx.serialization.json.JsonObject): CadastroDetalhe {
        return runCatching {
            val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
            val profile = _uiState.value.profile ?: throw IllegalStateException("UsuÃ¡rio nÃ£o autenticado.")
            val activeSession = ensureFreshSession(session)
            val created = workflowRepository.createCadastroDraft(activeSession, profile, payload)
            val cadastrosAtualizados = repository.fetchCadastros(activeSession)
            val statsAtualizadas = repository.fetchCadastroStats(activeSession)
            _uiState.update {
                it.copy(
                    selectedCadastro = created,
                    cadastros = cadastrosAtualizados,
                    cadastroStats = statsAtualizadas,
                )
            }
            created
        }.getOrElse { throwable ->
            throw IllegalStateException(
                mapCadastroFlowErrorMessage(
                    throwable.message,
                    "Falha ao criar cadastro.",
                ),
            )
        }
    }

    fun persistCadastroDraftSilently(id: String, payload: JsonObject) {
        val session = currentSession ?: return
        viewModelScope.launch {
            runCatching {
                val activeSession = ensureFreshSession(session)
                val profile = _uiState.value.profile
                val payloadPersistencia = buildJsonObject {
                    payload.forEach { (key, value) -> put(key, value) }
                    profile?.id?.takeIf { it.isNotBlank() }?.let { put("created_by", it) }
                    profile?.teamId?.takeIf { it.isNotBlank() }?.let { put("team_id", it) }
                }
                workflowRepository.updateCadastro(activeSession, id, payloadPersistencia)
            }.onSuccess { updated ->
                _uiState.update { current ->
                    if (current.selectedCadastro?.id == id) {
                        current.copy(selectedCadastro = updated)
                    } else {
                        current
                    }
                }
            }.onFailure { throwable ->
                Log.w(logTag, "Falha ao persistir rascunho em background", throwable)
            }
        }
    }

    suspend fun deleteCadastroRecord(
        id: String,
        motivoExclusao: String = "Exclusao solicitada pelo app mobile",
    ) {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        workflowRepository.deleteCadastroLogico(
            session = activeSession,
            cadastroId = id,
            motivoExclusao = motivoExclusao,
        )
        val cadastrosAtualizados = repository.fetchCadastros(activeSession)
        val statsAtualizadas = repository.fetchCadastroStats(activeSession)
        _uiState.update {
            it.copy(
                selectedCadastro = null,
                cadastros = cadastrosAtualizados,
                cadastroStats = statsAtualizadas,
            )
        }
    }

    suspend fun uploadTempFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        prefix: String = "",
    ): UploadedTempFile {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val profile = _uiState.value.profile ?: throw IllegalStateException("UsuÃ¡rio nÃ£o autenticado.")
        val activeSession = ensureFreshSession(session)
        return workflowRepository.uploadTempFile(activeSession, profile.id, fileName, mimeType, bytes, prefix)
    }

    suspend fun deleteTempFile(path: String) {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        workflowRepository.deleteTempFile(activeSession, path)
    }

    suspend fun buscarResponsaveisFinanceiros(
        tipoBusca: InclusaoBuscaTipo,
        valor: String,
    ): List<ResponsavelFinanceiroResumo> {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        return workflowRepository.buscarResponsaveisFinanceiros(activeSession, tipoBusca, valor)
    }

    suspend fun enviarInclusaoDependente(payload: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement {
        val session = currentSession ?: throw IllegalStateException("SessÃ£o nÃ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        return workflowRepository.enviarInclusaoDependente(activeSession, payload)
    }

    suspend fun uploadDependenteDocumento(
        idFuncionario: Int,
        idDependente: Int,
        arquivoPath: String,
        arquivoNome: String,
        bucket: String = "cadastros-temp-files",
    ): Boolean {
        val session = currentSession ?: throw IllegalStateException("SessÃƒÂ£o nÃƒÂ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        return workflowRepository.uploadDependenteDocumento(
            session = activeSession,
            idFuncionario = idFuncionario,
            idDependente = idDependente,
            arquivoPath = arquivoPath,
            arquivoNome = arquivoNome,
            bucket = bucket,
        )
    }

    suspend fun enqueueDependenteUpload(
        cadastroId: String?,
        idFuncionario: Int,
        idDependente: Int,
        arquivoPath: String,
        arquivoNome: String,
        tipo: String = "dependente",
        bucket: String = "cadastros-temp-files",
    ): Boolean {
        val session = currentSession ?: throw IllegalStateException("SessÃƒÂ£o nÃƒÂ£o encontrada.")
        val activeSession = ensureFreshSession(session)
        return workflowRepository.enqueueDependenteUpload(
            session = activeSession,
            cadastroId = cadastroId,
            idFuncionario = idFuncionario,
            idDependente = idDependente,
            arquivoPath = arquivoPath,
            arquivoNome = arquivoNome,
            tipo = tipo,
            bucket = bucket,
        )
    }

    private suspend fun bootstrapSession(session: SavedSession) {
        _uiState.update {
            it.copy(
                loading = true,
                isAuthenticated = true,
                configurationMissing = !AppConfig.isConfigured(),
                cadastros = emptyList(),
                cadastrosLoading = false,
                cadastrosLoaded = false,
                cadastroSupportLoading = false,
                cadastroSupportLoaded = false,
                vendedores = emptyList(),
                adesionistas = emptyList(),
                planosMap = emptyList(),
                parentescosMap = emptyList(),
                statusAdesoes = emptyList(),
                adminUsers = emptyList(),
                adminTeams = emptyList(),
                adminLoading = false,
                adminLoaded = false,
                auditLemmit = AuditLemmitResponse(),
                uploadQueue = emptyList(),
                uploadQueueOperation = null,
                resetQueueResult = null,
                cadastrosExcluidos = emptyList(),
                adminFeatureLoading = false,
                cadastroWorkspace = CadastroWorkspaceState(),
                linkWorkspace = LinkWorkspaceState(),
                cadastroOverlay = null,
                errorMessage = null,
            )
        }

        runCatching { withContext(Dispatchers.IO) { loadCriticalSessionData(session) } }
            .onSuccess { critical ->
                applyCriticalSessionData(critical, null)
                prefetchCadastroSupport(session, critical.profile)
                if (_uiState.value.activeTab == MainTab.CADASTROS) {
                    ensureCadastroResourcesLoaded()
                }
                if (_uiState.value.activeTab in setOf(
                        MainTab.USERS,
                        MainTab.TEAMS,
                        MainTab.SETTINGS,
                        MainTab.AUDITORIA_LEMMIT,
                        MainTab.FILA_UPLOAD_ERP,
                        MainTab.ADESOES_EXCLUIDAS,
                    )
                ) {
                    ensureAdminResourcesLoaded()
                }
            }
            .onFailure { throwable ->
                Log.e(logTag, "Falha ao carregar dados completos da sessao", throwable)
                runCatching { withContext(Dispatchers.IO) { loadFallbackSessionData(session) } }
                    .onSuccess { fallback ->
                        applyCriticalSessionData(
                            critical = fallback,
                            errorMessage = "SessÃ£o iniciada com dados parciais. Use Atualizar para tentar novamente.",
                        )
                    }
                    .onFailure { fallbackThrowable ->
                        Log.e(logTag, "Falha ao carregar fallback da sessao", fallbackThrowable)
                        sessionStore.clear()
                        _uiState.update {
                            it.copy(
                                loading = false,
                                isAuthenticated = false,
                                profile = null,
                                team = null,
                                cadastros = emptyList(),
                                cadastrosLoaded = false,
                                cadastroSupportLoaded = false,
                                errorMessage = fallbackThrowable.message ?: "SessÃ£o invÃ¡lida ou expirada.",
                            )
                        }
                    }
            }
    }

    private fun applyCriticalSessionData(critical: CriticalSessionData, errorMessage: String?) {
        _uiState.update { current ->
            current.copy(
                loading = false,
                isAuthenticated = true,
                profile = critical.profile,
                team = critical.team,
                systemOverview = critical.systemOverview,
                cadastroStats = critical.cadastroStats,
                cadastrosLoading = false,
                configurationMissing = !AppConfig.isConfigured(),
                errorMessage = errorMessage,
            )
        }
    }

    private suspend fun refreshAll(session: SavedSession) {
        _uiState.update { it.copy(loading = true, errorMessage = null) }
        runCatching { withContext(Dispatchers.IO) { loadCriticalSessionData(session) } }
            .onSuccess { critical ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        profile = critical.profile,
                        team = critical.team,
                        systemOverview = critical.systemOverview,
                        cadastroStats = critical.cadastroStats,
                    )
                }
                prefetchCadastroSupport(session, critical.profile, force = true)
                if (_uiState.value.activeTab == MainTab.CADASTROS || _uiState.value.cadastrosLoaded) {
                    ensureCadastroResourcesLoaded(force = true)
                }
                if (_uiState.value.activeTab in setOf(
                        MainTab.USERS,
                        MainTab.TEAMS,
                        MainTab.SETTINGS,
                        MainTab.AUDITORIA_LEMMIT,
                        MainTab.FILA_UPLOAD_ERP,
                        MainTab.ADESOES_EXCLUIDAS,
                    ) || _uiState.value.adminLoaded
                ) {
                    ensureAdminResourcesLoaded(force = true)
                }
            }
            .onFailure { throwable ->
                Log.e(logTag, "Falha ao atualizar dados da sessao", throwable)
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = throwable.message ?: "Falha ao atualizar dados.",
                    )
                }
            }
    }

    private fun ensureCadastroResourcesLoaded(force: Boolean = false) {
        val session = currentSession ?: return
        val profile = _uiState.value.profile ?: return
        if (!force && _uiState.value.cadastroSupportLoaded && _uiState.value.cadastrosLoaded) return

        if (force || !_uiState.value.cadastroSupportLoaded) {
            prefetchCadastroSupport(session, profile, force = force)
        }

        if (force || !_uiState.value.cadastrosLoaded) {
            viewModelScope.launch {
                _uiState.update { it.copy(cadastrosLoading = true, errorMessage = null) }
                runCatching { withContext(Dispatchers.IO) { loadCadastros(session) } }
                    .onSuccess { cadastros ->
                        _uiState.update {
                            it.copy(
                                cadastros = cadastros,
                                cadastrosLoading = false,
                                cadastrosLoaded = true,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        Log.e(logTag, "Falha ao carregar lista de cadastros", throwable)
                        _uiState.update {
                            it.copy(
                                cadastrosLoading = false,
                                errorMessage = throwable.message ?: "Falha ao carregar cadastros.",
                            )
                        }
                    }
            }
        }
    }

    private fun ensureAdminResourcesLoaded(force: Boolean = false) {
        val session = currentSession ?: return
        if (!force && (_uiState.value.adminLoaded || _uiState.value.adminLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(adminLoading = true, errorMessage = null) }
            runCatching { withContext(Dispatchers.IO) { loadAdminData(session) } }
                .onSuccess { adminData ->
                    _uiState.update {
                        it.copy(
                            adminUsers = adminData.users,
                            adminTeams = adminData.teams,
                            adminLoading = false,
                            adminLoaded = true,
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.e(logTag, "Falha ao carregar dados administrativos", throwable)
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            errorMessage = throwable.message ?: "Falha ao carregar dados administrativos.",
                        )
                    }
                }
        }
    }

    private suspend fun refreshAdminData(session: SavedSession) {
        val adminData = withContext(Dispatchers.IO) { loadAdminData(session) }
        _uiState.update {
            it.copy(
                adminUsers = adminData.users,
                adminTeams = adminData.teams,
                adminLoaded = true,
            )
        }
    }

    private fun prefetchCadastroSupport(
        session: SavedSession,
        profile: MobileProfile,
        force: Boolean = false,
    ) {
        if (!force && (_uiState.value.cadastroSupportLoading || _uiState.value.cadastroSupportLoaded)) return

        viewModelScope.launch {
            _uiState.update { it.copy(cadastroSupportLoading = true) }
            runCatching { withContext(Dispatchers.IO) { loadCadastroSupportData(session, profile) } }
                .onSuccess { support ->
                    _uiState.update { current ->
                        current.copy(
                            cadastroSupportLoading = false,
                            cadastroSupportLoaded = true,
                            vendedores = support.vendedores,
                            adesionistas = support.adesionistas,
                            planosMap = support.planos,
                            parentescosMap = support.parentescos,
                            statusAdesoes = support.statusAdesoes,
                            cadastroWorkspace = current.cadastroWorkspace.copy(
                                config = support.config,
                                selectedVendedorId = current.cadastroWorkspace.selectedVendedorId
                                    .takeIf { value -> support.vendedores.any { it.id == value } }
                                    ?: "",
                                selectedAdesionistaId = current.cadastroWorkspace.selectedAdesionistaId
                                    .takeIf { value -> support.adesionistas.any { it.id == value } }
                                    ?: "",
                            ),
                            linkWorkspace = current.linkWorkspace.copy(links = support.links),
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.e(logTag, "Falha ao carregar suporte do modulo cadastro", throwable)
                    _uiState.update {
                        it.copy(
                            cadastroSupportLoading = false,
                            errorMessage = throwable.message ?: "Falha ao carregar modulo cadastro.",
                        )
                    }
                }
        }
    }

    private suspend fun loadCriticalSessionData(session: SavedSession): CriticalSessionData {
        val activeSession = ensureFreshSession(session)
        val profile = repository.fetchProfile(activeSession)

        return coroutineScope {
            val teamDeferred = async { profile.teamId?.let { repository.fetchTeam(activeSession, it) } }
            val statsDeferred = async {
                runCatching { repository.fetchCadastroStats(activeSession) }
                    .recoverCatching { repository.fetchCadastroStatsFromCache(activeSession) }
                    .getOrDefault(CadastroStats())
            }
            val overviewDeferred = async {
                if (profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR")) {
                    repository.fetchSystemOverview(activeSession)
                } else {
                    SystemOverview()
                }
            }

            CriticalSessionData(
                profile = profile,
                team = teamDeferred.await(),
                systemOverview = overviewDeferred.await(),
                cadastroStats = statsDeferred.await(),
            )
        }
    }

    private suspend fun loadCadastroSupportData(
        session: SavedSession,
        profile: MobileProfile,
    ): CadastroSupportData {
        val activeSession = ensureFreshSession(session)

        return coroutineScope {
            val configDeferred = async { workflowRepository.fetchCadastroConfig(activeSession) }
            val vendedoresDeferred = async {
                if (profile.role != "VENDEDOR") {
                    workflowRepository.fetchProfilesByRole(activeSession, "VENDEDOR")
                } else {
                    emptyList()
                }
            }
            val adesionistasDeferred = async {
                if (profile.role in setOf("ADMINISTRADOR", "ADMIN", "GERENTE", "GESTOR", "SUPERVISOR", "VENDEDOR", "CADASTRO")) {
                    workflowRepository.fetchProfilesByRole(activeSession, "ADESIONISTA")
                } else {
                    emptyList()
                }
            }
            val linksDeferred = async { workflowRepository.fetchActiveLinks(activeSession) }
            val planosDeferred = async { workflowRepository.fetchPlanosMap(activeSession) }
            val parentescosDeferred = async { workflowRepository.fetchParentescosMap(activeSession) }
            val statusDeferred = async { workflowRepository.fetchStatusAdesoes(activeSession) }

            CadastroSupportData(
                config = configDeferred.await(),
                vendedores = vendedoresDeferred.await(),
                adesionistas = adesionistasDeferred.await(),
                links = linksDeferred.await(),
                planos = planosDeferred.await(),
                parentescos = parentescosDeferred.await(),
                statusAdesoes = statusDeferred.await(),
            )
        }
    }

    private suspend fun loadCadastros(session: SavedSession): List<CadastroResumo> {
        val activeSession = ensureFreshSession(session)
        return repository.fetchCadastros(activeSession)
    }

    private suspend fun loadAdminData(session: SavedSession): AdminData {
        val activeSession = ensureFreshSession(session)
        return coroutineScope {
            val usersDeferred = async { repository.fetchUsers(activeSession) }
            val teamsDeferred = async { repository.fetchTeamsAdmin(activeSession) }
            AdminData(
                users = usersDeferred.await(),
                teams = teamsDeferred.await(),
            )
        }
    }

    private suspend fun loadFallbackSessionData(session: SavedSession): LoadedSessionData {
        val activeSession = ensureFreshSession(session)
        val profile = repository.fetchProfile(activeSession)
        val team = profile.teamId?.let { repository.fetchTeam(activeSession, it) }
        return LoadedSessionData(
            profile = profile,
            team = team,
            systemOverview = SystemOverview(),
            cadastroStats = CadastroStats(),
        )
    }

    private suspend fun ensureFreshSession(session: SavedSession): SavedSession {
        val refreshed = authService.refreshIfNeeded(session)
        if (refreshed != session) {
            currentSession = refreshed
            sessionStore.save(refreshed)
        }
        return refreshed
    }

    private fun requiresVendedorSelection(profile: MobileProfile): Boolean {
        return profile.role in setOf(
            "ADMINISTRADOR",
            "ADMIN",
            "GERENTE",
            "GESTOR",
            "SUPERVISOR",
            "CADASTRO",
            "ADESIONISTA",
            "VENDEDOR",
        )
    }

    private suspend fun ensureCadastroEmpresaBeforeSend(
        session: SavedSession,
        cadastroId: String,
        fallbackEmpresa: EmpresaResumo?,
        cachedCadastro: CadastroDetalhe? = null,
    ): CadastroDetalhe {
        var detalhe = cachedCadastro ?: workflowRepository.fetchCadastroDetalhe(session, cadastroId)
        if ((detalhe.empresaId ?: detalhe.empresaCodigo) != null) return detalhe

        val inferredFromRaw = inferEmpresaCodigoFromRaw(detalhe.empresaRaw)
        val empresaId = fallbackEmpresa?.id?.takeIf { it > 0 } ?: inferredFromRaw
        val empresaCodigo = fallbackEmpresa?.codigo?.takeIf { it > 0 } ?: empresaId

        if (empresaId == null || empresaCodigo == null) {
            throw IllegalStateException("Selecione uma empresa antes de enviar.")
        }

        detalhe = workflowRepository.updateCadastro(
            session = session,
            id = cadastroId,
            payload = buildJsonObject {
                put("empresa_id", empresaId)
                put("empresa_codigo", empresaCodigo)
                fallbackEmpresa?.nomeFantasia
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put("empresa_nome", it) }
                fallbackEmpresa?.cnpj
                    ?.takeIf { it.isNotBlank() }
                    ?.let { put("empresa_cnpj", it) }
                fallbackEmpresa?.raw?.let { put("empresa_raw", it) }
                    ?: detalhe.empresaRaw?.let { put("empresa_raw", it) }
                fallbackEmpresa?.exigeMatricula?.let { put("empresa_exige_matricula", it) }
                fallbackEmpresa?.precoPlano?.let { put("planos_raw", it) }
                    ?: detalhe.planosRaw?.let { put("planos_raw", it) }
            },
        )

        if ((detalhe.empresaId ?: detalhe.empresaCodigo) == null) {
            detalhe = workflowRepository.fetchCadastroDetalhe(session, cadastroId)
            if ((detalhe.empresaId ?: detalhe.empresaCodigo) == null) {
                throw IllegalStateException("Selecione uma empresa antes de enviar.")
            }
        }
        return detalhe
    }

    private fun inferEmpresaCodigoFromRaw(raw: JsonElement?): Int? {
        val obj = runCatching { raw?.jsonObject }.getOrNull() ?: return null
        val keys = listOf("id", "codigo", "codigoDaEmpresa", "empresa_codigo", "empresaId", "codigoEmpresa")
        for (key in keys) {
            val primitive = obj[key]?.jsonPrimitive ?: continue
            primitive.intOrNull?.takeIf { it > 0 }?.let { return it }
            primitive.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        return null
    }

    private fun mapCadastroFlowErrorMessage(message: String?, fallback: String): String {
        val normalized = message?.lowercase().orEmpty()
        val isAuthSessionError =
            normalized.contains("jwt expired") ||
                normalized.contains("invalid jwt") ||
                normalized.contains("auth session missing") ||
                normalized.contains("refresh token") ||
                normalized.contains("token has expired") ||
                normalized.contains("401 unauthorized") ||
                normalized.contains("status: 401")
        val isCreatedByProfileError =
            normalized.contains("created_by") &&
                (
                    normalized.contains("violates") ||
                        normalized.contains("foreign key") ||
                        normalized.contains("not-null")
                    )
        return when {
            isCreatedByProfileError ->
                "Perfil do usuário não encontrado para este cadastro. Saia e entre novamente; se persistir, solicite ajuste do perfil."
            isAuthSessionError ->
                "Sua sessão expirou. Faça login novamente para continuar."
            normalized.contains("row-level security") || normalized.contains("permission denied") ->
                "Você não tem permissão para concluir esta operação com o usuário atual."
            normalized.contains("invalid input syntax for type integer") ->
                "Existe um campo numerico invalido no cadastro. Revise empresa, vendedor, planos e dados obrigatorios."
            normalized.contains("conversion failed when converting date and/or time from character string") ->
                "Existe uma data invalida no cadastro. Revise data de nascimento do titular/dependentes e tente novamente."
            normalized.contains("cd_plano") || normalized.contains("plano valido para") ->
                "Selecione um plano valido para o titular e dependentes antes de cadastrar."
            normalized.contains("cadastro sem nome") ->
                "Cadastro sem nome. Preencha o nome do titular na etapa 1."
            normalized.contains("selecione uma empresa antes de enviar") ->
                "Selecione uma empresa valida antes de cadastrar."
            else -> message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val json = Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            }
            val client = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(json)
                }
            }
            val sessionStore = SessionStore(appContext)
            val authService = SupabaseAuthService(client = client, json = json)
            val repository = SupabaseRepository(client = client, json = json)
            val workflowRepository = CadastroWorkflowRepository(client = client, json = json)

            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        sessionStore = sessionStore,
                        authService = authService,
                        repository = repository,
                        workflowRepository = workflowRepository,
                    ) as T
                }
            }
        }
    }
}

private data class CriticalSessionData(
    val profile: MobileProfile,
    val team: MobileTeam?,
    val systemOverview: SystemOverview,
    val cadastroStats: CadastroStats,
)

private data class CadastroSupportData(
    val config: CadastroConfig?,
    val vendedores: List<TeamMemberOption>,
    val adesionistas: List<TeamMemberOption>,
    val links: List<CadastroLinkItem>,
    val planos: List<PlanoMap>,
    val parentescos: List<ParentescoMap>,
    val statusAdesoes: List<StatusAdesao>,
)

private data class AdminData(
    val users: List<AdminUser>,
    val teams: List<AdminTeam>,
)

private typealias LoadedSessionData = CriticalSessionData

