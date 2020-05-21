package toothpick

import cmd
import ensureSuccess
import org.cadixdev.at.io.AccessTransformFormats
import org.cadixdev.atlas.Atlas
import org.cadixdev.lorenz.asm.LorenzRemapper
import org.cadixdev.lorenz.io.MappingFormats
import org.cadixdev.mercury.Mercury
import org.cadixdev.mercury.at.AccessTransformerRewriter
import org.cadixdev.mercury.extra.BridgeMethodRewriter
import org.cadixdev.mercury.remapper.MercuryRemapper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import paper.minecraftversion
import paper.runGitCmd
import stuff.taskGroupPrivate
import java.io.File
import java.io.FileWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun initRemappingTasks(project: Project): List<Task> {
    val createMappings: Task by project.tasks.creating {
        group = taskGroupPrivate
        onlyIf {
            !Files.exists(project.projectDir.toPath().resolve("work/spigotToMojang.srg"))
        }
        doLast {
            val projectDir = project.projectDir.toPath()

            val buildData = projectDir.resolve("work/Paper/work/BuildData/mappings")

            project.logger.info("Reading...")
            val classes = MappingFormats.CSRG.read(buildData.resolve("bukkit-1.15.2-cl.csrg"))
            val members = MappingFormats.CSRG.read(buildData.resolve("bukkit-1.15.2-members.csrg"))

            project.logger.info("Fixing packages...")
            classes.topLevelClassMappings.forEach { klass ->
                if (!klass.deobfuscatedName.contains("/")) {
                    klass.deobfuscatedName = "net/minecraft/server/" + klass.deobfuscatedName
                }
            }
            members.topLevelClassMappings.forEach { klass ->
                if (!klass.deobfuscatedName.contains("/")) {
                    klass.deobfuscatedName = "net/minecraft/server/" + klass.deobfuscatedName
                }
            }

            project.logger.info("Combining...")
            val spigotToNms = members.reverse()
            spigotToNms.topLevelClassMappings.forEach { klassWith ->
                val klass = classes.topLevelClassMappings.find { k -> k.deobfuscatedName == klassWith.obfuscatedName }
                if (klass != null) {
                    klassWith.deobfuscatedName = klass.obfuscatedName
                    klassWith.innerClassMappings.forEach { innerKlass ->
                        val topKlass = classes.topLevelClassMappings.find { topKlass -> topKlass.deobfuscatedName == klass.deobfuscatedName }
                        val topInnerClass = topKlass?.innerClassMappings?.find { topInnerKlass -> topInnerKlass.deobfuscatedName == innerKlass.deobfuscatedName }
                        innerKlass.deobfuscatedName = topInnerClass?.obfuscatedName
                    }
                }
            }
            classes.topLevelClassMappings.forEach { klass ->
                val klassWith = spigotToNms.getOrCreateTopLevelClassMapping(klass.deobfuscatedName)
                klassWith.deobfuscatedName = klass.obfuscatedName
            }

            val nmsToSpigot = spigotToNms.reverse()

            project.logger.info("Writing...")
            MappingFormats.SRG.write(spigotToNms, project.projectDir.toPath().resolve("work/spigotToNms.srg"))
            MappingFormats.SRG.write(nmsToSpigot, project.projectDir.toPath().resolve("work/nmsToSpigot.srg"))
            project.logger.info("Done!")


            project.logger.info("Reading mojang...")
            val mojangFile = project.projectDir.resolve("work/server.txt")
            if (!mojangFile.exists()) {
                mojangFile.writeText(URL("https://launcher.mojang.com/v1/objects/59c55ae6c2a7c28c8ec449824d9194ff21dc7ff1/server.txt").readText())
            }
            val mojangMappings = MappingFormats.byId("proguard").read(mojangFile.toPath())

            project.logger.info("Combining...")
            val mojangToSpigot = mojangMappings.merge(nmsToSpigot)
            val spigotToMojang = mojangToSpigot.reverse()

            project.logger.info("Writing...")
            MappingFormats.SRG.write(mojangToSpigot, project.projectDir.toPath().resolve("work/mojangToSpigot.srg"))
            MappingFormats.SRG.write(spigotToMojang, project.projectDir.toPath().resolve("work/spigotToMojang.srg"))
            project.logger.info("Done!")
        }
    }

    val applyMercury: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(createMappings)
        doLast {
            val projectDir = project.projectDir.toPath()
            val outputDir = projectDir.resolve("work/Paper-Server-Remapped/src")

            val paper = project.projectDir.resolve("work/Paper/Paper-Server")
            val remapped = project.projectDir.resolve("work/Paper-Server-Remapped")

            if (remapped.exists()) {
                ensureSuccess(cmd("git", "reset", "--hard", "origin/master", directory = remapped))
            } else {
                ensureSuccess(cmd("git", "clone", paper.absolutePath, remapped.absolutePath, directory = project.projectDir))
            }

            ensureSuccess(cmd("git", "am", "--3way", project.projectDir.resolve("toothpick/preremapping.patch").absolutePath, directory = paper))

            // TODO fix me, we are losing paper-servers resources folder here
            if (Files.isDirectory(outputDir)) {
                outputDir.toFile().deleteRecursively()
            }
            Files.createDirectory(outputDir)

            val mappings = MappingFormats.SRG.read(projectDir.resolve("work/spigotToMojang.srg"))
            val combined = MappingFormats.SRG.read(mappings, projectDir.resolve("toothpick/toothpick.srg"))
            MappingFormats.SRG.write(combined, project.projectDir.toPath().resolve("work/spigotToMojangPlusToothpick.srg"))

            val ats = AccessTransformFormats.FML.read(projectDir.resolve("toothpick/toothpick.at"))
            val mercury = Mercury()

            mercury.sourceCompatibility = "1.8"
            mercury.encoding = StandardCharsets.UTF_8

            mercury.classPath.add(projectDir.resolve("work/Paper/work/Minecraft/1.15.2/1.15.2-mapped.jar"))
            mercury.classPath.add(projectDir.resolve("work/Paper/Paper-API/src/main/java"))

            project.subprojects.filter { p -> p.name == "fake" }.forEach { p ->
                p.configurations.filter { config -> config.isCanBeResolved }.forEach { config ->
                    config.resolvedConfiguration.files.filter { file -> !file.absolutePath.contains("build/classes") }.forEach { file ->
                        mercury.classPath.add(file.toPath())
                    }
                }
            }

            mercury.processors.add(MercuryRemapper.create(combined))
            mercury.processors.add(AccessTransformerRewriter.create(ats))
            mercury.processors.add(BridgeMethodRewriter.create())

            // run for main
            mercury.rewrite(projectDir.resolve("work/Paper/Paper-Server/src/main/java"), outputDir.resolve("main/java"))
            // add main to CP
            mercury.classPath.add(outputDir.resolve("main/java"))
            // run for test
            //mercury.rewrite(projectDir.resolve("work/Paper/Paper-Server/src/test/java"), outputDir.resolve("test/java"))

            runGitCmd("add", ".", "-A", directory = outputDir.toFile())
            runGitCmd("commit", "-m", "Mojang Mappings", directory = outputDir.toFile())
        }
    }

    val applyAtlas: Task by project.tasks.creating {
        group = taskGroupPrivate
        dependsOn(createMappings)
        doLast {
            val projectDir = project.projectDir.toPath()
            val mappings = MappingFormats.SRG.read(projectDir.resolve("work/spigotToMojang.srg"))
            val combined = MappingFormats.SRG.read(mappings, projectDir.resolve("toothpick/toothpick.srg"))
            MappingFormats.SRG.write(combined, project.projectDir.toPath().resolve("work/spigotToMojangPlusToothpick.srg"))

            val ats = AccessTransformFormats.FML.read(projectDir.resolve("toothpick/toothpick.at"))
            val atlas = Atlas()
            atlas.install {
                AccessTransformingJarEntryTransformer(ats)
            }
            atlas.install { ctx ->
                DebugJarEntryRemappingTransformer(LorenzRemapper(combined, ctx.inheritanceProvider()))
            }
            atlas.run(projectDir.resolve("work/Paper/work/Minecraft/1.15.2/1.15.2-mapped.jar"), projectDir.resolve("work/1.15.2-mojang-mapped.jar"))
            atlas.close()

            ensureSuccess(cmd("mvn", "install:install-file", "-q", "-Dfile=${project.projectDir.resolve("work/1.15.2-mojang-mapped.jar").absolutePath}", "-Dpackaging=jar", "-DgroupId=me.minidigger", "-DartifactId=minecraft-server", "-Dversion=\"$minecraftversion-SNAPSHOT\"", directory = project.projectDir))
        }
    }

    val decompMapped: Task by project.tasks.creating {
        group = taskGroupPrivate
        doLast {
            val classesFolder = project.projectDir.resolve("work/classes")
            logger.lifecycle("Extracing classes...")
            classesFolder.deleteRecursively()
            classesFolder.mkdirs()
            ensureSuccess(cmd("jar", "xf", project.projectDir.resolve("work/1.15.2-mojang-mapped.jar").absolutePath, directory = classesFolder, printToStdout = true))

            val decompFolder = project.projectDir.resolve("work/decomp")
            decompFolder.deleteRecursively()
            decompFolder.mkdirs()
            logger.lifecycle("Decompiling classes...")
            try {
                ensureSuccess(cmd("java", "-jar", project.projectDir.resolve("work/Paper/work/BuildData/bin/fernflower.jar").absolutePath, "-dgs=1", "-hdc=0", "-asc=1", "-udv=0", "-rsy=1", "-aoa=1", classesFolder.absolutePath, decompFolder.absolutePath, directory = project.rootDir, printToStdout = true))
            } catch (e: IllegalStateException) {
                decompFolder.deleteRecursively()
                throw GradleException("Failed to decompile classes.", e)

            }
        }
    }

    return listOf(applyAtlas, applyMercury)
}
