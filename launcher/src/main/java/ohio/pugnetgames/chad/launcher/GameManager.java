package ohio.pugnetgames.chad.launcher;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Manages game versions — querying the server, caching JARs locally,
 * detecting updates, and launching the game.
 */
public class GameManager {

    private static final String INSTALL_DIR_NAME = ".orbrunner";
    private static final String VERSIONS_SUBDIR = "versions";
    private static final String CONFIG_FILE = "launcher.json";
    private static final Gson GSON = new Gson();

    private final Path installDir;
    private final Path versionsDir;
    private final Path configFile;
    private Config config;

    public GameManager() {
        String userHome = System.getProperty("user.home");
        this.installDir = Paths.get(userHome, INSTALL_DIR_NAME);
        this.versionsDir = installDir.resolve(VERSIONS_SUBDIR);
        this.configFile = installDir.resolve(CONFIG_FILE);

        try {
            Files.createDirectories(versionsDir);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.config = loadConfig();
    }

    // ─── Config ───

    public Config getConfig() {
        return config;
    }

    public void setServerUrl(String url) {
        config.serverUrl = url;
        saveConfig();
    }

    private Config loadConfig() {
        if (Files.exists(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null)
                    return c;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Defaults — server URL is injected at build time via launcher.properties
        Config c = new Config();
        c.serverUrl = readBundledServerUrl();
        c.selectedVersion = null;
        c.lastPlayedVersion = null;
        return c;
    }

    private static String readBundledServerUrl() {
        try (InputStream is = GameManager.class.getResourceAsStream("/launcher.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String url = props.getProperty("server.url");
                if (url != null && !url.isBlank())
                    return url;
            }
        } catch (IOException e) {
            System.err.println("[GameManager] Could not read bundled server URL: " + e.getMessage());
        }
        return "http://localhost:5000";
    }

    private void saveConfig() {
        try (Writer w = Files.newBufferedWriter(configFile)) {
            GSON.toJson(config, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ─── Server Queries ───

    /**
     * Fetch the list of available versions from the server.
     */
    public List<VersionInfo> getAvailableVersions() {
        try {
            String json = httpGet(config.serverUrl + "/api/versions");
            Type listType = new TypeToken<List<VersionInfo>>() {
            }.getType();
            List<VersionInfo> versions = GSON.fromJson(json, listType);
            return versions != null ? versions : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[GameManager] Failed to fetch versions: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get the latest version info from the server.
     */
    public VersionInfo getLatestVersion() {
        try {
            String json = httpGet(config.serverUrl + "/api/latest");
            return GSON.fromJson(json, VersionInfo.class);
        } catch (Exception e) {
            System.err.println("[GameManager] Failed to fetch latest version: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if the latest server version is newer than what's installed.
     */
    public boolean needsUpdate() {
        VersionInfo latest = getLatestVersion();
        if (latest == null)
            return false;
        return !isInstalled(latest.version);
    }

    // ─── Local Management ───

    /**
     * Return which versions are cached locally.
     */
    public List<String> getInstalledVersions() {
        List<String> versions = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir, "orbrunner-*.jar")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                // "orbrunner-v3.0.jar" -> "v3.0"
                String ver = name.replace("orbrunner-", "").replace(".jar", "");
                versions.add(ver);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return versions;
    }

    /**
     * Check if a specific version JAR exists locally.
     */
    public boolean isInstalled(String version) {
        return Files.exists(jarPath(version));
    }

    /**
     * Check if ANY version is installed at all (first-run detection).
     */
    public boolean hasAnyInstall() {
        return !getInstalledVersions().isEmpty();
    }

    /**
     * Get the download URL for a specific version.
     */
    public String getDownloadUrl(String version) {
        return config.serverUrl + "/api/download/" + version;
    }

    /**
     * Get the local path where a version JAR would be stored.
     */
    public Path jarPath(String version) {
        return versionsDir.resolve("orbrunner-" + version + ".jar");
    }

    /**
     * Remember the last played version.
     */
    public void setLastPlayed(String version) {
        config.lastPlayedVersion = version;
        saveConfig();
    }

    /**
     * Set the selected version.
     */
    public void setSelectedVersion(String version) {
        config.selectedVersion = version;
        saveConfig();
    }

    // ─── Hash Verification ───

    /**
     * Compute the SHA-256 hash of the locally cached JAR for a given version.
     * Returns null if the file is missing or hashing fails.
     */
    public String computeLocalHash(String version) {
        Path jar = jarPath(version);
        if (!Files.exists(jar))
            return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(jar)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("[GameManager] Hash failed for " + version + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Check whether the locally cached JAR matches the server's expected hash.
     * Pass the already-fetched server version list to avoid an extra HTTP call.
     *
     * Returns {@code true} if the hashes match, or if the server has no hash
     * on record (pre-v3.3 upload) — so old installs are
     * never incorrectly flagged.
     * Returns {@code false} if hashes differ — the JAR needs to be re-downloaded.
     */
    public boolean isHashValid(String version, List<VersionInfo> serverVersions) {
        if (!isInstalled(version))
            return false;
        String serverHash = serverVersions.stream()
                .filter(v -> v.version.equals(version))
                .map(v -> v.hash)
                .findFirst()
                .orElse(null);
        if (serverHash == null || serverHash.isEmpty())
            return true; // no hash to check
        String localHash = computeLocalHash(version);
        if (localHash == null)
            return false;
        return serverHash.equals(localHash);
    }

    // ─── Uninstall ───

    /**
     * Delete the locally cached JAR for the given version.
     * Clears selectedVersion from config if it matches.
     */
    public void uninstallVersion(String version) throws IOException {
        Files.deleteIfExists(jarPath(version));
        if (version.equals(config.selectedVersion)) {
            config.selectedVersion = null;
            saveConfig();
        }
    }

    // ─── Changelog ───

    /**
     * Fetch the raw markdown changelog for a version from the server.
     * Returns null if the server is unreachable or the changelog doesn't exist.
     */
    public String fetchChangelog(String version) {
        try {
            return httpGet(config.serverUrl + "/api/changelog/" + version);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Launching ───

    /**
     * Launch the game for the given version.
     */
    public Process launchGame(String version) throws IOException {
        Path jar = jarPath(version);
        if (!Files.exists(jar)) {
            throw new FileNotFoundException("JAR not found: " + jar);
        }

        System.out.println("[GameManager] Launching " + jar);

        ProcessBuilder pb = new ProcessBuilder("java", "-jar", jar.toAbsolutePath().toString());
        pb.inheritIO();
        Process process = pb.start();

        setLastPlayed(version);
        return process;
    }

    // ─── HTTP Helper ───

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("HTTP " + code + " from " + urlStr);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    // ─── Inner Classes ───

    public static class Config {
        public String serverUrl;
        public String selectedVersion;
        public String lastPlayedVersion;
    }

    public static class VersionInfo {
        public String version;
        public long size;
        public String uploaded_at;
        public String hash; // SHA-256 hex digest; null for versions uploaded before v3.3

        @Override
        public String toString() {
            return version;
        }
    }
}
