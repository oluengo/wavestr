import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Wavestr para Linux y Windows (Compose Desktop). Usa la lógica de :core y los textos de la app Android.
// Beta: WAVESTR_BETA=1 → «Wavestr Beta», con su propia carpeta de datos.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val wavestrVersion: String = Regex("val wavestrVersion = \"(.*)\"")
    .find(rootProject.file("app/build.gradle.kts").readText())!!.groupValues[1]
val beta = System.getenv("WAVESTR_BETA") == "1"
val betaN = (System.getenv("WAVESTR_BETA_N") ?: "0").toInt()
val appName = if (beta) "Wavestr Beta" else "Wavestr"
val shownVersion = if (beta) "$wavestrVersion-beta.$betaN" else wavestrVersion
// Instaladores: solo números. En beta, el tercer número es el de la compilación para que cada una actualice a la anterior.
val packageVersionNumber = wavestrVersion.split(".").let { (a, b, c) -> if (beta) "$a.$b.${betaN % 65535}" else "$a.$b.$c" }

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(libs.coil3.svg)
    implementation(libs.vlcj)
}

// Los textos (ca/es/en) salen de los strings.xml de Android: una sola fuente de traducciones.
val syncI18n by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/res")) { include("values/strings.xml", "values-es/strings.xml", "values-ca/strings.xml") }
    into(layout.buildDirectory.dir("generated/i18n/i18n"))
}
val buildInfo by tasks.registering {
    val out = layout.buildDirectory.file("generated/info/wavestr.properties")
    inputs.property("v", shownVersion); inputs.property("beta", beta)
    outputs.file(out)
    doLast { out.get().asFile.apply { parentFile.mkdirs(); writeText("version=$shownVersion\nbeta=$beta\nname=$appName\n") } }
}
sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/i18n"))
sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/info"))
tasks.named("processResources") { dependsOn(syncI18n, buildInfo) }

compose.desktop {
    application {
        mainClass = "io.github.oluengo.wavestr.desktop.MainKt"
        // Solo para la prueba de arranque en CI (WAVESTR_SMOKE=1 ./gradlew :desktop:run).
        if (System.getenv("WAVESTR_SMOKE") != null) jvmArgs += listOf("-Dwavestr.smoke=" + System.getenv("WAVESTR_SMOKE"), "-XX:ErrorFile=/tmp/wavestr_hs_err.log")
        System.getenv("WAVESTR_HLS_DEMUX")?.let { jvmArgs += "-Dwavestr.hlsdemux=$it" }
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = if (beta) "Wavestr-Beta" else "Wavestr"
            packageVersion = packageVersionNumber
            description = "Radio online y directos de Nostr"
            vendor = "oluengo"
            copyright = "MIT"
            licenseFile.set(rootProject.file("LICENSE"))
            includeAllModules = true
            linux {
                packageName = if (beta) "wavestr-beta" else "wavestr"
                iconFile.set(project.file("icons/wavestr.png"))
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                debMaintainer = "oluengo@users.noreply.github.com"
            }
            windows {
                iconFile.set(project.file("icons/wavestr.ico"))
                menuGroup = appName
                perUserInstall = true
                dirChooser = true
                shortcut = true
                menu = true
                // Fijo para que cada versión actualice a la anterior (distinto en la beta: se instalan juntas).
                upgradeUuid = if (beta) "5c1f1f8e-3e0b-4a51-9a3e-2b6f6d0f9b21" else "a7c3d2e4-7f1b-4f3a-8d6e-0c9b5e2f4a17"
            }
        }
    }
}
