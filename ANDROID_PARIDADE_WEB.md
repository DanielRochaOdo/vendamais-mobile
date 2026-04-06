# Paridade Web -> Android (Cadastro)

Documento de contrato para o app Android nativo, consolidado a partir dos arquivos de regra do repositório web (fora de `android-app`) e do código Android.

## Fontes de verdade usadas
- `FLUXO_CADASTRO_ATUALIZADO.md`
- `REGRAS_CADASTRO_USUARIO.md`
- `CORRECOES_VENDEDOR_ID.md`
- `src/components/cadastro/*` (fluxo real em produção web)
- `src/components/cadastro/cadastroOverlayStateMachine.ts`

## Matriz de paridade funcional

| Regra/fluxo | Web (referência) | Android (implementação) | Status |
|---|---|---|---|
| Validar CPF antes de consulta | `NovoCadastroCard.tsx` | `AppViewModel.createDraftFromCpf` + `CadastroPayloadBuilder.validateCpf` | OK |
| Duplicidade local (RPC) | `useCadastros.ts` + fluxo web | `CadastroWorkflowRepository.checkCpfExistente` | OK |
| Bloqueio por já existir no ERP | `erp-check-associado` no fluxo web | `CadastroWorkflowRepository.checkErpAssociado` | OK |
| Seleção de empresa obrigatória antes de consultar CPF | `NovoCadastroCard.tsx` | `AppViewModel.createDraftFromCpf` | OK |
| Observações da empresa só na seleção de empresa | `EmpresaSearchCard.tsx` | `AppViewModel.selectEmpresa` + `CadastroOverlayDialogs.ObservacoesEmpresa` | OK |
| Empresa cancelada bloqueia avanço | `EmpresaSearchCard.tsx` / `NovoCadastroCard.tsx` | `AppViewModel.createDraftFromCpf` + overlay `EmpresaCancelada` | OK |
| Lemmit com limite/aviso | `NovoCadastroCard.tsx` + `LemmitLimitModal.tsx` | `CadastroWorkflowRepository.createDraftFromCpf` + overlay `LemmitLimit` | OK |
| Vendedor obrigatório no fluxo de venda | `NovoCadastroCard.tsx` (regra funcional) | `CadastroOperationsCard` + `CadastroEditorDialog` + `AppViewModel.createDraftFromCpf` | OK |
| Persistência de rascunho no editor | `draftStore.ts` / fluxo web | `CadastroEditorDialog` (autosave em background + debounce) + `AppViewModel.persistCadastroDraftSilently` | OK |
| Fluxo de etapas 1 -> 2 com `Cadastrar` só na etapa final | `CadastroModal.tsx` | `CadastroEditorDialog.currentStep` e rodapé condicional | OK |
| Fechar com status condicional | `CadastroModal.tsx` + `SelectStatusModal.tsx` | `CadastroEditorDialog` (`showSelectStatusOnClose`) | OK |
| Envio principal com idempotência | `erp-novo-usuario2` + headers web | `CadastroWorkflowRepository.enviarParaErp` (`X-Idempotency-Key`, `X-Cadastro-Id`) | OK |
| Parceiro inválido / dependente ativo | `CadastroModal.tsx`, `InclusaoDependenteModal.tsx` | `CadastroApiErrorMapper` + `CadastroOverlayDialogs` | OK |
| Inclusão de dependente com empresa obrigatória | `InclusaoDependenteModal.tsx` / `ContinuarInclusaoDependenteModal.tsx` | `InclusaoDependenteDialog.ensureEmpresaIdentificada` + overlay `EmpresaNaoIdentificada` | OK |
| Upload de documento com fallback para fila | `erp-upload-documento` + `erp-enqueue-upload` | `CadastroWorkflowRepository.processDocumentoUpload` e `InclusaoDependenteDialog` | OK |
| Exclusão lógica de cadastro | `excluir-cadastro` | `AppViewModel.deleteCadastroByOverlay` + `CadastroWorkflowRepository.deleteCadastroLogico` | OK |

## Ordem de prioridade de overlays
Android mantém a mesma prioridade do resolver central:
1. EntryPoint
2. EmpresaNaoIdentificada
3. ObservacoesEmpresa
4. EmpresaCancelada
5. LemmitLimit
6. SelectStatus
7. ParceiroInvalido / DependenteAtivo
8. ExcluirCadastro
9. AlreadyExists
10. LinkQr
11. LinkAssociados
12. VisualizarArquivo

Referência Android: `android-app/app/src/main/java/br/com/vendamais/mobile/domain/cadastro/CadastroModalStateMachine.kt`.

## Observações de implementação
- Regras de role foram normalizadas para suportar aliases (`ADMIN`/`ADMINISTRADOR`, `GERENTE`/`GESTOR`) nos pontos críticos do fluxo de cadastro.
- Mensagens técnicas de erro do backend foram mapeadas para mensagens amigáveis no fluxo de cadastro.
- A persistência do editor prioriza não perder progresso em minimização/troca de contexto.
