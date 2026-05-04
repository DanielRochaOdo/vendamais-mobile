# SPECS TECNICAS - HANDOFF COMPLETO DO PROJETO ADESART

Data da analise: 2026-04-22
Escopo analisado: frontend React (src), Edge Functions (supabase/functions), migrations SQL (supabase/migrations), configuracoes de build/deploy.

## 1. Objetivo deste documento
Este documento foi escrito para passagem de bastao para outro dev continuar o projeto sem depender de contexto oral.

Cobertura deste arquivo:
- mapa de rotas e redirecionamentos
- onde cada modal e chamado
- onde cada regra de negocio esta aplicada
- inventario completo de APIs (frontend -> edge, frontend -> RPC, edge -> ERP/Lemmit)
- mapeamento de tabelas, storage, realtime e persistencia local
- configuracoes de ambiente e deploy
- riscos conhecidos, pontos de atencao e checklist de onboarding

---

## 2. Arquitetura geral

### 2.1 Stack
- Frontend: React 18 + TypeScript + Vite + Tailwind
- Roteamento: react-router-dom
- Backend: Supabase (Postgres + Auth + Storage + Realtime + Edge Functions)
- Persistencia client-side: localStorage + Zustand persist + IndexedDB
- Integracoes externas: ERP (odontoart.s4e.com.br) e Lemmit

### 2.2 Bootstrap e providers
- Entrada da app: `src/main.tsx`
- Arvore principal: `src/App.tsx`
- Ordem de providers:
  1. `ErrorBoundary`
  2. `BrowserRouter`
  3. `AuthProvider`
  4. `ConfigCadastroProvider`
  5. rotas (`AppRoutes`)

### 2.3 Contextos centrais
- `src/contexts/AuthContext.tsx`
  - restaura sessao (`supabase.auth.getSession`)
  - sincroniza estado com `onAuthStateChange`
  - busca perfil em `profiles`
  - expõe `signIn`, `signOut`, `refreshProfile`
- `src/contexts/ConfigCadastroContext.tsx`
  - carrega e mantem em memoria:
    - `cadastro_config`
    - `cadastro_planos_map`
    - `cadastro_parentesco_map`
  - faz subscribe realtime em `cadastro_config`

---

## 3. Rotas, guardas e redirecionamentos

## 3.1 Tabela de rotas
| Rota | Componente | Protecao | Perfis com acesso |
|---|---|---|---|
| `/adesao/:token` | `PublicCadastroLink` | publica | qualquer usuario sem login |
| `/preview/link-plano` | `PublicCadastroLinkPreview` | publica | qualquer usuario sem login |
| `/login` | `Login` | publica com redirect | nao autenticado |
| `/dashboard` | `Dashboard` | `ProtectedRoute` | autenticado |
| `/users` | `Users` | `ProtectedRoute allowedRoles` | `ADMINISTRADOR`, `GERENTE`, `SUPERVISOR` |
| `/teams` | `Teams` | `ProtectedRoute` | autenticado |
| `/cadastro` | `Cadastro` | `ProtectedRoute` | autenticado |
| `/configuracoes` | `ConfiguracoesCadastro` | `ProtectedRoute` | autenticado (menu exibe para admin) |
| `/auditoria-lemmit` | `AuditoriaLemmit` | `ProtectedRoute allowedRoles` | `ADMINISTRADOR` |
| `/fila-upload-erp` | `FilaUploadERP` | `ProtectedRoute allowedRoles` | `ADMINISTRADOR` |
| `/adesoes-excluidas` | `AdesoesExcluidas` | `ProtectedRoute allowedRoles` | `ADMINISTRADOR` |
| `/profile` | `Profile` | `ProtectedRoute` | autenticado |
| `/` | redirect | condicional auth | dashboard ou login |
| `*` | redirect | fallback global | dashboard ou login |

Arquivos:
- `src/App.tsx`
- `src/components/ProtectedRoute.tsx`

## 3.2 Regras de redirecionamento
- Usuario autenticado abrindo `/login` vai para `/dashboard`
- Usuario sem sessao em rota protegida vai para `/login`
- Usuario sem role permitida em rota com `allowedRoles` vai para `/dashboard`
- `/` e wildcard `*` redirecionam para dashboard (auth) ou login (nao auth)
- Apos login com sucesso (`src/pages/Login.tsx`), `navigate('/dashboard')`
- Logout (`src/components/Layout.tsx`) faz `navigate('/login')`
- `ErrorBoundary` tem recovery por `window.location.reload()`

## 3.3 Menu e direcionamento por role
Arquivo: `src/components/Layout.tsx`

Menus principais:
- sempre: dashboard, cadastro, profile
- users: apenas `ADMINISTRADOR|GERENTE|SUPERVISOR`
- teams: `ADMINISTRADOR|GERENTE|CADASTRO|SUPERVISOR|VENDEDOR|ADESIONISTA`

Menus de configuracao (dropdown):
- configuracoes: admin
- auditoria lemmit: admin
- fila upload erp: admin
- adesoes excluidas: admin

---

## 4. Mapa das telas (responsabilidade funcional)

### 4.1 Cadastro (`src/pages/Cadastro.tsx`)
- Tabs: `novo`, `link`, `dependente`, `incompletos`, `completos`
- Persiste estado da pagina em `localStorage` por usuario (`cadastro-page-state:{userId}`)
- Carrega cadastros sob demanda para abas incompletos/completos
- Abre modais principais:
  - `CadastroModal`
  - `InclusaoDependenteModal`
  - `ContinuarInclusaoDependenteModal`

### 4.2 Dashboard (`src/pages/Dashboard.tsx`)
- Exibe cards com estatisticas mensais de cadastro e inclusao de dependente
- Busca stats por vendedor via RPC `get_stats_by_vendedor`
- Abre `StatsByVendedorModal`
- Bloco de visao geral: usuarios, equipes, perfil

### 4.3 Users (`src/pages/Users.tsx`)
- CRUD parcial de usuarios (criacao via edge function)
- Busca perfis + equipes
- Criacao via endpoint `create-user`
- Edicao via `EditUserModal`

### 4.4 Teams (`src/pages/Teams.tsx`)
- Admin/Gerente: lista e gerencia equipes
- Supervisor: visao focada na propria equipe
- Modais:
  - `EditTeamModal`
  - `EditTeamMembersModal`

### 4.5 Configuracoes (`src/pages/ConfiguracoesCadastro.tsx`)
Abas:
- geral (`GeralConfigCard`)
- planos (`PlanosMapTable`)
- parentesco (`ParentescoMapTable`)
- status de adesao (`StatusAdesoesTable`)
- logs de API (`ApiLogsTable`)

### 4.6 Auditoria Lemmit (`src/pages/AuditoriaLemmit.tsx`)
- usa RPC `audit_lemmit`
- filtros por periodo + paginacao
- cards de consumo/custo + ultimas consultas

### 4.7 Fila Upload ERP (`src/pages/FilaUploadERP.tsx`)
- admin only
- listagem da `erp_upload_queue`
- subscribe realtime `erp_upload_queue_changes`
- acao manual `erp-process-upload-queue`
- acao manual de reset `reset_stuck_queue_items`

### 4.8 Adesoes Excluidas (`src/pages/AdesoesExcluidas.tsx`)
- admin only
- consulta `cadastros_excluidos`
- filtros por nome/cpf/periodo/exclusor
- modal de detalhe com snapshot JSON da adesao excluida

### 4.9 Perfil (`src/pages/Profile.tsx`)
- edicao de nome, telefone e external_id
- update direto em `profiles`
- valida telefone para 11 digitos

### 4.10 Fluxo publico de adesao
- `src/pages/PublicCadastroLink.tsx` (pagina real)
- `src/pages/PublicCadastroLinkPreview.tsx` (preview local)

---

## 5. Mapa completo de modais

## 5.1 Modais de cadastro
| Modal | Definicao | Onde e chamado | Quando abre |
|---|---|---|---|
| `CadastroModal` | `src/components/cadastro/CadastroModal.tsx` | `src/pages/Cadastro.tsx` | continuar adesao pendente tipo `cadastro` |
| `InclusaoDependenteModal` | `src/components/cadastro/InclusaoDependenteModal.tsx` | `src/pages/Cadastro.tsx` | iniciar inclusao de dependente |
| `ContinuarInclusaoDependenteModal` | `src/components/cadastro/ContinuarInclusaoDependenteModal.tsx` | `src/pages/Cadastro.tsx` | continuar pendencia `inclusao_dependente` |
| `SelectStatusModal` | `src/components/cadastro/SelectStatusModal.tsx` | `CadastroModal`, `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | status de adesao obrigatorio antes de concluir |
| `ParceiroInvalidoModal` | `src/components/cadastro/ParceiroInvalidoModal.tsx` | `CadastroModal`, `InclusaoDependenteModal` | retorno ERP com erro de parceiro/vendedor invalido |
| `DependenteAtivoModal` | `src/components/cadastro/DependenteAtivoModal.tsx` | `CadastroModal` | erro ERP codigo 3 com dependente ativo |
| `LemmitLimitModal` | `src/components/cadastro/LemmitLimitModal.tsx` | `NovoCadastroCard`, `DependentesSection`, `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | limite de consulta Lemmit atingido |
| `LemmitErrorModal` | `src/components/cadastro/LemmitErrorModal.tsx` | `NovoCadastroCard` | erro de consulta Lemmit com opcao de continuar |
| `ClientExistsModal` | `src/components/cadastro/ClientExistsModal.tsx` | `NovoCadastroCard` | cliente ja existente detectado |
| `CadastroExistenteModal` | `src/components/cadastro/CadastroExistenteModal.tsx` | `NovoCadastroCard` | CPF existente no sistema/local |
| `AlreadyExistsModal` | `src/components/cadastro/AlreadyExistsModal.tsx` | `CadastrosIncompletosList`, `CadastrosGerenteView`, `CadastrosSupervisorView` | visualizar dados ERP associados ao CPF |
| `ExcluirCadastroModal` | `src/components/cadastro/ExcluirCadastroModal.tsx` | `CadastrosIncompletosList` | confirmacao de exclusao com motivo |
| `EmpresaNaoIdentificadaModal` | `src/components/cadastro/EmpresaNaoIdentificadaModal.tsx` | `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | fluxo nao conseguiu resolver empresa automaticamente |
| `EmpresaCanceladaModal` | `src/components/cadastro/EmpresaCanceladaModal.tsx` | `EmpresaSearchCard` | empresa com `codigoSituacao` bloqueado pela config |
| `ObservacoesEmpresaModal` | `src/components/cadastro/ObservacoesEmpresaModal.tsx` | `EmpresaSearchCard` | empresa com observacao comercial |
| `VisualizarArquivoModal` | `src/components/cadastro/VisualizarArquivoModal.tsx` | sem call site encontrado | componente existe, sem uso no estado atual |

## 5.2 Modais fora do modulo cadastro
| Modal | Definicao | Onde e chamado | Funcao |
|---|---|---|---|
| `StatsByVendedorModal` | `src/components/dashboard/StatsByVendedorModal.tsx` | `src/pages/Dashboard.tsx` | detalhar cards por vendedor |
| `EditUserModal` | `src/components/users/EditUserModal.tsx` | `src/pages/Users.tsx` | editar usuario |
| `EditTeamModal` | `src/components/teams/EditTeamModal.tsx` | `src/pages/Teams.tsx` | editar equipe |
| `EditTeamMembersModal` | `src/components/teams/EditTeamMembersModal.tsx` | `src/pages/Teams.tsx` | vincular/remover membros da equipe |

Observacao:
- `Users` e `Teams` possuem tambem modais inline locais para criacao (na propria pagina), alem dos modais componentizados acima.

---

## 6. Regras de negocio (catalogo detalhado)

## 6.1 Autenticacao e autorizacao
- Login exige credenciais Supabase Auth, depois valida perfil em `profiles`.
- Sem perfil ativo/sincronizado, sessao e limpa localmente.
- `ProtectedRoute` bloqueia nao autenticado e role nao autorizada.
- Restricoes adicionais por pagina:
  - `FilaUploadERP` e `AdesoesExcluidas`: hard-check admin dentro da pagina
  - `Users`: criacao permitida para admin/gerente/supervisor
  - `Teams`: criacao de equipe so admin

## 6.2 Status de adesao (obrigatoriedade)
- Status obrigatorio no fechamento dos fluxos de cadastro e inclusao de dependente.
- Se `status_adesao_id` ausente, abre `SelectStatusModal`.
- Lista de status vem de `status_adesoes` ordenada por `ordem`.
- Mudanca de status de item pendente pode ser feita na listagem de incompletos.

Arquivos principais:
- `src/components/cadastro/SelectStatusModal.tsx`
- `src/components/cadastro/CadastroModal.tsx`
- `src/components/cadastro/InclusaoDependenteModal.tsx`
- `src/components/cadastro/ContinuarInclusaoDependenteModal.tsx`
- `src/components/cadastro/CadastrosIncompletosList.tsx`

## 6.3 Regras de empresa, plano e matricula
- Empresa pode ser bloqueada por `codigoSituacao` usando `cadastro_config.codigos_empresa_invalidos`.
- Quando bloqueada, abre `EmpresaCanceladaModal` e exige nova busca.
- Matricula obrigatoria se `empresa_exige_matricula === 1`.
- Regra de matricula e validada no frontend e revalidada no backend publico (`cadastro-public-submit`).
- Planos ocultos (`cadastro_config.planos_ocultos`) nao aparecem na selecao de dependentes, exceto preservando plano ja salvo em edicao.

Arquivos:
- `src/components/cadastro/EmpresaSearchCard.tsx`
- `src/components/cadastro/DependentesSection.tsx`
- `src/pages/PublicCadastroLink.tsx`
- `supabase/functions/cadastro-public-submit/index.ts`

## 6.4 Regras de CPF e bloqueio
- Novo cadastro valida CPF existente local via RPC `check_cpf_existente`.
- Verificacao ERP de associado (`erp-check-associado`) define:
  - `exists`
  - `shouldBlock`
  - `blockReason`
  - `summary` de empresa/plano/situacao
- Public link bloqueia CPF em 2 niveis:
  1. reutilizacao de CPF ja concluido no mesmo link
  2. bloqueio local global via RPC `check_public_link_blocked_cpf`
- RPC `check_public_link_blocked_cpf` bloqueia CPF ja usado como titular ou dependente em cadastro enviado (nao excluido).

Arquivos:
- `src/components/cadastro/NovoCadastroCard.tsx`
- `src/hooks/useCadastros.ts`
- `src/pages/PublicCadastroLink.tsx`
- `supabase/functions/cadastro-link-check-cpf/index.ts`
- `supabase/functions/cadastro-public-submit/index.ts`
- migration `20260323123000_add_check_public_link_blocked_cpf_function.sql`

## 6.5 Regras de dependentes
- Deve existir exatamente 1 titular no array de dependentes (`tipo === 1`).
- Todo dependente precisa de plano.
- Campos obrigatorios por dependente:
  - nome, data nascimento, sexo, parentesco/tipo, plano, nome da mae
- CPF de dependente:
  - validacao de digito
  - obrigatorio para maior de idade em fluxos onde esta regra foi implementada
- Parceiro invalido no ERP dispara modal para selecionar vendedor valido e reprocessar.

Arquivos:
- `src/components/cadastro/DependentesSection.tsx`
- `src/components/cadastro/InclusaoDependenteModal.tsx`
- `src/components/cadastro/ContinuarInclusaoDependenteModal.tsx`
- `src/components/cadastro/CadastroModal.tsx`

## 6.6 Lemmit (consumo, limite e cobranca)
- Feature toggle por config:
  - `ativar_lemmit`
  - `lemmit_dependente`
  - `lemmit_inclusao_dependente`
- Antes de consultar: RPC `can_use_lemmit`.
- Ao estourar limite: RPC `get_lemmit_limit_info` + `LemmitLimitModal`.
- Debito de saldo ocorre em dois caminhos:
  - frontend chama `debit_lemmit_balance` em alguns fluxos
  - edge `lemit-consulta-pessoa` chama `decrement_lemmit_balance`
- Custo unitario da consulta na edge: `0.12`.

Ponto de atencao:
- Existe risco de dupla contabilizacao em cenarios especificos se fluxo frontend e edge debitarem no mesmo evento funcional.

## 6.7 Upload de documentos e fila de retry
- Upload direto tenta `erp-upload-documento`.
- Em falha, fallback para fila assicrona `erp-enqueue-upload`.
- Worker `erp-process-upload-queue`:
  - processa itens `queued`/`retry_wait`
  - locka item para `processing`
  - maximo 5 tentativas
  - backoff de 10 minutos entre retries
  - espera 10 segundos entre uploads
  - remove arquivo do bucket apenas em sucesso
- Reset de travados:
  - RPC `reset_stuck_queue_items` (threshold default 15 min)
  - usado no inicio do worker e manualmente na tela admin

Migrations relevantes:
- `20260203165153_create_erp_upload_queue.sql`
- `20260204133345_add_erp_upload_queue_cron.sql`
- `20260204134253_add_reset_stuck_queue_items_function.sql`

## 6.8 Exclusao de adesao
- Exclusao exige motivo.
- Edge `excluir-cadastro` valida ownership:
  - `VENDEDOR`: so seu proprio cadastro (`vendedor_id == user.id`)
  - `ADESIONISTA`: so cadastro criado por ele (`created_by == user.id`)
- Antes de deletar em `cadastros`, grava snapshot em `cadastros_excluidos`.

## 6.9 Regras de criacao de usuarios
- Edge `create-user` valida:
  - role valido
  - roles operacionais (`CADASTRO`, `SUPERVISOR`, `VENDEDOR`, `ADESIONISTA`) exigem `external_id` e `team_id`
  - roles gerenciais (`ADMINISTRADOR`, `GERENTE`) nao devem receber `external_id/team_id`
  - supervisor so cria usuario da propria equipe
  - permissao minima para criar: `ADMINISTRADOR|GERENTE|SUPERVISOR`

## 6.10 Reconciliacao de envio ERP apos abort/network
- Hook `useCadastros.enviarParaERP` trata Abort-like errors.
- Tenta reconciliar por `erp-check-associado` para verificar se cadastro entrou no ERP mesmo com falha de rede local.
- Se reconciliado, sincroniza local como enviado com `reconciled: true`.

Arquivo:
- `src/hooks/useCadastros.ts`

---

## 7. Catalogo completo de API e integracoes

## 7.1 Frontend -> Edge Functions (`/functions/v1/*`)
| Endpoint | Metodo | Chamado em | Finalidade |
|---|---|---|---|
| `erp-check-associado` | POST | `useCadastros`, `InclusaoDependenteModal` | validar CPF/codigo no ERP, detectar bloqueio |
| `lemit-consulta-pessoa` | POST | `useCadastros`, `DependentesSection`, `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | enrichment Lemmit |
| `erp-endereco-cep` | POST | `useCadastros` | consulta endereco por CEP |
| `erp-search-empresa` | POST | `useCadastros` | busca empresa por CNPJ/nome/id |
| `erp-novo-usuario2` | POST | `useCadastros.enviarParaERP` | envio de adesao titular para ERP |
| `erp-novo-dependente` | POST | `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | envio de inclusao dependente |
| `erp-upload-documento` | POST | `CadastroModal`, `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | upload imediato doc dependente |
| `erp-enqueue-upload` | POST | `CadastroModal`, `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | enfileirar upload para processamento assicrono |
| `erp-process-upload-queue` | POST | `FilaUploadERP` | disparar worker de fila manualmente |
| `create-user` | POST | `Users` | criar usuario + profile |
| `excluir-cadastro` | POST | `CadastrosIncompletosList` | excluir com auditoria |
| `cadastro-link-resolve` | POST | `PublicCadastroLink` | resolver token publico |
| `cadastro-link-check-cpf` | POST | `PublicCadastroLink` | validar CPF antes do submit publico |
| `cadastro-public-submit` | POST | `PublicCadastroLink` | submit final publico + envio ERP + sync local |

## 7.2 Frontend -> RPC
| RPC | Chamado em | Objetivo |
|---|---|---|
| `get_cadastros_stats` | `useCadastros` | stats resumidas de cadastro |
| `get_stats_from_cache` | `useStats` | stats com cache |
| `get_stats_by_vendedor` | `Dashboard` | detalhe por vendedor para modal |
| `check_cpf_existente` | `NovoCadastroCard` | validacao de duplicidade local |
| `can_use_lemmit` | `NovoCadastroCard`, `DependentesSection`, `InclusaoDependenteModal`, `ContinuarInclusaoDependenteModal` | gate de limite/permissao |
| `get_lemmit_limit_info` | mesmos fluxos de cima | detalhe do limite para UI |
| `debit_lemmit_balance` | `NovoCadastroCard`, `InclusaoDependenteModal` | debito frontend de Lemmit |
| `audit_lemmit` | `AuditoriaLemmit` | cards e listas de auditoria |
| `reset_stuck_queue_items` | `FilaUploadERP` e worker | reset de itens travados |
| `check_public_link_blocked_cpf` | usado em edges publicas | bloqueio de CPF no fluxo publico |

## 7.3 Frontend -> tabelas Supabase (direto)
Principais tabelas consumidas/escritas:
- `profiles`
- `teams`
- `cadastros`
- `cadastro_links`
- `cadastros_excluidos`
- `cadastro_config`
- `cadastro_planos_map`
- `cadastro_parentesco_map`
- `status_adesoes`
- `api_logs`
- `erp_upload_queue`
- `lemmit_consultas`

## 7.4 Frontend -> Storage
Bucket principal:
- `cadastros-temp-files`

Usos:
- upload/remocao em utilitario `src/utils/uploadFile.ts`
- download assinado na tela de fila (`FilaUploadERP`)
- remocao de arquivos apos sucesso ou limpeza de pendencia

## 7.5 Frontend -> Realtime
- Canal `cadastro_config_changes` (`ConfigCadastroContext`)
- Canal `erp_upload_queue_changes` (`FilaUploadERP`)

## 7.6 Edge -> APIs externas
| Edge Function | API externa | Endpoint |
|---|---|---|
| `erp-check-associado` | ERP | `/v2/api/associados` |
| `erp-endereco-cep` | ERP | `/api/redeatendimento/Endereco` |
| `erp-search-empresa` | ERP | `/api/empresa/BuscaEmpresas` |
| `erp-novo-usuario2` | ERP | `/api/vendedor/NovoUsuario2` |
| `erp-novo-dependente` | ERP | `/api/vendedor/NovoDependente` |
| `erp-upload-documento` | ERP | `/api/dependente/UploadDocDependente` |
| `erp-process-upload-queue` | ERP | mesmo upload de documento |
| `cadastro-public-submit` | ERP | check associado + `NovoUsuario2` |
| `lemit-consulta-pessoa` | Lemmit | webhook HTTP configurado na funcao |

## 7.7 Edge -> Supabase (server-side)
Funcoes que gravam `api_logs`:
- `erp-check-associado`
- `erp-endereco-cep`
- `erp-search-empresa`
- `erp-novo-usuario2`
- `erp-novo-dependente`
- `erp-upload-documento`
- `lemit-consulta-pessoa`
- `cadastro-public-submit` (via helper para logs ERP public)

Funcoes com regras de dados internas:
- `create-user`: `auth.admin.createUser` + insert em `profiles`
- `excluir-cadastro`: insert `cadastros_excluidos` + delete `cadastros`
- `erp-enqueue-upload`: insert `erp_upload_queue`
- `erp-process-upload-queue`: update status fila + reset stuck
- `cadastro-link-resolve/check-cpf/submit`: validacoes e lifecycle de `cadastro_links` e `cadastros`

---

## 8. Persistencia local (frontend)

## 8.1 localStorage de UI por pagina
Chaves encontradas:
- `cadastro-page-state:{userId}`
- `ui:users:{userId}:search-term`
- `ui:users:{userId}:show-create-modal`
- `ui:users:{userId}:create-form`
- `ui:teams:{userId}:show-create-modal`
- `ui:teams:{userId}:team-name`
- `ui:profile:{userId}:editing`
- `ui:profile:{userId}:name-draft`
- `ui:profile:{userId}:external-id-draft`
- `ui:profile:{userId}:telefone-draft`
- `ui:configuracoes-cadastro:{userId}:active-tab`
- `ui:config-api-logs:{userId}:filter`
- `ui:config-api-logs:{userId}:page`
- `ui:config-api-logs:{userId}:data-inicio`
- `ui:config-api-logs:{userId}:data-fim`
- `ui:auditoria-lemmit:{userId}:current-page`
- `ui:auditoria-lemmit:{userId}:date-range`
- `ui:fila-upload-erp:{userId}:status-filter`
- `ui:fila-upload-erp:{userId}:current-page`
- `ui:adesoes-excluidas:{userId}:busca-nome`
- `ui:adesoes-excluidas:{userId}:busca-cpf`
- `ui:adesoes-excluidas:{userId}:data-inicio`
- `ui:adesoes-excluidas:{userId}:data-fim`
- `ui:adesoes-excluidas:{userId}:exclusor-filtro`
- `ui:adesoes-excluidas:{userId}:current-page`
- `public-cadastro-link-draft:{token}`

## 8.2 Drafts de modais (Zustand persist)
- Store: `src/state/draftStore.ts`
- Chave persistida: `modal-drafts-storage`
- Key format interno: `draft:{userId}:{modalName}` ou `draft:{userId}:{modalName}:{cadastroId}`
- TTL aplicado em `draftStorage.ts`: 7 dias
- Somente metadata de arquivo e guardada (sem base64)

## 8.3 Pending file store (IndexedDB)
- DB: `adesart-pending-files`
- Store: `files`
- Utilitario: `src/utils/pendingFileStore.ts`
- Objetivo: recuperar arquivo apos interrupcao/reload em fluxos com input file

## 8.4 Draft publico do link
- `PublicCadastroLink` guarda rascunho por token
- Versao de schema: `PUBLIC_CADASTRO_DRAFT_VERSION = 1`
- Expiracao: 24 horas
- persiste dados de cpf/form/dependentes/mensagem de lookup

---

## 9. Banco de dados e migracoes (resumo pratico)

## 9.1 Objetos mais criticos
- Tabelas de auth/organizacao: `profiles`, `teams`
- Core negocio: `cadastros`, `status_adesoes`, `cadastros_excluidos`
- Configuracao de cadastro: `cadastro_config`, `cadastro_planos_map`, `cadastro_parentesco_map`
- Integracao: `api_logs`, `erp_upload_queue`
- Fluxo publico: `cadastro_links` + colunas `cadastros.origem_link_id`, `cadastros.fluxo_publico`
- Controle Lemmit: campos de saldo/limite em `profiles` + consultas/auditoria

## 9.2 Funcoes SQL importantes
- `check_cpf_existente`
- `get_cadastros_stats`
- `get_stats_from_cache`
- `get_stats_by_vendedor`
- `audit_lemmit`
- `can_use_lemmit`
- `get_lemmit_limit_info`
- `debit_lemmit_balance`
- `decrement_lemmit_balance`
- `reset_stuck_queue_items`
- `check_public_link_blocked_cpf`

## 9.3 Cron e automacao
- migration `20260204133345_add_erp_upload_queue_cron.sql`
  - agenda `process_erp_upload_queue` a cada 2 minutos
- migration `20260204134253_add_reset_stuck_queue_items_function.sql`
  - cron de backup para reset de stuck a cada 30 minutos

## 9.4 Realtime
- `cadastro_config` publicado para realtime
- `erp_upload_queue` adicionado na publication realtime

## 9.5 Observacao historica de roles
No codigo TS ainda existe union com roles legadas (`GESTOR`, `CADASTRO`) em alguns pontos, enquanto regras novas usam principalmente `GERENTE` e outros perfis atuais. Revisar consolidacao de nomenclatura para evitar ambiguidades.

---

## 10. Configuracao e deploy

## 10.1 Variaveis frontend (`.env`)
Obrigatorias:
- `VITE_SUPABASE_URL`
- `VITE_SUPABASE_ANON_KEY`
- `VITE_PUBLIC_APP_URL`

Observacoes:
- `VITE_PUBLIC_APP_URL` nao pode apontar para localhost no gerador de link publico (`src/lib/publicUrl.ts`).
- Se nao definido em producao, fallback e `https://vendamais.odontoart.com`.

## 10.2 Segredos Edge Functions (Supabase)
- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY`
- `ERP_TOKEN`
- `ERP_BASE_URL`
- `ERP_URL`
- `ERP_URL_NOVO_DEPENDENTE`
- `ERP_ENDPOINT`
- `LEMMIT_API_KEY`

## 10.3 Scripts de projeto
`package.json`:
- `npm run dev`
- `npm run build`
- `npm run lint`
- `npm run preview`
- `npm run typecheck`

## 10.4 Deploy Vercel
`vercel.json`:
- build command: `npm run build`
- output: `dist`
- rewrite SPA global: `/(.*) -> /index.html`
- cache agressivo em `/assets/*`

---

## 11. Fluxos criticos ponta a ponta

## 11.1 Nova adesao interna
1. Usuario abre aba `novo` em `Cadastro`.
2. `NovoCadastroCard` valida CPF local (`check_cpf_existente`) e ERP (`erp-check-associado`).
3. Se habilitado, consulta Lemmit para pre-fill.
4. Cria/atualiza rascunho em `cadastros` com status incompleto.
5. Abre `CadastroModal` para completar dados/dependentes/arquivo/status.
6. Envia para ERP (`erp-novo-usuario2`) com idempotency key.
7. Faz sync local para status enviado.
8. Upload de documento: direto em ERP ou fallback para fila.

## 11.2 Inclusao de dependente (novo)
1. Aba `dependente` abre `InclusaoDependenteModal`.
2. Busca responsavel (ERP).
3. Resolve empresa e planos.
4. Valida dependentes + status de adesao.
5. Envia `erp-novo-dependente`.
6. Processa anexos (direto ou fila).

## 11.3 Continuar inclusao pendente
1. Em `incompletos`, seleciona cadastro tipo `inclusao_dependente`.
2. Abre `ContinuarInclusaoDependenteModal`.
3. Restaura draft/pending files se existir.
4. Repete validacoes e envio final.

## 11.4 Fluxo publico por link
1. Usuario acessa `/adesao/:token`.
2. Resolve token (`cadastro-link-resolve`).
3. Consulta CPF (`cadastro-link-check-cpf`) antes de liberar formulario.
4. Preenche formulario publico + dependentes.
5. Submit para `cadastro-public-submit`.
6. Backend revalida tudo, cria `cadastros`, envia ERP, sincroniza status enviado e marca uso do link.

## 11.5 Fila de upload
1. Item e enfileirado em `erp_upload_queue` quando upload direto falha.
2. Cron e/ou acao manual disparam `erp-process-upload-queue`.
3. Worker processa em background, atualiza status e aplica retry.
4. Tela admin acompanha via realtime.

---

## 12. Logs, monitoramento e depuracao

## 12.1 Onde investigar erros
- Frontend console logs (ha muitos logs detalhados em hooks/modais)
- Tabela `api_logs` para chamadas de edge/integracao
- Tabela `erp_upload_queue` para falhas de upload
- `cadastros.erp_response` e `cadastros.payload_erp` para historico de envio

## 12.2 Endpoints com logging explicito em `api_logs`
- praticamente todos os endpoints ERP/Lemmit e submit publico
- permite filtrar por sucesso/erro, periodo e detalhes request/response (`ApiLogsTable`)

## 12.3 Comportamentos de resiliencia importantes
- Idempotencia em `erp-novo-usuario2` por header e reuso de resposta em `cadastros`/`api_logs`
- Reconciliacao apos abort no frontend (`useCadastros`)
- Fila com retry e reset de stuck
- Drafts locais para evitar perda de formulario em mobile

---

## 13. Riscos e pontos de atencao para continuidade

1. `VisualizarArquivoModal` sem uso atual: decidir remover ou integrar.
2. Possivel sobreposicao de debito Lemmit entre frontend e edge: revisar politica unica de cobranca.
3. Muitos logs `console.*` em producao no frontend; avaliar reduzir ruido apos estabilizacao.
4. Existem arquivos legados/backup (`draftStorage.ts.old`) e migracoes historicas extensas; evitar usar arquivo antigo por engano.
5. Consolidar roles legadas (`GESTOR`) vs atuais (`GERENTE`) no type system e regras SQL.
6. Confirmar em ambiente real se todos segredos Edge estao preenchidos (especialmente `ERP_*` e `LEMMIT_API_KEY`).

---

## 14. Checklist para novo dev (primeiros dias)

1. Validar `.env` local e acesso ao projeto Supabase correto.
2. Rodar app (`npm install`, `npm run dev`) e validar login.
3. Testar rotas principais com dois perfis: admin e vendedor.
4. Executar fluxo completo de:
   - nova adesao
   - inclusao de dependente
   - link publico
5. Conferir `api_logs` durante testes para entender payloads reais.
6. Revisar `useCadastros.ts` e `PublicCadastroLink.tsx` como fontes centrais de regra de negocio.
7. Revisar edge functions em `supabase/functions/*` antes de alterar contrato de payload.
8. Auditar tabela `erp_upload_queue` e comportamento do worker com item de teste.
9. Mapear pendencias funcionais com o time de negocio (regras de bloqueio ERP, planos validos, status de adesao).

---

## 15. Arquivos nucleares para leitura inicial

Frontend core:
- `src/App.tsx`
- `src/contexts/AuthContext.tsx`
- `src/contexts/ConfigCadastroContext.tsx`
- `src/hooks/useCadastros.ts`
- `src/pages/Cadastro.tsx`
- `src/pages/PublicCadastroLink.tsx`
- `src/components/cadastro/NovoCadastroCard.tsx`
- `src/components/cadastro/CadastroModal.tsx`
- `src/components/cadastro/InclusaoDependenteModal.tsx`
- `src/components/cadastro/ContinuarInclusaoDependenteModal.tsx`
- `src/components/cadastro/DependentesSection.tsx`
- `src/pages/FilaUploadERP.tsx`

Backend/edge core:
- `supabase/functions/erp-novo-usuario2/index.ts`
- `supabase/functions/erp-novo-dependente/index.ts`
- `supabase/functions/erp-upload-documento/index.ts`
- `supabase/functions/erp-enqueue-upload/index.ts`
- `supabase/functions/erp-process-upload-queue/index.ts`
- `supabase/functions/cadastro-link-resolve/index.ts`
- `supabase/functions/cadastro-link-check-cpf/index.ts`
- `supabase/functions/cadastro-public-submit/index.ts`
- `supabase/functions/lemit-consulta-pessoa/index.ts`
- `supabase/functions/create-user/index.ts`
- `supabase/functions/excluir-cadastro/index.ts`

Migrations chave:
- `20260203165153_create_erp_upload_queue.sql`
- `20260204133345_add_erp_upload_queue_cron.sql`
- `20260204134253_add_reset_stuck_queue_items_function.sql`
- `20260320150000_add_cadastro_links_public_flow.sql`
- `20260323123000_add_check_public_link_blocked_cpf_function.sql`
- `20260323133000_add_click_metrics_to_cadastro_links.sql`

---

## 16. Contratos de endpoint (resumo pratico de payload)

## 16.1 `erp-check-associado`
- Entrada:
  - `cpf` (string) ou `codigoAssociado` (string/number)
- Saida principal:
  - `exists` (boolean)
  - `shouldBlock` (boolean)
  - `blockReason` (string)
  - `dados` (array ERP bruto)
  - `summary` (empresa/codigo/situacao/plano)

## 16.2 `erp-search-empresa`
- Entrada:
  - um dos campos: `cnpj`, `nome`, `empresaId`
- Saida:
  - `ok`
  - `empresas[]` com:
    - `id`, `codigo`, `razaoSocial`, `nomeFantasia`, `cnpj`
    - `codigoSituacao`
    - `enderecoEmpresa`
    - `exigeMatricula`
    - `observacoes`
    - `precoPlano`
    - `raw` (payload ERP original)

## 16.3 `erp-endereco-cep`
- Entrada:
  - `cep`
- Saida:
  - `ok`
  - `dados` com mapeamento de logradouro/bairro/municipio/uf + `UfSigla`

## 16.4 `erp-novo-usuario2`
- Entrada:
  - payload ERP completo em `dados`
  - headers importantes:
    - `X-Idempotency-Key`
    - `X-Cadastro-Id`
- Saida sucesso:
  - `success: true`
  - `data` (resposta ERP)
- Saida erro:
  - `error`
  - `details`
  - `status`

## 16.5 `erp-novo-dependente`
- Entrada:
  - `dados.responsavelFinanceiro` obrigatorio
  - `dados.parceiro.codigo` obrigatorio
- Saida:
  - `success: true` + `data`
  - ou `error/details/status`

## 16.6 `erp-upload-documento`
- Entrada:
  - obrigatorios: `idFuncionario`, `idDependente`
  - arquivo por uma das estrategias:
    - `arquivo` + `arquivoNome` (base64 direto)
    - `arquivoPath` (+ opcional `bucket`) para download no Supabase e reenvio ao ERP
- Saida:
  - `success: true` + `data` ou erro detalhado

## 16.7 `erp-enqueue-upload`
- Entrada:
  - `cadastroId` (pode ser null em alguns fluxos)
  - `idFuncionario`, `idDependente`
  - `arquivoPath`, `arquivoNome`
  - `tipo` (`titular` ou `dependente`)
  - opcional `bucket`
- Saida:
  - `queued: true`
  - `queue_id`

## 16.8 `erp-process-upload-queue`
- Entrada:
  - sem payload obrigatorio
- Saida:
  - 200 quando sem itens elegiveis
  - 202 quando processamento em background iniciado
  - inclui `queued_count` e `estimated_time_seconds`

## 16.9 `create-user`
- Entrada:
  - `name`, `email`, `password`, `role`
  - para roles operacionais: `external_id`, `team_id`
- Saida:
  - `success: true` + `user` (profile criado)
  - ou erro de validacao/permissao

## 16.10 `excluir-cadastro`
- Entrada:
  - `cadastroId`
  - `motivoExclusao`
- Saida:
  - sucesso: remove de `cadastros` e registra em `cadastros_excluidos`

## 16.11 `cadastro-link-resolve`
- Entrada:
  - `token` (texto puro do link)
- Saida:
  - `ok`
  - `link` com dados de empresa, planos, vendedor e telefone
  - incrementa `click_count` e `last_clicked_at`

## 16.12 `cadastro-link-check-cpf`
- Entrada:
  - `token`
  - `cpf`
- Saida:
  - `ok: true` quando permitido
  - 409 com `code` em casos de bloqueio/reuso

## 16.13 `cadastro-public-submit`
- Entrada:
  - `token`
  - `cadastro` com:
    - dados pessoais
    - contatos
    - endereco
    - dependentes
    - matricula (quando exigida)
- Saida:
  - sucesso: `ok`, `cadastroId`, `message`
  - pode retornar `warning` se ERP concluiu mas houve falha de sync parcial
  - erro: `error` + `details` em cenarios de rejeicao ERP

## 16.14 `lemit-consulta-pessoa`
- Entrada:
  - `cpf`
- Saida:
  - sucesso com payload pessoa Lemmit
  - erros controlados para `notFound`, `invalidCPF`, `workflowError`, `empty`
  - edge registra custo no log e debita saldo quando aplicavel
---

FIM
