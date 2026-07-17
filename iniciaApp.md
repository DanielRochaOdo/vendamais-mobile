# Fluxo de desenvolvimento Android

Para trabalhar em Kotlin/Compose sem gerar APK a cada mudança, use o app em `debug` no emulador.

## Iniciar o ambiente

```powershell
.\iniciar-app.ps1
```

Esse comando:

- inicia o emulador `VendaMais_API_35` se ele ainda nao estiver aberto
- instala o app com `:app:installDebug`
- abre a `MainActivity`

## Atualizacao em tempo real

No Android Studio:

- deixe o app rodando em `debug`
- use `Apply Changes` para mudancas simples
- use `Live Edit` para telas em Jetpack Compose

## Quando ainda vai precisar rebuildar

- alteracao em `build.gradle.kts`
- mudanca de dependencias
- mudanca em recursos nativos ou manifesto
- mudanca estrutural que force restart do processo

## Dica pratica

Se quiser limpar o emulador antes de abrir:

```powershell
.\iniciar-app.ps1 -CleanEmulator
```
