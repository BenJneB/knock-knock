plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "be.ombres.conseil"
    compileSdk = 37

    defaultConfig {
        applicationId = "be.ombres.conseil"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // APK installé hors Play Store : signé avec la clé de debug tant qu'aucune clé n'est fournie.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Les captures du jeu servent de données au test instrumenté de lecture (OcrDumpTest).
    sourceSets.getByName("androidTest").assets.directories.add("../../captures")

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.mlkit.text)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
