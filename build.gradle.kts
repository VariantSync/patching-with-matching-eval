import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "1.9.0"
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
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.variantsync.vevos:simulation:1.1.2")
    implementation("org.apache.commons:commons-configuration2:2.8.0")
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("org.tinylog:tinylog-api-kotlin:2.6.2")
    implementation("org.tinylog:tinylog-impl:2.6.2")
    implementation("de.ovgu:featureide.lib.fm:3.7.2")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.5.0.202303070854-r")
    implementation("org.sat4j:core:2.3.5")
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("net.ssehub:kernel_haven:1.0.0")
    implementation("net.lingala.zip4j:zip4j:2.11.4")
    implementation("org.variantsync:diffdetective:1.0.0")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}


tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}
