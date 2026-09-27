plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.142"
}

group = providers.gradleProperty("mod.group").get()
version = providers.gradleProperty("mod.version").get()

base {
    archivesName.set(providers.gradleProperty("mod.id").get())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.shedaniel.me/")
    maven("https://api.modrinth.com/maven")
}

neoForge {
    version = providers.gradleProperty("neoforge.version").get()

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
    }

    mods {
        create(providers.gradleProperty("mod.id").get()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    val webp4j = "dev.matrixlab.webp4j:webp4j-core:${providers.gradleProperty("webp4j.version").get()}"
    jarJar(implementation(webp4j)!!)

    // These integrations are optional at runtime, but their APIs are referenced by source.
    compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${providers.gradleProperty("clothconfig.version").get()}")
    compileOnly("maven.modrinth:jade:${providers.gradleProperty("jade.version").get()}+neoforge")
    compileOnly("maven.modrinth:first-person-model:${providers.gradleProperty("firstpersonmodel.version").get()}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
