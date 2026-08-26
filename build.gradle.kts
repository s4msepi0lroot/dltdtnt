plugins {
    java
}

group = "ru.sepiolsmp"
version = "0.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 1.21.1 API. Youer is Paper-compatible, so plugins build against Paper.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Gson is shipped inside the server jar, so it is compile-only here (no shading needed).
    compileOnly("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("SepiolCore-${project.version}.jar")
}
