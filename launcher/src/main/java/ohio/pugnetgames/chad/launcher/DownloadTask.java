package ohio.pugnetgames.chad.launcher;

import javafx.concurrent.Task;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;

/**
 * Background task that downloads a game version JAR from the server
 * with progress tracking. Reports bytes downloaded / total bytes
 * so the UI can show a progress bar and MB counter.
 */
public class DownloadTask extends Task<Path> {

    private final String downloadUrl;
    private final Path destination;

    public DownloadTask(String downloadUrl, Path destination) {
        this.downloadUrl = downloadUrl;
        this.destination = destination;
    }

    @Override
    protected Path call() throws Exception {
        updateMessage("Connecting...");

        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Server returned HTTP " + responseCode);
        }

        long totalBytes = conn.getContentLengthLong();
        if (totalBytes <= 0) {
            // Fallback: unknown size
            totalBytes = -1;
        }

        updateMessage("Downloading...");

        // Ensure parent directory exists
        destination.getParent().toFile().mkdirs();

        long downloaded = 0;
        byte[] buffer = new byte[8192];

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream out = new FileOutputStream(destination.toFile())) {

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (isCancelled()) {
                    updateMessage("Cancelled");
                    // Clean up partial file
                    destination.toFile().delete();
                    return null;
                }

                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                if (totalBytes > 0) {
                    updateProgress(downloaded, totalBytes);
                    String msg = formatSize(downloaded) + " / " + formatSize(totalBytes);
                    updateMessage(msg);
                } else {
                    updateMessage(formatSize(downloaded) + " downloaded");
                }
            }
        }

        updateMessage("Download complete!");
        updateProgress(1, 1);
        return destination;
    }

    /**
     * Format byte count into human-readable string.
     */
    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
