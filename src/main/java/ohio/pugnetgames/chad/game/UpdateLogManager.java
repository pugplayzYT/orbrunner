package ohio.pugnetgames.chad.game;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads and parses markdown update log files from the update_logs/ resource
 * folder.
 * 
 * Each .md file is parsed into an UpdateEntry with:
 * - title: extracted from the first "# ..." line
 * - lines: the full file content split into MarkdownLine objects
 * 
 * MarkdownLine has a type (H1, H2, H3, BULLET, TEXT, BLANK) and the cleaned
 * text.
 * InGameUI uses these types to pick colors and indentation.
 * 
 * To add a new update log:
 * 1. Create a new .md file in src/main/resources/update_logs/
 * 2. Add the filename to update_logs/index.txt
 * 3. Start the .md file with "# vX.X - Title Here"
 */
public class UpdateLogManager {

    /** Represents a single parsed line of markdown. */
    public static class MarkdownLine {
        public enum Type {
            H1, H2, H3, BULLET, TEXT, BLANK
        }

        public final Type type;
        public final String text;

        public MarkdownLine(Type type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    /** A single update log entry (one .md file). */
    public static class UpdateEntry {
        public final String title; // e.g. "v1.0 - The Great UI Overhaul"
        public final String filename; // e.g. "v1.0.md"
        public final List<MarkdownLine> lines;

        public UpdateEntry(String title, String filename, List<MarkdownLine> lines) {
            this.title = title;
            this.filename = filename;
            this.lines = lines;
        }
    }

    private final List<UpdateEntry> entries = new ArrayList<>();

    /**
     * Loads all update logs listed in update_logs/index.txt.
     * Call this once at startup.
     */
    public void loadAll() {
        entries.clear();

        // Read the index file
        List<String> filenames = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("update_logs/index.txt")) {
            if (is == null) {
                System.err.println("[UpdateLogManager] update_logs/index.txt not found in resources.");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    filenames.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("[UpdateLogManager] Error reading index.txt: " + e.getMessage());
            return;
        }

        // Load each markdown file
        for (String filename : filenames) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("update_logs/" + filename)) {
                if (is == null) {
                    System.err.println("[UpdateLogManager] File not found: update_logs/" + filename);
                    continue;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                List<MarkdownLine> mdLines = new ArrayList<>();
                String title = filename; // fallback title
                String rawLine;

                while ((rawLine = reader.readLine()) != null) {
                    MarkdownLine ml = parseLine(rawLine);

                    // Extract title from first H1 and skip it — it's shown in the entry header
                    if (ml.type == MarkdownLine.Type.H1 && title.equals(filename)) {
                        title = ml.text;
                        continue;
                    }

                    mdLines.add(ml);
                }

                entries.add(new UpdateEntry(title, filename, mdLines));
            } catch (IOException e) {
                System.err.println("[UpdateLogManager] Error reading " + filename + ": " + e.getMessage());
            }
        }

        // Reverse so newest is first (files are listed oldest-first in index.txt)
        Collections.reverse(entries);

        System.out.println("[UpdateLogManager] Loaded " + entries.size() + " update log(s).");
    }

    /**
     * Parses a single raw line of markdown into a MarkdownLine.
     */
    private MarkdownLine parseLine(String raw) {
        if (raw.trim().isEmpty()) {
            return new MarkdownLine(MarkdownLine.Type.BLANK, "");
        }

        String trimmed = raw.trim();

        MarkdownLine.Type type;
        String text;

        if (trimmed.startsWith("### ")) {
            type = MarkdownLine.Type.H3;
            text = trimmed.substring(4).trim();
        } else if (trimmed.startsWith("## ")) {
            type = MarkdownLine.Type.H2;
            text = trimmed.substring(3).trim();
        } else if (trimmed.startsWith("# ")) {
            type = MarkdownLine.Type.H1;
            text = trimmed.substring(2).trim();
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            type = MarkdownLine.Type.BULLET;
            text = trimmed.substring(2).trim();
        } else {
            type = MarkdownLine.Type.TEXT;
            text = trimmed;
        }

        // Strip markdown bold markers for display (we use color/type instead)
        text = text.replace("**", "");
        return new MarkdownLine(type, text);
    }

    /**
     * Returns all loaded entries (newest first).
     */
    public List<UpdateEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
