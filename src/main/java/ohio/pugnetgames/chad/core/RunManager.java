package ohio.pugnetgames.chad.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Handles all disk I/O for the Runs system.
 *
 * Save structure:
 *   ~/.orbCollectorGame/runs/{id}/
 *       meta.dat   — name, difficulty, timestamps, status, elapsed time
 *       state.dat  — world seed, player position/rotation, key collection state
 */
public class RunManager {

    private static final String USER_HOME   = System.getProperty("user.home");
    private static final Path   RUNS_DIR    = Paths.get(USER_HOME, ".orbCollectorGame", "runs");

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates a new run folder on disk and returns its RunData.
     * If the sanitized name would collide with an existing folder, a numeric
     * suffix is appended (e.g. "my-run-2").
     */
    public RunData createRun(String displayName, Difficulty difficulty) {
        String baseId = sanitizeName(displayName.isBlank() ? "my-run" : displayName);
        String id     = uniqueId(baseId);
        Path   folder = RUNS_DIR.resolve(id);

        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            System.err.println("[RunManager] Failed to create run folder: " + e.getMessage());
        }

        long now = System.currentTimeMillis();
        RunData run = new RunData(id, displayName, difficulty, now, 0L,
                                  RunData.STATUS_IN_PROGRESS, 0L, folder);
        writeMeta(run);
        return run;
    }

    /**
     * Loads all runs from disk, sorted newest-first by createdAt.
     */
    public List<RunData> loadAllRuns() {
        List<RunData> list = new ArrayList<>();
        if (!Files.exists(RUNS_DIR)) return list;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(RUNS_DIR)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    RunData run = readMeta(entry);
                    if (run != null) list.add(run);
                }
            }
        } catch (IOException e) {
            System.err.println("[RunManager] Failed to list runs: " + e.getMessage());
        }

        list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return list;
    }

    /**
     * Writes (or overwrites) the state.dat for a run.
     */
    public void saveRunState(RunData run, RunState state) {
        Path file = run.folderPath.resolve("state.dat");
        StringBuilder sb = new StringBuilder();
        sb.append("seed=").append(state.worldSeed).append('\n');
        sb.append("playerX=").append(state.playerX).append('\n');
        sb.append("playerY=").append(state.playerY).append('\n');
        sb.append("playerZ=").append(state.playerZ).append('\n');
        sb.append("yaw=").append(state.yaw).append('\n');
        sb.append("pitch=").append(state.pitch).append('\n');
        // keys: comma-separated booleans
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < state.keysCollected.length; i++) {
            if (i > 0) keys.append(',');
            keys.append(state.keysCollected[i]);
        }
        sb.append("keys=").append(keys).append('\n');
        writeFile(file, sb.toString());
    }

    /**
     * Reads state.dat for a run. Returns null if not found or corrupt.
     */
    public RunState loadRunState(RunData run) {
        Path file = run.folderPath.resolve("state.dat");
        if (!Files.exists(file)) return null;

        Map<String, String> props = readProps(file);
        try {
            long    seed    = Long.parseLong(props.get("seed"));
            float   px      = Float.parseFloat(props.get("playerX"));
            float   py      = Float.parseFloat(props.get("playerY"));
            float   pz      = Float.parseFloat(props.get("playerZ"));
            float   yaw     = Float.parseFloat(props.get("yaw"));
            float   pitch   = Float.parseFloat(props.get("pitch"));
            String  keyStr  = props.getOrDefault("keys", "");
            boolean[] keys  = parseKeys(keyStr);
            return new RunState(seed, px, py, pz, yaw, pitch, keys);
        } catch (Exception e) {
            System.err.println("[RunManager] Corrupt state.dat for run '" + run.id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Marks a run as completed and persists the final elapsed time.
     */
    public void markCompleted(RunData run, long elapsedMs) {
        run.status      = RunData.STATUS_COMPLETED;
        run.completedAt = System.currentTimeMillis();
        run.elapsedMs   = elapsedMs;
        writeMeta(run);
    }

    /**
     * Updates only the elapsed time in meta.dat (called on mid-run save).
     */
    public void updateElapsed(RunData run, long elapsedMs) {
        run.elapsedMs = elapsedMs;
        writeMeta(run);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Sanitizes a display name into a safe folder name. */
    public static String sanitizeName(String name) {
        return name.toLowerCase(Locale.ROOT)
                   .trim()
                   .replaceAll("\\s+", "-")
                   .replaceAll("[^a-z0-9\\-]", "")
                   .replaceAll("-{2,}", "-")
                   .replaceAll("^-|-$", "");
    }

    private String uniqueId(String base) {
        if (base.isBlank()) base = "run";
        if (!Files.exists(RUNS_DIR.resolve(base))) return base;
        int suffix = 2;
        while (Files.exists(RUNS_DIR.resolve(base + "-" + suffix))) suffix++;
        return base + "-" + suffix;
    }

    private void writeMeta(RunData run) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(run.displayName).append('\n');
        sb.append("difficulty=").append(run.difficulty.name()).append('\n');
        sb.append("createdAt=").append(run.createdAt).append('\n');
        sb.append("completedAt=").append(run.completedAt).append('\n');
        sb.append("status=").append(run.status).append('\n');
        sb.append("elapsedMs=").append(run.elapsedMs).append('\n');
        writeFile(run.folderPath.resolve("meta.dat"), sb.toString());
    }

    private RunData readMeta(Path folder) {
        Path file = folder.resolve("meta.dat");
        if (!Files.exists(file)) return null;
        Map<String, String> p = readProps(file);
        try {
            String     name        = p.getOrDefault("name", folder.getFileName().toString());
            Difficulty diff        = Difficulty.valueOf(p.getOrDefault("difficulty", "EASY"));
            long       createdAt   = Long.parseLong(p.getOrDefault("createdAt", "0"));
            long       completedAt = Long.parseLong(p.getOrDefault("completedAt", "0"));
            String     status      = p.getOrDefault("status", RunData.STATUS_IN_PROGRESS);
            long       elapsedMs   = Long.parseLong(p.getOrDefault("elapsedMs", "0"));
            return new RunData(folder.getFileName().toString(), name, diff,
                               createdAt, completedAt, status, elapsedMs, folder);
        } catch (Exception e) {
            System.err.println("[RunManager] Corrupt meta.dat in " + folder + ": " + e.getMessage());
            return null;
        }
    }

    private Map<String, String> readProps(Path file) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                int eq = line.indexOf('=');
                if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1));
            }
        } catch (IOException e) {
            System.err.println("[RunManager] Failed to read " + file + ": " + e.getMessage());
        }
        return map;
    }

    private void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[RunManager] Failed to write " + path + ": " + e.getMessage());
        }
    }

    private boolean[] parseKeys(String csv) {
        if (csv == null || csv.isBlank()) return new boolean[0];
        String[] parts = csv.split(",");
        boolean[] result = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Boolean.parseBoolean(parts[i].trim());
        }
        return result;
    }
}
