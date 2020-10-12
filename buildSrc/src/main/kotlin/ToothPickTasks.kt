import dumstuff.dumShitTasks
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import paper.applyPatches
import paper.decompile
import paper.init
import paper.remap
import stuff.repoId
import stuff.repoUrl
import stuff.taskGroupPrivate
import stuff.taskGroupPublic
import toothpick.initRemappingTasks
import java.io.File

fun Project.initToothPickTasks(toothPickExtension: ToothPickExtension) = run {
    val initGitSubmodules: Task by project.tasks.creating {
        group = taskGroupPublic
        onlyIf {
            !File("work/${toothPick.upstreamName}/.git").exists() || !File("work/${toothPick.upstreamName}/work/BuldData.git").exists()
        }
        doLast {
            val (exit, _) = cmd("git", "submodule", "update", "--init", "--recursive", directory = rootDir, printToStdout = true)
            if (exit != 0) {
                throw IllegalStateException("Failed to checkout git submodules: git exited with code $exit")
            }
        }
    }
    initGitSubmodules.name

    val loadData: Task by tasks.creating {
        group = taskGroupPrivate
        dependsOn(initGitSubmodules)
        doLast {
            @Suppress("EXPERIMENTAL_API_USAGE") init(project, toothPick.upstreamName)
        }
    }

    val remap = remap(project)
    remap.mustRunAfter(loadData)
    val decompile = decompile(project)
    decompile.mustRunAfter(remap)
    val applySpigotPatches = applyPatches(project)
    applySpigotPatches.mustRunAfter(decompile)
    val setupUpstream: Task by tasks.creating {
        group = taskGroupPublic
        dependsOn(loadData, remap, decompile, applySpigotPatches)
    }
    setupUpstream.name


    fun copyResources() {
        logger.lifecycle(">>> Copying over resources")
        projectDir.resolve("work/Paper/Paper-Server/src/main/resources/")
                .copyRecursively(projectDir.resolve("${toothPick.forkName}-Server/src/main/resources/"), true)

    }

    val copyResourcesTask: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            copyResources()
        }
    }
    copyResourcesTask.name

    fun applyPatchesWithParam(filter: (File) -> Boolean) {
        for ((name, stuff) in toothPick.subProjects) {
            val (sourceRepo, projectDir, patchesDir) = stuff

            // Reset or initialize subproject
            logger.lifecycle(">>> Resetting subproject $name")
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
            ensureSuccess(cmd("git", "clone", sourceRepo.absolutePath, projectDir.absolutePath, directory = rootDir))


            // Apply patches
            val patches = patchesDir.listFiles()
                    ?.filter(filter)
                    ?.sortedWith(naturalOrder())
                    ?.takeIf { it.isNotEmpty() } ?: continue

            logger.lifecycle(">>> Applying patches to $name")
            val gitCommand = arrayListOf("git", "am", "--3way")
            gitCommand.addAll(patches.map { it.absolutePath })
            ensureSuccess(cmd(*gitCommand.toTypedArray(), directory = projectDir, printToStdout = true))
            logger.lifecycle(">>> Done")
            copyResources()
        }
    }


    val applyPatches: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            applyPatchesWithParam { it.name.endsWith(".patch") }
        }
    }
    applyPatches.name

    val applyPatchesLocal: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            applyPatchesWithParam { it.name.endsWith(".patch") || it.name.endsWith(".localpatch") }
        }
    }
    applyPatchesLocal.name


    val rebuildPatches: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            for ((name, stuff) in toothPick.subProjects) {
                val (_, projectDir, patchesDir) = stuff

                if (!patchesDir.exists()) {
                    patchesDir.mkdirs()
                }

                logger.lifecycle(">>> Rebuilding patches for $name")

                // Nuke old patches
                patchesDir.listFiles()
                        ?.filter { it.name.endsWith(".patch") }
                        ?.sorted()
                        ?.forEach { it.delete() }

                // And generate new
                ensureSuccess(cmd("git", "format-patch",
                        "--no-stat", "--zero-commit", "--full-index", "--no-signature", "-N",
                        "-o", patchesDir.absolutePath, "origin/master",
                        directory = projectDir,
                        printToStdout = true
                ))
            }
        }
    }
    rebuildPatches.name

    val mojangMappings: Task by tasks.creating {
        group = taskGroupPublic
        initRemappingTasks(project, toothPickExtension).forEach {
            dependsOn(it)
            it.mustRunAfter(loadData)
        }
        dependsOn(loadData)
    }

    val cleanUp: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            for ((_, stuff) in toothPick.subProjects) {
                val (_, projectDir, _) = stuff
                logger.lifecycle("Deleting $projectDir...")
                projectDir.deleteRecursively()
            }
            val upstreamDir = File(rootProject.projectDir, toothPick.upstreamName)
            logger.lifecycle("Deleting $upstreamDir...")
            upstreamDir.deleteRecursively()

            val workDir = File(rootProject.projectDir, "work")
            logger.lifecycle("Deleting $workDir...")
            workDir.listFiles { _, filename ->
                filename != "Paper"
            }?.forEach {
                println("Deleting $it")
                it.deleteRecursively()
            }
        }
    }
    cleanUp.name

    dumShitTasks(project)[0].name

    val installLocalMaven: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            for ((_, stuff) in toothPick.subProjects) {
                val (_, projectDir, _) = stuff
                logger.lifecycle("Installing into local maven $projectDir...")
                projectDir.resolve("build/libs").listFiles { file -> file.name.endsWith(".jar") }?.forEach { file ->
                    kotlin.runCatching {
                        cmd("mvn", "install:install-file", "-Dfile=" + file.toPath().toString(), "-DpomFile=" + projectDir.resolve("pom.xml").toString(), directory = projectDir)
                    }
                }
            }
            projectDir.resolve("work").listFiles { file -> file.name.endsWith("mapped.jar") }?.forEach { file ->
                kotlin.runCatching {
                    cmd(
                            "mvn",
                            "install:install-file",
                            "-Dfile=" + file.toPath().toString(),
                            "-DgroupId=me.minidigger",
                            "-DartifactId=minecraft-server",
                            "-Dversion=${toothPick.minecraftVersion}-SNAPSHOT",
                            "-Dpackaging=jar",
                            "-DgeneratePom=true",
                            directory = projectDir
                    )
                }
            }

        }
    }
    installLocalMaven.name

    val deployMaven: Task by tasks.creating {
        group = taskGroupPublic
        doLast {
            if (repoId.isNullOrBlank() || repoUrl.isNullOrBlank()) {
                logger.warn("stuff.repoId or stuff.repoUrl is not set, abborting.")
                return@doLast
            }
            for ((_, stuff) in toothPick.subProjects) {
                val (_, projectDir, _) = stuff
                logger.lifecycle("Installing into local maven $projectDir...")
                projectDir.resolve("build/libs").listFiles { file -> file.name.endsWith(".jar") }?.forEach { file ->
                    kotlin.runCatching {
                        cmd("mvn", "deploy:deploy-file", "-DrepositoryId=$repoId", "-Durl=$repoUrl", "-Dfile=${file.toPath()}", "-DpomFile=${projectDir.resolve("pom.xml")}", directory = projectDir)
                    }
                }
            }

            projectDir.resolve("work").listFiles { file -> file.name.endsWith("mapped.jar") }?.forEach { file ->
                kotlin.runCatching {
                    cmd(
                            "mvn",
                            "deploy:deploy-file",
                            "-Dfile=" + file.toPath().toString(),
                            "-DgroupId=me.minidigger",
                            "-DartifactId=minecraft-server",
                            "-Dversion=${toothPick.minecraftVersion}-SNAPSHOT",
                            "-Dpackaging=jar",
                            "-DgeneratePom=true",
                            "-DrepositoryId=$repoId",
                            "-Durl=$repoUrl",
                            directory = projectDir
                    )
                }
            }

        }
    }
    deployMaven.name

}
