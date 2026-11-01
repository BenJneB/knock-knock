plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Clé de signature des versions à distribuer : variables d'environnement (GitHub Actions) ou, en local,
// ~/.gradle/gradle.properties (conseil.keystore=…, etc.). Créer la clé : tools/creer-cle.sh.
fun signing(name: String): String? =
    providers.environmentVariable("CONSEIL_" + name.uppercase()).orNull
        ?: providers.gradleProperty("conseil.$name").orNull
val keystore = signing("keystore")?.let { file(it) }?.takeIf { it.isFile }

// Numéro de version : le numéro du passage GitHub Actions, pour que chaque APK distribué s'installe par-dessus
// le précédent (1 en local).
val build = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1

android {
    namespace = "be.ombres.conseil"
    compileSdk = 37

    defaultConfig {
        applicationId = "be.ombres.conseil"
        minSdk = 26
        targetSdk = 37
        versionCode = build
        versionName = "0.2.$build"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Un APK par type de processeur : le moteur de lecture de ML Kit pèse ~11 Mo par type.
    // Pour un téléphone : app-arm64-v8a-release.apk (presque tous les téléphones depuis 2017),
    // app-armeabi-v7a-release.apk pour les plus anciens ; x86_64 sert à l'émulateur.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = keystore
            storePassword = signing("keystore_password")
            keyAlias = signing("key_alias")
            keyPassword = signing("key_password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Vraie clé si elle est fournie ; sinon clé de debug (constructions locales, pull requests).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Les captures du jeu servent de données au test instrumenté de lecture (OcrDumpTest).
    sourceSets.getByName("androidTest").assets.directories.add("../../captures")

    // Android Lint : tout avertissement fait échouer la vérification (CI comprise), sauf les règles qui signalent
    // une version plus récente disponible (elles changent avec le temps, sans changement du code).
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.mlkit.text)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
