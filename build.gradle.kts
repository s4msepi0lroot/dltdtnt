plugins {
	id("java")
}

group = "ru.sepiolsmp"
version = "0.1.0"

repositories {
	mavenCentral()
	maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
	compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
}

tasks.jar {
	archiveFileName.set("SepiolSkins-${project.version}.jar")
}
