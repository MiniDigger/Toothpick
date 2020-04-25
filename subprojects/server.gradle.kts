import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import java.text.SimpleDateFormat
import java.util.*

plugins {
    java
    id("com.github.johnrengelman.shadow")
}

val setup: Task by tasks.creating {
    if (project.parent?.extra?.get("settingUp") as Boolean) {
        logger.info("Setting up ${project.name}")

        repositories {
            loadRepositories(File(project.projectDir, "pom.xml"))
        }

        dependencies {
            loadDependencies(File(project.projectDir, "pom.xml"), true)
        }

        val test by tasks.getting(Test::class) {
            onlyIf { false } // NEVER
        }

        val shadowJar by tasks.getting(ShadowJar::class) {
            transform(Log4j2PluginsCacheFileTransformer::class.java)

            manifest {
                attributes(
                        "Main-Class" to "org.bukkit.craftbukkit.Main",
                        "Implementation-Title" to "CraftBukkit",
                        //"Implementation-Version" to "${describe}",
                        "Implementation-Vendor" to SimpleDateFormat("yyyyMMdd-HHmm").format(Date()),
                        "Specification-Title" to "Bukkit",
                        "Specification-Version" to "${project.version}",
                        "Specification-Vendor" to "Bukkit Team"
                )
            }

            // Don't like to do this but sadly have to do this for compatibility reasons
            val relocVersion = MyProject.minecraftVersion.replace(".", "_")
            relocate("org.bukkit.craftbukkit", "org.bukkit.craftbukkit.v$relocVersion") {
                exclude("org.bukkit.craftbukkit.Main*")
            }

            relocate("net.minecraft.server", "net.minecraft.server.v$relocVersion")
        }

        tasks["build"].dependsOn(shadowJar)
    }
}
