# Specs Tecnicas - Handoff Android (Paridade Web)

## 1) Objetivo deste documento
Este arquivo foi montado para transferir o estado atual do app Android com foco nos mesmos 3 objetivos do documento web:
1. Onde cada modal/dialog e chamado.
2. Onde cada regra de negocio e aplicada.
3. Onde existe cada chamada de API.

Data da analise: 2026-04-20.
Escopo principal: `android-app/app/src/main/java/br/com/vendamais/mobile/`.

## 2) Mapa de modais (definicao, call site e gatilho)

| Modal/Dialog | Definicao | Onde e chamado | Gatilho principal |
|---|---|---|---|
| `ObservacoesEmpresa` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:16`, render em `ui/screens/CadastroOverlayDialogs.kt:27` | `ui/AppViewModel.kt:376`, `ui/screens/InclusaoDependenteDialog.kt:853` | Empresa selecionada com observacoes configuradas. |
| `EmpresaCancelada` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:21`, render em `ui/screens/CadastroOverlayDialogs.kt:45` | `ui/AppViewModel.kt:353`, `ui/AppViewModel.kt:702`, `ui/screens/InclusaoDependenteDialog.kt:835` | `codigoSituacao` da empresa bloqueado por config (`codigosEmpresaInvalidos`). |
| `EmpresaNaoIdentificada` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:12`, render em `ui/screens/CadastroOverlayDialogs.kt:60` | `ui/screens/InclusaoDependenteDialog.kt:435`, `ui/screens/InclusaoDependenteDialog.kt:864`, `ui/screens/InclusaoDependenteDialog.kt:873` | Inclusao de dependente sem empresa valida identificada. |
| `LemmitLimit` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:25`, render em `ui/screens/CadastroOverlayDialogs.kt:81` | `ui/AppViewModel.kt:800`, `ui/screens/InclusaoDependenteDialog.kt:460` | Limite/permissao de consulta Lemmit indisponivel. |
| `LemmitError` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:32`, render em `ui/screens/CadastroOverlayDialogs.kt:101` | `ui/AppViewModel.kt:806` | Warning/falha de consulta Lemmit sem bloqueio duro. |
| `SelectStatus` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:36`, render em `ui/screens/CadastroOverlayDialogs.kt:276` | `ui/screens/CadastroEditorDialog.kt:1541`, `ui/screens/CadastroEditorDialog.kt:1548`, `ui/screens/InclusaoDependenteDialog.kt:414`, `ui/screens/InclusaoDependenteDialog.kt:421` | Fluxo tenta concluir sem status de adesao definido. |
| `ParceiroInvalido` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:38`, render em `ui/screens/CadastroOverlayDialogs.kt:114` | `ui/AppViewModel.kt:897`, `ui/AppViewModel.kt:1200`, `ui/AppViewModel.kt:1302`, `ui/screens/InclusaoDependenteDialog.kt:1231` | Mapeamento de erro ERP de parceiro invalido (`CadastroApiErrorMapper`). |
| `DependenteAtivo` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:42`, render em `ui/screens/CadastroOverlayDialogs.kt:160` | `ui/AppViewModel.kt:897`, `ui/AppViewModel.kt:1200`, `ui/AppViewModel.kt:1302`, `ui/screens/InclusaoDependenteDialog.kt:1231` | Mapeamento de erro ERP de dependente ativo. |
| `ExcluirCadastro` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:46`, render em `ui/screens/CadastroOverlayDialogs.kt:180` | `ui/screens/CadastrosScreen.kt:1280`, `ui/screens/CadastroEditorDialog.kt:1441` | Usuario solicita excluir adesao pendente. |
| `AlreadyExists` (overlay) | `domain/cadastro/CadastroModalStateMachine.kt:51`, render em `ui/screens/CadastroOverlayDialogs.kt:216` | `ui/AppViewModel.kt:860` | CPF ja existe no fluxo de criacao de rascunho. |
| `Cadastro pendente encontrado` | `ui/VendaMaisApp.kt:362` | `ui/AppViewModel.kt:813`, `ui/AppViewModel.kt:851` | `CadastroExistenteException` com opcao de continuar/reiniciar pendencia. |
| `CadastroEditorDialog` | `ui/screens/CadastroEditorDialog.kt:165` | `ui/VendaMaisApp.kt:418` | Abrir cadastro do tipo `cadastro`. |
| `InclusaoDependenteDialog` | `ui/screens/InclusaoDependenteDialog.kt:156` | `ui/screens/CadastrosScreen.kt:482`, `ui/VendaMaisApp.kt:425` | Iniciar inclusao de dependente ou continuar pendencia `inclusao_dependente`. |
| `CadastroDetailDialog` | `ui/screens/CadastrosScreen.kt:1178` | `ui/VendaMaisApp.kt:437` | Visualizacao de cadastro nao editavel no fluxo atual. |
| `LinkQrCodeDialog` | `ui/screens/CadastroLinksCard.kt:443` | `ui/screens/CadastroLinksCard.kt:223` | Usuario abre QR Code de link gerado. |
| `UserEditorDialog` | `ui/screens/UsersScreen.kt:226` | `ui/screens/UsersScreen.kt:170`, `ui/screens/UsersScreen.kt:197` | Criacao/edicao de usuario. |
| `TeamEditorDialog` | `ui/screens/TeamsScreen.kt:199` | `ui/screens/TeamsScreen.kt:142`, `ui/screens/TeamsScreen.kt:156` | Criacao/edicao de equipe e membros. |
| `StatsByVendedorDialog` | `ui/screens/DashboardScreen.kt:329` | `ui/screens/DashboardScreen.kt:167` | Drilldown de metricas do dashboard por vendedor. |
| `PublicAdesaoTokenScreen` (fullscreen publico) | `ui/screens/PublicAdesaoTokenScreen.kt:89` | `ui/VendaMaisApp.kt:137` | App aberto com token publico (`deepLinkToken`). |
| `LinkQr`, `LinkAssociados`, `VisualizarArquivo` (overlay intents) | `domain/cadastro/CadastroModalStateMachine.kt:56`, `:61`, `:66` + render `ui/screens/CadastroOverlayDialogs.kt:229`, `:242`, `:263` | Nenhum call site ativo encontrado por `rg` no Android atual | Intents definidos para paridade de arquitetura; sem disparo no snapshot atual. |

## 3) Regras de negocio (onde estao e o que fazem)

### 3.1 Regras de status da adesao

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Status obrigatorio para envio de cadastro | `ui/screens/CadastroEditorDialog.kt:1540-1552`, `data/remote/CadastroWorkflowRepository.kt:937-939` | UI bloqueia envio sem status; repositorio tambem valida antes do ERP. |
| Status obrigatorio na inclusao de dependente | `ui/screens/InclusaoDependenteDialog.kt:412-426`, `:595` | Sem status, abre overlay `SelectStatus` e interrompe salvar/enviar. |
| Fechamento com status no editor | `ui/screens/CadastroEditorDialog.kt:551-554`, `:1634-1661` | Ao fechar sem status (quando ha lista de status), pede selecao e salva antes de fechar. |
| Conjunto de status pendentes centralizado | `domain/cadastro/CadastroStatusRules.kt:5-15` | Define status pendente aceito e query `in.(...)` usada para deduplicacao. |

### 3.2 Regras de empresa e matricula

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Empresa bloqueada por situacao nao pode seguir | `ui/AppViewModel.kt:349-359`, `:698-708`, `ui/screens/InclusaoDependenteDialog.kt:830-843` | Dispara overlay `EmpresaCancelada` e impede continuidade. |
| Observacoes de empresa sao checkpoint | `ui/AppViewModel.kt:372-383`, `ui/screens/InclusaoDependenteDialog.kt:850-860` | Dispara overlay `ObservacoesEmpresa` apos selecao. |
| Empresa precisa estar identificada na inclusao | `ui/screens/InclusaoDependenteDialog.kt:430-443` | Sem codigo/nome validos, abre `EmpresaNaoIdentificada` e bloqueia fluxo. |
| Matricula obrigatoria por empresa | `ui/screens/CadastroEditorDialog.kt:625-627`, `ui/screens/PublicAdesaoTokenScreen.kt:449-455`, `:542-544`, `data/remote/CadastroWorkflowRepository.kt:940-942` | Valida condicionalmente quando `empresaExigeMatricula == 1`. |

### 3.3 Regras de dependentes

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Dependente exige nome, nascimento, sexo, parentesco, plano e nome da mae | `ui/screens/InclusaoDependenteDialog.kt:382-394`, `ui/screens/CadastroEditorDialog.kt:638-659` | Bloqueia salvar/enviar enquanto houver campos obrigatorios faltando. |
| CPF obrigatorio para maior de 18 anos | `ui/screens/InclusaoDependenteDialog.kt:386`, `ui/screens/CadastroEditorDialog.kt:647-649` | CPF condicional por idade. |
| CPF validado por DV (titular e dependentes) | `data/remote/CadastroPayloadBuilder.kt:35-49`, `ui/screens/InclusaoDependenteDialog.kt:387`, `ui/screens/CadastroEditorDialog.kt:645-651` | Impede envio com CPF invalido. |
| Deve existir titular consistente no payload | `ui/screens/CadastroEditorDialog.kt:635-637`, `ui/screens/PublicAdesaoTokenScreen.kt:581-585` | Editor exige titular no indice base; fluxo publico exige exatamente 1 titular. |
| Todo dependente precisa de plano | `ui/screens/InclusaoDependenteDialog.kt:389`, `ui/screens/CadastroEditorDialog.kt:657`, `data/remote/CadastroWorkflowRepository.kt:947-951` | Valida plano local e no repositorio antes do envio ERP. |
| Planos ocultos por configuracao | `ui/screens/CadastroEditorDialog.kt:248-253`, `:1960-1978`; `ui/screens/InclusaoDependenteDialog.kt:691-696`, `:1737-1784` | Remove planos ocultos da lista de selecao. |

### 3.4 Regras de arquivo/upload

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Arquivo obrigatorio quando configurado | `ui/screens/InclusaoDependenteDialog.kt:392-393`, `data/remote/CadastroWorkflowRepository.kt:953-956` | Sem arquivo, bloqueia fluxo de dependente e envio final do cadastro. |
| Validacao de tipo/tamanho local (PDF/JPG/PNG ate 10MB) | `ui/screens/CadastroEditorDialog.kt:109`, `:2305-2309`; `ui/screens/InclusaoDependenteDialog.kt:110`, `:1883-1887` | Rejeita arquivo fora dos formatos aceitos ou acima do limite. |
| Upload direto com fallback de fila | `data/remote/CadastroWorkflowRepository.kt:973-1004`, `ui/screens/InclusaoDependenteDialog.kt:662-668` | Tenta `erp-upload-documento`; se falhar, enfileira em `erp-enqueue-upload`. |

### 3.5 Regras de parceiro/vendedor

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Parceiro invalido mapeado para overlay de recuperacao | `domain/cadastro/CadastroApiErrorMapper.kt:4-20`, `ui/AppViewModel.kt:1198-1202`, `:1300-1303` | Erro ERP dispara `ParceiroInvalido` com opcao de reenvio. |
| Retry com vendedor informado manualmente | `ui/screens/CadastroOverlayDialogs.kt:148-153`, `ui/AppViewModel.kt:1215-1315` | Usuario informa codigo/nome e tenta reenviar cadastro selecionado. |
| Codigo de parceiro numerico valido na inclusao dependente | `ui/screens/InclusaoDependenteDialog.kt:602-609`, `:646-654` | Sem codigo externo valido, bloqueia envio ERP. |

### 3.6 Regras de CPF, bloqueios e duplicidade

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Consulta inicial de CPF combina local + ERP + cliente anterior | `data/remote/CadastroWorkflowRepository.kt:255-277`, `:268-273`, `:351-360` | Consolida dados e bloqueia quando ERP retorna bloqueio. |
| Duplicidade de rascunho tratada por constraint | `data/remote/CadastroWorkflowRepository.kt:66-72`, `:651-686`, `:806-838`, `:1120-1137` | Em conflito, reconcilia para pendente existente em vez de falhar seco. |
| Mensagem amigavel para duplicidade | `domain/cadastro/CadastroApiErrorMapper.kt:24-39` | Traduz erro tecnico (`23505`, unique constraint) para mensagem funcional. |
| Fluxo publico exige consulta/lock de CPF antes de concluir | `ui/screens/PublicAdesaoTokenScreen.kt:367-404`, `:506-509`, `:621-628` | CPF precisa ser validado no link e revalidado no submit. |

### 3.7 Regras Lemmit

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Lemmit pode ser desativada por config | `data/remote/CadastroWorkflowRepository.kt:316` | Se desativada, fluxo segue sem enriquecimento. |
| Gate de uso e limite | `data/remote/CadastroWorkflowRepository.kt:318`, `:337-347`, `:1187-1204`; `ui/screens/InclusaoDependenteDialog.kt:451-460` | Verifica permissao/saldo e abre overlay de limite quando necessario. |
| Retry controlado em falha de consulta | `ui/screens/InclusaoDependenteDialog.kt:465-503`, `:511-528` | Ate 3 tentativas com timeout/backoff e mensagem de retry para usuario. |

### 3.8 Regras de resiliencia e consistencia

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Autosave de rascunho no editor | `ui/screens/CadastroEditorDialog.kt:871-880`, `:882-896`, `ui/AppViewModel.kt:1805-1827` | Persiste silenciosamente em debounce e `ON_STOP`. |
| Idempotencia no envio ao ERP (cadastro interno) | `data/remote/CadastroWorkflowRepository.kt:1017-1019` | Usa `X-Idempotency-Key` no `erp-novo-usuario2`. |
| Fluxo publico com draft resiliente (24h + versao) | `ui/screens/PublicAdesaoTokenScreen.kt:51-53`, `:173-213`, `:217-289` | Salva/restaura por token em `SharedPreferences` com TTL de 24h. |
| Fluxo publico com idempotencia de submit | `ui/screens/PublicAdesaoTokenScreen.kt:562-563`, `:630-631`; `data/remote/CadastroWorkflowRepository.kt:1373-1390` | Gera `submissionId` e envia `X-Idempotency-Key` dedicado para link publico. |

### 3.9 Regras de permissao e exclusao

| Regra | Onde aplica | Comportamento |
|---|---|---|
| Exclusao exige motivo | `ui/screens/CadastroOverlayDialogs.kt:180-214`, `data/remote/CadastroWorkflowRepository.kt:869` | Overlay coleta motivo e chama edge function `excluir-cadastro`. |
| Escopo de criacao/edicao de usuario por role | `ui/screens/UsersScreen.kt:226` + backend `functions/v1/create-user` via `data/remote/SupabaseRepository.kt:278` | UI monta payload; backend aplica regra final de role/equipe. |

## 4) Catalogo de chamadas de API

## 4.1 Android -> Edge Functions (`/functions/v1/*`)

| Endpoint | Chamado em | Finalidade |
|---|---|---|
| `erp-search-empresa` | `data/remote/CadastroWorkflowRepository.kt:232` | Busca de empresa por codigo/CNPJ/nome. |
| `erp-check-associado` | `data/remote/CadastroWorkflowRepository.kt:1240`, `:1395` | Consulta CPF/codigo no ERP para bloqueio e dados de responsavel. |
| `lemit-consulta-pessoa` | `data/remote/CadastroWorkflowRepository.kt:1209` | Enriquecimento Lemmit. |
| `erp-endereco-cep` | `data/remote/CadastroWorkflowRepository.kt:1219` | Enriquecimento de endereco por CEP. |
| `erp-novo-usuario2` | `data/remote/CadastroWorkflowRepository.kt:1013` | Envio de adesao titular ao ERP. |
| `erp-novo-dependente` | `data/remote/CadastroWorkflowRepository.kt:1344` | Envio de inclusao de dependente ao ERP. |
| `erp-upload-documento` | `data/remote/CadastroWorkflowRepository.kt:975`, `:1287` | Upload imediato de documento no ERP. |
| `erp-enqueue-upload` | `data/remote/CadastroWorkflowRepository.kt:991`, `:1317` | Fallback para fila de upload ERP. |
| `erp-process-upload-queue` | `data/remote/SupabaseRepository.kt:240` | Processamento da fila de upload. |
| `create-user` | `data/remote/SupabaseRepository.kt:278` | Criacao de usuario administrativo. |
| `excluir-cadastro` | `data/remote/CadastroWorkflowRepository.kt:869` | Exclusao logica com motivo. |
| `cadastro-link-resolve` | `data/remote/CadastroWorkflowRepository.kt:1354` | Resolve token publico e metadados do link. |
| `cadastro-link-check-cpf` | `data/remote/CadastroWorkflowRepository.kt:1364` | Valida CPF no contexto do link publico. |
| `cadastro-public-submit` | `data/remote/CadastroWorkflowRepository.kt:1379` | Envio final do formulario publico. |

## 4.2 Android -> RPCs (`rest/v1/rpc/*`)

| RPC | Chamado em | Uso |
|---|---|---|
| `get_cadastros_stats` | `data/remote/SupabaseRepository.kt:94` | Estatisticas de cadastro. |
| `get_stats_from_cache` | `data/remote/SupabaseRepository.kt:102` | Estatisticas em cache para dashboard. |
| `get_stats_by_vendedor` | `data/remote/SupabaseRepository.kt:157` | Drilldown por vendedor. |
| `audit_lemmit` | `data/remote/SupabaseRepository.kt:201` | Auditoria Lemmit. |
| `reset_stuck_queue_items` | `data/remote/SupabaseRepository.kt:250` | Reset de itens travados da fila de upload. |
| `check_cpf_existente` | `data/remote/CadastroWorkflowRepository.kt:1176` | Verifica duplicidade de CPF no fluxo interno. |
| `can_use_lemmit` | `data/remote/CadastroWorkflowRepository.kt:1189` | Gate de permissao/limite Lemmit. |
| `get_lemmit_limit_info` | `data/remote/CadastroWorkflowRepository.kt:1199` | Detalhes de limite/saldo Lemmit. |

## 4.3 Android -> Tabelas Supabase (`rest/v1/<table>`)

Tabelas com uso direto identificado:

- `cadastros`: leitura/escrita de rascunho, envio e sync em `data/remote/CadastroWorkflowRepository.kt:704`, `:778`, `:798`, `:1054`, `:1093`, `:1112`.
- `cadastro_links`: gerar/regerar/excluir links em `data/remote/CadastroWorkflowRepository.kt:174`, `:198`, `:211`.
- `cadastro_config`, `cadastro_planos_map`, `cadastro_parentesco_map`, `status_adesoes`, `profiles`: carga de apoio em `data/remote/CadastroWorkflowRepository.kt` (metodos `fetch*` no topo do arquivo).
- `profiles`, `teams`, `cadastros`, `erp_upload_queue`, `cadastros_excluidos`: listagens administrativas em `data/remote/SupabaseRepository.kt`.

## 4.4 Android -> Storage Supabase

- Bucket principal: `cadastros-temp-files`.
- Upload/download/delete:
  - `data/remote/CadastroWorkflowRepository.kt:894-902` (`PUT /storage/v1/object/...`)
  - `data/remote/CadastroWorkflowRepository.kt:913-918` (`DELETE /storage/v1/object/...`)
  - `data/remote/CadastroWorkflowRepository.kt:924-930` (`GET /storage/v1/object/...`)
- Prefixos relevantes: `dependentes-temp/<cpf>` e `dependentes-continuar/<cpf>` em `ui/screens/InclusaoDependenteDialog.kt:242`.

## 4.5 Realtime no Android

Nenhum canal realtime Supabase ativo foi encontrado no snapshot atual do Android (sem `channel().subscribe()` no app).

## 5) Fluxos criticos para continuidade

Fluxo `Nova adesao (interno)`:
1. Usuario consulta CPF e cria/retoma rascunho (`AppViewModel.consultarCpfParaCadastro` -> `CadastroWorkflowRepository.createDraftFromCpf`).
2. Editor (`CadastroEditorDialog`) valida campos, contatos/dependentes/status.
3. Envio final usa `erp-novo-usuario2`, sincroniza `cadastros` e faz upload de arquivo com fallback de fila.

Fluxo `Inclusao de dependente`:
1. Inicia por `InclusaoDependenteDialog` (novo ou continuacao).
2. Resolve responsavel/empresa, valida status e dependentes.
3. Envia `erp-novo-dependente`, processa anexos (`erp-upload-documento` ou `erp-enqueue-upload`) e atualiza cadastro para `enviado`.

Fluxo `Link publico`:
1. App recebe `deepLinkToken` e abre `PublicAdesaoTokenScreen` (`ui/VendaMaisApp.kt:129-143`).
2. Resolve token (`cadastro-link-resolve`), consulta CPF (`cadastro-link-check-cpf`) e trava CPF.
3. Aplica validacoes locais de paridade (dados obrigatorios, plano, titular unico, matricula condicional, endereco).
4. Salva draft local com TTL de 24h.
5. Envia `cadastro-public-submit` com chave de idempotencia.

## 6) Pontos de atencao para quem assumir

- Overlay intents `LinkQr`, `LinkAssociados` e `VisualizarArquivo` existem na maquina de estados, mas sem call site ativo no Android atual.
- Fluxo de upload deve manter caminho direto + fila para preservar resiliencia operacional.
- Validacoes de `cadastro-public-submit` no backend continuam sendo a ultima barreira de integridade; nao depender so da UI.
- O fluxo publico Android ja foi ajustado para paridade comportamental com web nos pontos criticos: gate de CPF, draft resiliente, validacao de payload e idempotencia de submit.

## 7) Referencias principais por arquivo

- `android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/PublicAdesaoTokenScreen.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastroEditorDialog.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/InclusaoDependenteDialog.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/ui/screens/CadastroOverlayDialogs.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/domain/cadastro/CadastroModalStateMachine.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/domain/cadastro/CadastroApiErrorMapper.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/domain/cadastro/CadastroStatusRules.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/ui/AppViewModel.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/CadastroWorkflowRepository.kt`
- `android-app/app/src/main/java/br/com/vendamais/mobile/data/remote/SupabaseRepository.kt`
