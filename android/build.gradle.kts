plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ktlint) apply false
}

// Style du code Kotlin (règles dans .editorconfig) : ./gradlew ktlintCheck, ou ktlintFormat pour corriger.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.get())
    }
}

// Node.js et Yarn (pour compiler le moteur en JavaScript) se téléchargent depuis les dépôts déclarés
// dans settings.gradle.kts : on empêche le plugin Kotlin d'ajouter les siens.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().apply {
            downloadBaseUrl.set(null as String?)
            // Version de Node fixée (celle par défaut du plugin Kotlin, 24.16.0, précède les correctifs de sécurité
            // d'octobre 2026). À garder alignée avec node-version dans .github/workflows/tests.yml.
            version.set("24.22.2")
        }
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
