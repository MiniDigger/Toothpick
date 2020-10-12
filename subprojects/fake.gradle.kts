plugins {
    java
}

// This project is used to resolve dependencies, used by mercury in the mapping process

val parentPomFile = project.parent?.projectDir?.resolve("work/Paper/pom.xml")
val serverPomFile = project.parent?.projectDir?.resolve("work/Paper/Paper-Server/pom.xml")
val apiPomFile = project.parent?.projectDir?.resolve("work/Paper/Paper-API/pom.xml")
val mojangPomFile = project.parent?.projectDir?.resolve("work/Paper/Paper-MojangAPI/pom.xml")

if (parentPomFile != null) {
    repositories {
        loadRepositories(parentPomFile, project)
    }

    dependencies {
        loadDependencies(parentPomFile, project, true)
    }
}

if (serverPomFile != null) {
    repositories {
        loadRepositories(serverPomFile, project)
    }

    dependencies {
        loadDependencies(serverPomFile, project, true)
    }
}
if (apiPomFile != null) {
    repositories {
        loadRepositories(apiPomFile, project)
    }

    dependencies {
        loadDependencies(apiPomFile, project, true)
    }
}

if (mojangPomFile != null) {
    repositories {
        loadRepositories(mojangPomFile, project)
    }

    dependencies {
        loadDependencies(mojangPomFile, project, true)
    }
}
