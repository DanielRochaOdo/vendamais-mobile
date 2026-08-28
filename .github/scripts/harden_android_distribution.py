from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"updated {label}")


gradle = Path("android-app/app/build.gradle.kts")
text = gradle.read_text()

old_release_tasks = '''val releaseBuildRequested = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .any { it in setOf("assembleRelease", "bundleRelease", "renameReleaseApk", "renameReleaseBundle") }
'''
new_release_tasks = '''val releaseBuildRequested = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .any {
        it in setOf(
            "assembleStandardRelease",
            "bundleStandardRelease",
            "renameReleaseApk",
            "renameReleaseBundle",
            "assembleDirectRelease",
            "bundleDirectRelease",
            "renameDirectReleaseApk",
            "renameDirectReleaseBundle",
        )
    }

if (releaseBuildRequested && !hasReleaseSigningConfig) {
    throw GradleException(
        "Release build bloqueado: configure releaseStoreFile, releaseStorePassword, " +
            "releaseKeyAlias e releaseKeyPassword em android-app/local.properties. " +
            "As versoes de producao devem usar sempre a mesma chave de assinatura.",
    )
}
'''
if old_release_tasks not in text:
    raise SystemExit("release task detection block not found")
text = text.replace(old_release_tasks, new_release_tasks, 1)

text = text.replace(
    'val resolvedUpdateApkUrl = "$releaseArtifactBaseUrl/vendamais-mobile-v${appVersion.name}.apk"',
    'val resolvedDirectUpdateApkUrl = "$releaseArtifactBaseUrl/vendamais-mobile-direct-v${appVersion.name}.apk"',
    1,
)

old_update_fields = '''        buildConfigField("String", "UPDATE_METADATA_URL", quoted(resolvedUpdateMetadataUrl))
        buildConfigField("String", "UPDATE_APK_URL", quoted(resolvedUpdateApkUrl))
'''
new_update_fields = '''        // The standard build intentionally has no APK self-installer. This removes the
        // high-risk REQUEST_INSTALL_PACKAGES surface from the default production APK.
        buildConfigField("String", "UPDATE_METADATA_URL", quoted(""))
        buildConfigField("String", "UPDATE_APK_URL", quoted(""))
'''
if old_update_fields not in text:
    raise SystemExit("default update fields not found")
text = text.replace(old_update_fields, new_update_fields, 1)

old_after_default = '''        manifestPlaceholders["publicAppHost"] = resolvedPublicAppHost
    }

    signingConfigs {
'''
new_after_default = '''        manifestPlaceholders["publicAppHost"] = resolvedPublicAppHost
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
        }
        create("direct") {
            dimension = "distribution"
            // Private/direct channel only. This variant keeps the explicit, user-driven
            // APK updater and therefore receives REQUEST_INSTALL_PACKAGES via src/direct.
            buildConfigField("String", "UPDATE_METADATA_URL", quoted(resolvedUpdateMetadataUrl))
            buildConfigField("String", "UPDATE_APK_URL", quoted(resolvedDirectUpdateApkUrl))
        }
    }

    signingConfigs {
'''
if old_after_default not in text:
    raise SystemExit("defaultConfig/signingConfigs boundary not found")
text = text.replace(old_after_default, new_after_default, 1)

old_signing = '''                isV1SigningEnabled = true
                isV2SigningEnabled = true
'''
new_signing = '''                isV1SigningEnabled = true
                isV2SigningEnabled = true
                isV3SigningEnabled = true
                isV4SigningEnabled = true
'''
if old_signing not in text:
    raise SystemExit("signing schemes block not found")
text = text.replace(old_signing, new_signing, 1)

start = text.find('tasks.register<Copy>("renameReleaseBundle") {')
end = text.find('\ndependencies {', start)
if start < 0 or end < 0:
    raise SystemExit("release task section not found")

new_tasks = '''tasks.register<Copy>("renameReleaseBundle") {
    dependsOn("bundleStandardRelease")
    from(layout.buildDirectory.file("outputs/bundle/standardRelease/app-standard-release.aab"))
    into(layout.buildDirectory.dir("outputs/release-artifacts"))
    rename { "vendamais-mobile-v${android.defaultConfig.versionName}.aab" }
}

tasks.register<Copy>("renameReleaseApk") {
    dependsOn("assembleStandardRelease")
    from(layout.buildDirectory.dir("outputs/apk/standard/release")) {
        include("*.apk")
        exclude("*.idsig")
    }
    into(layout.buildDirectory.dir("outputs/release-artifacts"))
    rename { "vendamais-mobile-v${android.defaultConfig.versionName}.apk" }
}

tasks.register<Copy>("renameDirectReleaseBundle") {
    dependsOn("bundleDirectRelease")
    from(layout.buildDirectory.file("outputs/bundle/directRelease/app-direct-release.aab"))
    into(layout.buildDirectory.dir("outputs/release-artifacts"))
    rename { "vendamais-mobile-direct-v${android.defaultConfig.versionName}.aab" }
}

tasks.register<Copy>("renameDirectReleaseApk") {
    dependsOn("assembleDirectRelease")
    from(layout.buildDirectory.dir("outputs/apk/direct/release")) {
        include("*.apk")
        exclude("*.idsig")
    }
    into(layout.buildDirectory.dir("outputs/release-artifacts"))
    rename { "vendamais-mobile-direct-v${android.defaultConfig.versionName}.apk" }
}

// Kept under the historical task name because deployment scripts may already call it.
// The generated metadata is only for the private/direct self-update channel.
tasks.register("generateReleaseUpdateJson") {
    doLast {
        val outputDir = layout.buildDirectory.dir("outputs/release-artifacts").get().asFile
        outputDir.mkdirs()
        val versionName = android.defaultConfig.versionName.orEmpty()
        val versionCode = android.defaultConfig.versionCode ?: 0
        val json = """
            {
              "versionCode": $versionCode,
              "versionName": "$versionName",
              "apkUrl": "$resolvedDirectUpdateApkUrl",
              "notes": "Atualizacao da versao $versionName"
            }
        """.trimIndent() + System.lineSeparator()
        file("${outputDir.absolutePath}/android-update.json").writeText(json)
        file("${outputDir.absolutePath}/android-update-v$versionName.json").writeText(json)
    }
}

tasks.named("renameDirectReleaseBundle") {
    dependsOn("generateReleaseUpdateJson")
}

tasks.named("renameDirectReleaseApk") {
    dependsOn("generateReleaseUpdateJson")
}
'''
text = text[:start] + new_tasks + text[end:]
gradle.write_text(text)
print("updated Android build distribution/signing configuration")

manifest = Path("android-app/app/src/main/AndroidManifest.xml")
replace_once(
    manifest,
    '    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n',
    '    <uses-permission android:name="android.permission.INTERNET" />\n',
    "standard manifest installer permission removal",
)

direct_manifest = Path("android-app/app/src/direct/AndroidManifest.xml")
direct_manifest.parent.mkdir(parents=True, exist_ok=True)
direct_manifest.write_text('''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Private/direct distribution only. The standard release does not request this. -->
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
</manifest>
''')
print("created direct distribution manifest")

local_example = Path("android-app/local.properties.example")
local_text = local_example.read_text()
if "releaseStoreFile=" not in local_text:
    local_text += '''

# Required for every production release. Keep the same long-lived keystore for all versions.
releaseStoreFile=keystore/vendamais-release.jks
releaseStorePassword=CHANGE_ME
releaseKeyAlias=vendamais
releaseKeyPassword=CHANGE_ME
'''
    local_example.write_text(local_text)
    print("documented stable release signing properties")

checklist = Path("android-app/PLAY_STORE_CHECKLIST.md")
check_text = checklist.read_text()
check_text = check_text.replace(
    '- Gerar `bundleRelease`, nao `assembleRelease`.\n- Confirmar que o arquivo final e `app-release.aab`.\n- Validar a assinatura com a keystore de producao.\n',
    '- Gerar o canal padrao com `renameReleaseBundle` (internamente `bundleStandardRelease`).\n- Confirmar que o arquivo final e `vendamais-mobile-v<versao>.aab`.\n- Validar a assinatura com a mesma keystore de producao usada em todas as versoes.\n- Confirmar que o manifesto do canal `standard` NAO contem `REQUEST_INSTALL_PACKAGES`.\n- O canal `direct` e exclusivo para distribuicao privada com autoatualizacao por APK e nao deve ser enviado ao Google Play.\n',
    1,
)
if "## Play Protect" not in check_text:
    check_text += '''

## Play Protect

- O APK padrao de producao e o `standard`; ele nao solicita instalacao de outros APKs.
- Para distribuicao privada que realmente precise do atualizador interno, gerar `renameDirectReleaseApk`.
- Nao alternar a chave de assinatura entre versoes; a reputacao e a continuidade de update dependem do mesmo certificado.
- Antes de distribuir uma nova versao por sideload, validar a assinatura e testar o APK em um dispositivo com Play Protect ativo.
'''
checklist.write_text(check_text)
print("updated Play Store/Play Protect checklist")
