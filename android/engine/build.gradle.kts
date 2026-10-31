import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Moteur commun : stratégie (plan), lecture des captures et fusion des valeurs lues.
// Compilé pour la JVM (appli Android, tests) et en JavaScript (intégré dans la page web, voir la tâche webPage).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "engine.js"
                output?.library = "conseil"
                output?.libraryTarget = "umd"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            // Lecture d'une capture dans la page : appels asynchrones vers Tesseract.js (promesses).
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            // Fourni par Android ; seulement pour compiler l'enregistrement des lectures.
            compileOnly(libs.org.json)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.org.json)
        }
    }
}

// Assemble les pages web : chaque page de web/src/ reçoit les fichiers qu'elle inclut (code commun, script du mode)
// et le moteur compilé, et devient une page en un seul fichier dans web/ (artifact Claude, envoi par message…).
val webPage by tasks.registering {
    val bundle = tasks.named("jsBrowserProductionWebpack")
    dependsOn(bundle)
    val web = rootProject.layout.projectDirectory.dir("../web").asFile
    // Un dossier par mode (page.html + script.js) ; ce que partagent les pages est dans web/src/commun/.
    val pages = mapOf("zone-sombre/page.html" to "conseil-zone-sombre-chefs.html", "capitale/page.html" to "defense-capitale.html")
    val js = layout.buildDirectory.file("kotlin-webpack/js/productionExecutable/engine.js")
    inputs.file(js)
    inputs.dir(web.resolve("src"))
    outputs.files(pages.values.map { web.resolve(it) })
    doLast {
        val engine = js.get().asFile.readText().trim()
        // Inclusion : le moteur compilé, ou un fichier de web/src/ (commun/script.js, zone-sombre/script.js…).
        fun part(name: String) = if (name == "engine.js") engine
        else web.resolve("src/$name").takeIf { it.isFile }?.readText()?.trimEnd() ?: error("Inclusion inconnue « $name »")
        val include = Regex("""/\* inclure:([\w./-]+) \*/|<!-- inclure:([\w./-]+) -->""")
        for ((src, out) in pages) {
            val html = include.replace(web.resolve("src/$src").readText()) { m -> part(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
            val note = "<!-- Page assemblée depuis web/src/$src par « ./gradlew :engine:webPage » : modifier les sources, pas ce fichier. -->"
            web.resolve(out).writeText(html.replaceFirst("<!DOCTYPE html>\n", "<!DOCTYPE html>\n$note\n"))
        }
    }
}
