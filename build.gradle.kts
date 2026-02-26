plugins {
    java // Apply the standard Java plugin
}

group = "ohio.pugnetgames.chad"
version = file("src/main/resources/update_logs/index.txt")
    .readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .last()
    .removeSuffix(".md") // e.g. "v1.4.md" -> "v1.4"

repositories {
    mavenCentral()
}

// Declare variables using 'val' outside the dependencies block
val lwjglVersion = "3.3.3"
// IMPORTANT: Change 'natives-windows' to 'natives-linux' or 'natives-macos' if needed.
val lwjglNatives = "natives-windows"

dependencies {
    // Standard testing dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- MP3 SUPPORT (JLayer/Tritonus) ---
    // This allows Java Sound to handle MP3 format.
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")

    // --- LWJGL 3.3.3 Dependencies for TRUE OpenGL Rendering ---

    // 1. Use implementation(platform(...)) to manage versions (BOM)
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    // 2. Core LWJGL modules (version pulled from BOM)
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")
    implementation("org.lwjgl:lwjgl-assimp")

    // 3. Native libraries (FIXED NOTATION: Using full coordinate string for reliable classifier parsing)
    // We include the version here explicitly to satisfy the coordinate requirements for the classifier.
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-assimp:$lwjglVersion:$lwjglNatives")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

// 💥 THE FIX: Configure the JAR task to create an Uber JAR (Fat JAR) 💥
tasks.jar {
    // 1. Set the Main-Class
    manifest {
        attributes(mapOf("Main-Class" to "ohio.pugnetgames.chad.GameApp"))
    }

    // 2. Define how to handle duplicate files (important for dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // 3. Include all dependency classes from the runtimeClasspath directly into the JAR
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
}