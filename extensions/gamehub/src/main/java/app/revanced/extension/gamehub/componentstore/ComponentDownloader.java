package app.revanced.extension.gamehub.componentstore;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Downloads component release files with progress tracking and cancellation support.
 * Runs in background thread, reports progress to main thread via callback.
 */
public class ComponentDownloader {
    private static final String TAG = "BH-ComponentDownloader";
    private static final int BUFFER_SIZE = 8192;
    private static final int TIMEOUT_MS = 30000;
    
    private volatile boolean cancelled = false;
    private Thread downloadThread;

    public interface DownloadCallback {
        void onProgress(long downloaded, long total, int percentage);
        void onComplete(File downloadedFile);
        void onError(String error);
    }

    /**
     * Start downloading a component release to specified destination.
     * 
     * @param release The release to download
     * @param destDir Directory to save the file
     * @param callback Progress/completion callback (called on main thread)
     */
    public void download(final ComponentReleaseFetcher.ComponentRelease release, 
                        final File destDir, 
                        final DownloadCallback callback) {
        if (downloadThread != null && downloadThread.isAlive()) {
            callback.onError("Download already in progress");
            return;
        }
        
        cancelled = false;
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                InputStream input = null;
                FileOutputStream output = null;
                File tempFile = null;
                
                try {
                    // Create destination directory if needed
                    if (!destDir.exists() && !destDir.mkdirs()) {
                        postError(mainHandler, callback, "Failed to create download directory");
                        return;
                    }
                    
                    // Temporary file during download
                    tempFile = new File(destDir, release.fileName + ".tmp");
                    final File finalFile = new File(destDir, release.fileName);
                    
                    // Remove existing temp file
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    
                    Log.i(TAG, "Downloading " + release.version + " from " + release.downloadUrl);
                    
                    URL url = new URL(release.downloadUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);
                    conn.setRequestProperty("User-Agent", "BannerHub-ComponentStore/1.0");
                    
                    // Follow redirects
                    conn.setInstanceFollowRedirects(true);
                    
                    int code = conn.getResponseCode();
                    if (code != 200 && code != 301 && code != 302) {
                        postError(mainHandler, callback, "HTTP error: " + code);
                        return;
                    }
                    
                    // Get content length (may be -1 if unknown)
                    final long contentLength = conn.getContentLengthLong();
                    
                    input = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE);
                    output = new FileOutputStream(tempFile);
                    
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long downloaded = 0;
                    int lastPercentage = -1;
                    int bytesRead;
                    
                    while ((bytesRead = input.read(buffer)) != -1) {
                        if (cancelled) {
                            Log.i(TAG, "Download cancelled by user");
                            tempFile.delete();
                            postError(mainHandler, callback, "Download cancelled");
                            return;
                        }
                        
                        output.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;
                        
                        // Report progress
                        if (contentLength > 0) {
                            final int percentage = (int) ((downloaded * 100) / contentLength);
                            if (percentage != lastPercentage) {
                                lastPercentage = percentage;
                                final long finalDownloaded = downloaded;
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onProgress(finalDownloaded, contentLength, percentage);
                                    }
                                });
                            }
                        } else {
                            // Unknown size, report bytes only
                            final long finalDownloaded = downloaded;
                            if (downloaded % (1024 * 512) == 0) { // Update every 512KB
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onProgress(finalDownloaded, -1, -1);
                                    }
                                });
                            }
                        }
                    }
                    
                    output.flush();
                    output.close();
                    output = null;
                    
                    // Verify minimum size if specified
                    if (tempFile.length() < 1024) {
                        postError(mainHandler, callback, "Downloaded file too small (< 1KB)");
                        tempFile.delete();
                        return;
                    }
                    
                    // Rename temp to final
                    if (finalFile.exists()) {
                        finalFile.delete();
                    }
                    
                    if (!tempFile.renameTo(finalFile)) {
                        postError(mainHandler, callback, "Failed to finalize download");
                        return;
                    }
                    
                    Log.i(TAG, "Download complete: " + finalFile.getAbsolutePath() + 
                               " (" + downloaded + " bytes)");
                    
                    final File resultFile = finalFile;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onComplete(resultFile);
                        }
                    });
                    
                } catch (final Exception e) {
                    Log.e(TAG, "Download failed", e);
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                    postError(mainHandler, callback, "Download failed: " + e.getMessage());
                    
                } finally {
                    try {
                        if (output != null) output.close();
                        if (input != null) input.close();
                        if (conn != null) conn.disconnect();
                    } catch (Exception ignored) {}
                }
            }
        });
        
        downloadThread.start();
    }

    /**
     * Cancel ongoing download.
     */
    public void cancel() {
        cancelled = true;
        if (downloadThread != null) {
            downloadThread.interrupt();
        }
    }

    /**
     * Check if download is in progress.
     */
    public boolean isDownloading() {
        return downloadThread != null && downloadThread.isAlive() && !cancelled;
    }

    private void postError(Handler handler, final DownloadCallback callback, final String error) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }
}
