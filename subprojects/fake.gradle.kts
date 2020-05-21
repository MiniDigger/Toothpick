plugins {
    java
}
val pomFile = project.parent?.projectDir?.resolve("work/Paper/Paper-Server/pom.xml")
val pomApiFile = project.parent?.projectDir?.resolve("work/Paper/Paper-API/pom.xml")
if (pomFile != null) {
    repositories {
        loadRepositories(pomFile, project)
    }

    dependencies {
        loadDependencies(pomFile, project, true)
    }
}
if (pomApiFile != null) {
    repositories {
        loadRepositories(pomApiFile, project)
    }

    dependencies {
        loadDependencies(pomApiFile, project, true)
    }
}
