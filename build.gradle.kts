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
	compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
	options.release.set(21)
}

tasks.processResources {
	filesMatching("plugin.yml") {
		expand("version" to version)
	}
}

tasks.jar {
	archiveBaseName.set("SepiolEconomy")
}
