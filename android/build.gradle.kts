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
        // Outils de compilation et de test du moteur JavaScript (webpack, mocha) : versions corrigées de dépendances
        // ayant une faille connue (contrôle OSV-Scanner de la CI). Retirer une ligne quand l'outil l'intègre.
        the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().resolutions.addAll(
            listOf(
                "diff" to "8.0.3", // GHSA-73rr-hh4g-fpgx
                "glob" to "14.0.2", // GHSA-3g3m-2fjr-6pw4
                "minimatch" to "11.0.1", // GHSA-g3hv-75wg-m249
                "serialize-javascript" to "7.1.3", // GHSA-5c6j-r48x-rmvq, GHSA-m5mm-p8h7-m29v, GHSA-qj8w-gfj5-8c6v
            ).map { (name, version) -> org.jetbrains.kotlin.gradle.targets.js.yarn.YarnResolution(name).apply { include(version) } },
        )
    }
}
