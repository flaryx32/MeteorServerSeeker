plugins {
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
}

base {
    archivesName = project.property("archives_base_name").toString()
    version = project.property("mod_version").toString()
    group = project.property("maven_group").toString()
}

repositories {
    maven("https://maven.meteordev.org/releases") {
        name = "Meteor Dev Releases"
    }
    maven("https://maven.meteordev.org/snapshots") {
        name = "Meteor Dev Snapshots"
    }
}

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    // Meteor (published against named/Mojang-mapped Minecraft, so no remapping needed)
    implementation("meteordevelopment:meteor-client:${project.property("meteor_version")}")
}

tasks {
    processResources {
        val projectProperties = mapOf(
            "version" to project.version,
            "mc_version" to project.property("minecraft_version"),
            "commit" to (project.findProperty("commit") ?: "")
        )

        inputs.properties(projectProperties)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(projectProperties)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25

        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}
