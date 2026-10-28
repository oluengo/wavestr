// Beta (WAVESTR_BETA=1, rama beta en CI): otra app, «Wavestr Beta» (io.github.oluengo.wavestr.beta),
// versión X.Y.Z-beta.N y código versionCode·1000 + N. Se instala junto a la normal, con sus propios datos.
val wavestrVersion = "0.9.0"
val wavestrCode = 9
val beta = System.getenv("WAVESTR_BETA") == "1"
val betaN = (System.getenv("WAVESTR_BETA_N") ?: "0").toInt()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.oluengo.wavestr"
    compileSdk = 35

    defaultConfig {
        applicationId = if (beta) "io.github.oluengo.wavestr.beta" else "io.github.oluengo.wavestr"
        minSdk = 26
        targetSdk = 35
        versionCode = if (beta) wavestrCode * 1000 + (betaN % 1000) else wavestrCode
        versionName = if (beta) "$wavestrVersion-beta.$betaN" else wavestrVersion
        resValue("string", "app_name", if (beta) "Wavestr Beta" else "Wavestr")
        buildConfigField("boolean", "BETA", beta.toString())
        // Solo nuestros tres idiomas: evita que las librerías añadan otros al APK.
        resourceConfigurations += listOf("en", "es", "ca")
    }

    signingConfigs {
        // Firma de release desde variables de entorno (GitHub Actions). Si faltan, release queda sin firmar.
        val ksPath = System.getenv("WAVESTR_KEYSTORE")?.takeIf { it.isNotBlank() }
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("WAVESTR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WAVESTR_KEY_ALIAS")
                keyPassword = System.getenv("WAVESTR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Varias piezas de Material 3 (pull-to-refresh, segmented buttons…) siguen marcadas como experimentales.
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
    // Sin el bloque de dependencias cifrado de Google (como Usus; lo piden F-Droid/IzzyOnDroid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.extractor)

    implementation(project(":core"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.coil.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.coil.svg)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
