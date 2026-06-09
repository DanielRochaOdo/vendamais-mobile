# Play Store Checklist

## Build

- Gerar `bundleRelease`, nao `assembleRelease`.
- Confirmar que o arquivo final e `app-release.aab`.
- Validar a assinatura com a keystore de producao.

## Versao

- Incrementar `VERSION_CODE` em cada envio.
- Manter `VERSION_NAME` coerente com o changelog.

## Console do Google Play

- Informar politica de privacidade publica.
- Preencher a ficha de Data Safety.
- Declarar o uso de conta, login e dados coletados.
- Confirmar que a app nativa nao expõe funcionalidades incompletas para review.

## Ambiente

- `supabaseUrl` e `supabaseAnonKey` devem apontar para producao.
- `publicAppUrl` deve apontar para a URL publica real.
- Validar deep links e login em dispositivo real.
