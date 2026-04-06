# Android App

Aplicacao Android nativa em Kotlin + Jetpack Compose para substituir gradualmente o app web.

## Estado atual

- Login nativo com Supabase Auth.
- Sessao persistida com refresh de token.
- Dashboard nativo com indicadores do mes e drill-down por vendedor.
- Modulo de cadastros nativo com listagem, filtro e detalhe.
- Perfil do usuario e contexto do app.
- Build debug validado localmente.

## O que ainda depende de configuracao

Preencha `android-app/local.properties` com:

- `sdk.dir`
- `supabaseUrl`
- `supabaseAnonKey`
- `publicAppUrl`

Sem `supabaseUrl` e `supabaseAnonKey`, o app abre mas nao autentica.

## Build

Opcao 1:

```powershell
cd android-app
.\build-debug.ps1
```

Opcao 2:

```powershell
cd android-app
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

APK gerado em:

`android-app/app/build/outputs/apk/debug/app-debug.apk`

## Estrategia de migracao

1. Manter autenticacao, dashboard e operacao principal de cadastros em Compose.
2. Reaproveitar RPCs e Edge Functions ja existentes no Supabase.
3. Evoluir criacao/edicao completa de cadastro e uploads em telas nativas incrementais.
