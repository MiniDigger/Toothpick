val kotlinxDomVersion = "0.0.10"
val kotlinxSerializationVersion = "0.20.0"
val mercuryVersion = "0.1.0-dyescape-SNAPSHOT"
val lorenzVersion = "0.6.0-dyescape-SNAPSHOT"
val atlasVersion = "0.3.0-dyescape-SNAPSHOT"
val bombeVersion = "0.5.0-dyescape-SNAPSHOT"

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.3.70"
}

repositories {
    mavenCentral()
    // mavenLocal()
    jcenter()
    maven("https://repo.minidigger.me/repository/dyescape-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("http://artifactory.autocrm.net/artifactory/oss-sonatype-snapshots/")
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx.dom:$kotlinxDomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$kotlinxSerializationVersion")
    implementation("org.cadixdev:mercury:$mercuryVersion")
    implementation("org.cadixdev:lorenz:$lorenzVersion")
    implementation("org.cadixdev:lorenz-io-proguard:$lorenzVersion")
    implementation("org.cadixdev:atlas:$atlasVersion")
    implementation("org.cadixdev:bombe:$bombeVersion")

    implementation("org.cadixdev:bombe-jar:$bombeVersion")

    implementation("org.cadixdev:at:0.1.0-SNAPSHOT")
    implementation("me.jamiemansfield:string:0.1.0")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.23.0")

}

gradlePlugin {
    plugins {
        register("ToothPick") {
            id = "toothpick"
            implementationClass = "ToothPick"
        }
    }
}
