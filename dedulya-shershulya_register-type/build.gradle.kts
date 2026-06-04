plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "ru.itmo.mpp"

repositories {
    mavenCentral()
}

val ktor_version = "3.3.0"
val kotlinx_serialization_version = "1.9.0"

dependencies {
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

sourceSets.main {
    java.srcDir("src")
    resources.srcDir("resources")
}

tasks.register<JavaExec>("assign") {
    mainClass.set("RegisterTypeAssignKt")
    classpath = sourceSets.main.get().runtimeClasspath
    dependsOn(tasks.classes)
    standardOutput = System.out
    errorOutput = System.err
}

tasks.register<JavaExec>("checkAssignment") {
    mainClass.set("RegisterTypeCheckKt")
    classpath = sourceSets.main.get().runtimeClasspath
    dependsOn(tasks.classes)
    standardOutput = System.out
    errorOutput = System.err
    // Pass hint flag to the application when -Phint or -Phint=true is specified
    val hintProp = project.findProperty("hint")?.toString()
    val hintEnabled = hintProp != null && (hintProp.isEmpty() || hintProp.equals("true", ignoreCase = true))
    if (hintEnabled) {
        args("hint")
    }
}

tasks.build {
    dependsOn("checkAssignment")
}
