from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "android-app/app/src/main/java/br/com/vendamais/mobile/ui/AppViewModel.kt"
text = path.read_text(encoding="utf-8-sig")
actual = '''    suspend fun updateCadastroConfig(payload: JsonObject): CadastroConfig {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val config = repository.updateCadastroConfig(activeSession, payload)
        _uiState.update {
            it.copy(
                cadastroWorkspace = it.cadastroWorkspace.copy(config = config),
            )
        }
        return config
    }
'''
normalized = '''    suspend fun updateCadastroConfig(payload: JsonObject): CadastroConfig {
        val session = currentSession ?: throw IllegalStateException("Sessao nao encontrada.")
        val activeSession = ensureFreshSession(session)
        val updated = repository.updateCadastroConfig(activeSession, payload)
        _uiState.update {
            it.copy(cadastroWorkspace = it.cadastroWorkspace.copy(config = updated))
        }
        return updated
    }
'''
if normalized not in text:
    if actual not in text:
        raise RuntimeError("updateCadastroConfig shape not found")
    text = text.replace(actual, normalized, 1)
path.write_text(text, encoding="utf-8")
print("Stage 2 pre-normalization applied")
