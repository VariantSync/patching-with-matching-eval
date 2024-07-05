import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "org.variantsync.core"
version = "0.2.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "file:///${project.projectDir}/local-maven-repo")
}

dependencies {
    implementation("org.variantsync.vevos:simulation:2.0.0")
    // https://mvnrepository.com/artifact/org.apache.commons/commons-configuration2
    implementation("org.apache.commons:commons-configuration2:2.9.0")
    implementation("commons-logging:commons-logging:1.3.1")
    // https://mvnrepository.com/artifact/commons-beanutils/commons-beanutils
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("org.tinylog:tinylog-api-kotlin:2.6.2")
    implementation("org.tinylog:tinylog-impl:2.6.2")
    implementation("de.ovgu:featureide.lib.fm:3.7.2")
    // https://mvnrepository.com/artifact/org.eclipse.jgit/org.eclipse.jgit
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")
    implementation("org.sat4j:core:2.3.5")
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("net.ssehub:kernel_haven:1.0.0")
    implementation("net.lingala.zip4j:zip4j:2.11.4")
    implementation("org.variantsync:diffdetective:1.0.0")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.5")
    // https://mvnrepository.com/artifact/org.yaml/snakeyaml
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks.create<ShadowJar>("SyncStudy") {
    archiveBaseName.set("synchronization-study")
    archiveVersion.set("")

    // Exclude signature files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    // Include the main source sets (classes and resources)
    from(sourceSets.main.get().output)

    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "org.variantsync.evaluation.syncstudy.SynchronizationStudyKt"
    }
}

tasks.create<ShadowJar>("Cherries") {
    archiveBaseName.set("cherries")
    archiveVersion.set("")

    // Exclude signature files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    // Include the main source sets (classes and resources)
    from(sourceSets.main.get().output)

    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "org.variantsync.evaluation.cherries.CherryPickStudyKt"
    }
}

// Second JAR task
tasks.create<ShadowJar>("CherriesAnalysis") {
    archiveBaseName.set("result-analysis-cherries")
    archiveVersion.set("")

    // Exclude signature files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    // Include the main source sets (classes and resources)
    from(sourceSets.main.get().output)

    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "org.variantsync.evaluation.CherryPickResultAnalysis"
    }
}

tasks.create<ShadowJar>("SyncStudyAnalysis") {
    archiveBaseName.set("result-analysis-sync-study")
    archiveVersion.set("")

    // Exclude signature files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    // Include the main source sets (classes and resources)
    from(sourceSets.main.get().output)

    configurations = listOf(project.configurations.runtimeClasspath.get())

    manifest {
        attributes["Main-Class"] = "org.variantsync.evaluation.SyncStudyResultAnalysis"
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("Main")
}
