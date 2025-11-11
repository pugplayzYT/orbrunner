package ohio.pugnetgames.chad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Scans the hardcoded package directory "ohio.pugnetgames.chad"
 * recursively for all .java files, counts the total lines, and
 * prints a final summary.
 * * ASSUMPTION: This program is executed from the project's root
 * directory where the source code is located at:
 * [Project Root]/src/main/java/ohio/pugnetgames/chad
 * * To run:
 * 1. Compile: javac LineCounter.java
 * 2. Run: java LineCounter
 */
public class LineCounter {

    // HARDCODED TARGET: The package path to scan.
    private static final String TARGET_PACKAGE_PATH = "ohio/pugnetgames/chad";

    // Assumed location relative to the execution directory (e.g., project root)
    private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

    public static void main(String[] args) {

        // --- 1. Determine the starting directory automatically ---
        Path startDir = SOURCE_ROOT.resolve(TARGET_PACKAGE_PATH);

        System.out.println("✨ Java Code Line Count Report - Vibe Check in Progress... ✨");
        System.out.println("Targeting directory: " + startDir.toAbsolutePath());


        // --- 2. Validation ---
        if (!Files.isDirectory(startDir)) {
            System.err.println("\n❌ Major L. Couldn't find the package directory: " + startDir);
            System.err.println("   Make sure you are running this JAR/class from your project's root directory.");
            return;
        }

        // --- 3. Scanning Logic ---
        AtomicLong grandTotalLines = new AtomicLong(0);
        AtomicLong fileCount = new AtomicLong(0);

        try (Stream<Path> stream = Files.walk(startDir)) {
            stream
                    // Filter to include only regular files
                    .filter(Files::isRegularFile)
                    // Filter to include only files that end with ".java"
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        long lineCount = 0;
                        try {
                            // Read all lines and get the size of the resulting list
                            lineCount = Files.readAllLines(path).size();

                            // Update counters
                            grandTotalLines.addAndGet(lineCount);
                            fileCount.incrementAndGet();

                            // Print file details. relativize makes the path look cleaner.
                            // We relativize against the source root for a clean output path.
                            Path relativePath = SOURCE_ROOT.relativize(path);
                            System.out.printf("  %-70s: %d lines%n", relativePath, lineCount);

                        } catch (IOException e) {
                            System.err.printf("  Error reading file %s: %s%n", path, e.getMessage());
                        }
                    });

        } catch (IOException e) {
            System.err.println("\n❌ Couldn't traverse the directory: " + e.getMessage());
            return;
        }

        // --- 4. Final Summary ---
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("✅ Bet! Scanned %d files in %s. Total lines of Java code: %d%n",
                fileCount.get(), TARGET_PACKAGE_PATH, grandTotalLines.get());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("This is giving main character energy, pugplayzYT.");
    }
}