from pathlib import Path

path = Path("android-app/app/build.gradle.kts")
text = path.read_text()

old = '''val releaseBuildRequested = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .any { it == "assembleRelease" || it == "bundleRelease" }
'''
new = '''val releaseBuildRequested = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .any { it in setOf("assembleRelease", "bundleRelease", "renameReleaseApk", "renameReleaseBundle") }
'''
if old not in text:
    raise SystemExit("releaseBuildRequested block not found")
text = text.replace(old, new, 1)

old = '''tasks.register<Delete>("cleanReleaseApkOutputs") {
    delete(layout.buildDirectory.dir("outputs/apk/release"))
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn("cleanReleaseApkOutputs")
}

'''
if old not in text:
    raise SystemExit("cleanReleaseApkOutputs block not found")
text = text.replace(old, "", 1)

old = '''tasks.register("generateReleaseUpdateJson") {
    dependsOn("bundleRelease")
    doLast {
'''
new = '''tasks.register("generateReleaseUpdateJson") {
    doLast {
'''
if old not in text:
    raise SystemExit("generateReleaseUpdateJson dependency block not found")
text = text.replace(old, new, 1)

path.write_text(text)
print("updated release APK task graph")
