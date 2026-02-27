plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "ohio.pugnetgames.chad.launcher"
version = "1.0.0"

repositories {
    mavenCentral()
}

javafx {
    version = "17.0.2"
    modules("javafx.controls", "javafx.graphics")
}

dependencies {
    // JSON parsing — tiny, no-dependency library
    implementation("com.google.code.gson:gson:2.10.1")
}

application {
    mainClass.set("ohio.pugnetgames.chad.launcher.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Inject orbrunnerServerUrl from gradle.properties into launcher.properties at build time
tasks.processResources {
    val serverUrl = ((findProperty("orbrunnerServerUrl") as? String)?.takeIf { it.isNotBlank() })
        ?: "http://localhost:5000"
    inputs.property("orbrunnerServerUrl", serverUrl)
    filesMatching("launcher.properties") {
        expand("orbrunnerServerUrl" to serverUrl)
    }
}

// Fat JAR for distribution
tasks.jar {
    manifest {
        attributes(mapOf("Main-Class" to "ohio.pugnetgames.chad.launcher.Main"))
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}
