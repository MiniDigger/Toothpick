import MyProject.forkName
import MyProject.upstreamName
import kotlinx.dom.elements
import kotlinx.dom.parseXml
import kotlinx.dom.search
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.project
import java.io.File

fun DependencyHandlerScope.loadDependencies(pomFile: File, addAPI: Boolean = false) {
    if (!pomFile.exists()) {
        MyProject.logger.warn("$pomFile doesn't exist, please run `setupUpstream`!")
        return
    }

    MyProject.logger.info("Loading dependencies from pom $pomFile")

    val dom = parseXml(pomFile)

    val dependenciesBlock = dom.search("dependencies").firstOrNull() ?: return

    // Load dependencies
    dependenciesBlock.elements("dependency").forEach { dependencyElem ->
        val groupId = dependencyElem.search("groupId").firstOrNull()!!.textContent
        val artifactId = dependencyElem.search("artifactId").firstOrNull()!!.textContent
        val version = dependencyElem.search("version").firstOrNull()!!.textContent.applyReplacements(mapOf(
                "project.version" to "${MyProject.project.version}",
                "minecraft.version" to MyProject.minecraftVersion
        ))
        val scope = dependencyElem.search("scope").firstOrNull()?.textContent
        val classifier = dependencyElem.search("classifier").firstOrNull()?.textContent

        val dependencyString = "${groupId}:${artifactId}:${version}${classifier?.run { ":$this" } ?: ""}"
        MyProject.logger.debug("Read dependency '{}' from '{}'", dependencyString, pomFile.absolutePath)

        // Special case API
        if (artifactId == "${upstreamName.toLowerCase()}-api") {
            if (addAPI) {
                add("implementation", project(":${forkName.toLowerCase()}-api"))
            }
            return@forEach
        }

        when (scope) {
            "compile", null -> add("implementation", dependencyString)
            "provided" -> {
                add("compileOnly", dependencyString)
                add("testImplementation", dependencyString) // TODO: Bukkit quirk? or Maven scope mapping? No clue
            }
            "runtime" -> add("runtimeOnly", dependencyString)
            "test" -> add("testImplementation", dependencyString)
        }
    }
}

fun RepositoryHandler.loadRepositories(pomFile: File) {
    if (!pomFile.exists()) {
        MyProject.logger.warn("$pomFile doesn't exist, please run `setupUpstream`!")
        return
    }

    MyProject.logger.info("Loading repositories from pom $pomFile")

    val dom = parseXml(pomFile)
    val repositoriesBlock = dom.search("repositories").firstOrNull() ?: return

    // Load repositories
    repositoriesBlock.elements("repository").forEach { repositoryElem ->
        val url = repositoryElem.search("url").firstOrNull()?.textContent ?: return@forEach
        maven(url)
    }
}

private fun String.applyReplacements(replacements: Map<String, String>): String {
    var result = this
    for ((key, value) in replacements) {
        result = result.replace("\${$key}", value)
    }
    return result
}
