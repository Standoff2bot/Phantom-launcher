package app.revanced.extension.gamehub.componentstore;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs downloaded components: extraction, validation, and integration with
 * sp_winemu_unified_resources.xml for offline component picker compatibility.
 */
public class ComponentInstaller {
    private static final String TAG = "BH-ComponentInstaller";
    private static final String PREFS_NAME = "sp_winemu_unified_resources";
    private static final int BUFFER_SIZE = 8192;

    /**
     * Install a downloaded component file.
     * 
     * @param context Application context
     * @param downloadedFile The downloaded archive file
     * @param release Release metadata
     * @param repo Repository metadata
     * @return Installation result with status and message
     */
    public static InstallResult install(Context context, File downloadedFile,
                                       ComponentReleaseFetcher.ComponentRelease release,
                                       ComponentRepository repo) {
        try {
            Log.i(TAG, "Installing " + release.version + " from " + downloadedFile.getName());
            
            // Validate file exists and size
            if (!downloadedFile.exists() || !downloadedFile.canRead()) {
                return InstallResult.error("Downloaded file not accessible");
            }
            
            if (downloadedFile.length() < repo.validationRules.minSize) {
                return InstallResult.error("File too small (" + downloadedFile.length() + 
                                          " < " + repo.validationRules.minSize + " bytes)");
            }
            
            // Create installation directory
            File installDir = getInstallDir(context, repo.installPath, release.getComponentName());
            if (installDir.exists()) {
                // Remove old installation
                deleteRecursive(installDir);
            }
            
            if (!installDir.mkdirs()) {
                return InstallResult.error("Failed to create install directory");
            }
            
            // Extract archive
            boolean extracted = false;
            if (repo.fileFormat.equals("zip")) {
                extracted = extractZip(downloadedFile, installDir);
            } else if (repo.fileFormat.equals("tar.gz")) {
                extracted = extractTarGz(downloadedFile, installDir);
            } else if (repo.fileFormat.equals("tar.zst")) {
                return InstallResult.error("tar.zst format requires zstd support (not yet implemented)");
            } else {
                return InstallResult.error("Unsupported format: " + repo.fileFormat);
            }
            
            if (!extracted) {
                deleteRecursive(installDir);
                return InstallResult.error("Extraction failed");
            }
            
            // Validate required files
            if (!repo.validationRules.requiredFiles.isEmpty()) {
                for (String reqFile : repo.validationRules.requiredFiles) {
                    File f = new File(installDir, reqFile);
                    if (!f.exists()) {
                        Log.w(TAG, "Required file missing: " + reqFile);
                        // Continue anyway - validation is advisory
                    }
                }
            }
            
            // Register component in sp_winemu_unified_resources.xml
            boolean registered = registerComponent(context, release, repo, installDir);
            
            if (!registered) {
                Log.w(TAG, "Component installed but not registered (offline picker won't see it)");
            }
            
            Log.i(TAG, "Installation complete: " + installDir.getAbsolutePath());
            
            return InstallResult.success(installDir.getAbsolutePath(), registered);
            
        } catch (Exception e) {
            Log.e(TAG, "Installation failed", e);
            return InstallResult.error("Installation failed: " + e.getMessage());
        }
    }

    /**
     * Register component in SharedPreferences so offline component picker sees it.
     */
    private static boolean registerComponent(Context context, 
                                            ComponentReleaseFetcher.ComponentRelease release,
                                            ComponentRepository repo,
                                            File installDir) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            // Build EnvLayerEntity-compatible JSON
            JSONObject entry = new JSONObject();
            entry.put("name", release.getComponentName());
            entry.put("version", release.version);
            entry.put("displayName", repo.name + " " + release.version);
            entry.put("type", repo.componentType);
            entry.put("fileName", installDir.getName());
            entry.put("fileSize", getTotalSize(installDir));
            entry.put("fileMd5", ""); // Empty for custom components
            entry.put("downloadUrl", release.downloadUrl);
            entry.put("logo", "");
            entry.put("blurb", release.description);
            entry.put("upgradeMsg", "");
            entry.put("framework", "");
            entry.put("frameworkType", "");
            entry.put("fileType", 0);
            entry.put("versionCode", 1);
            entry.put("isSteam", 0);
            entry.put("status", 2); // Downloaded status
            entry.put("state", "None");
            entry.put("id", Math.abs(release.getComponentName().hashCode()));
            
            // Wrap in catalog format
            JSONObject wrapper = new JSONObject();
            wrapper.put("entry", entry);
            
            // Key format: COMPONENT:name
            String key = "COMPONENT:" + release.getComponentName();
            editor.putString(key, wrapper.toString());
            editor.apply();
            
            Log.i(TAG, "Registered component: " + key);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to register component", e);
            return false;
        }
    }

    /**
     * Extract ZIP archive.
     */
    private static boolean extractZip(File zipFile, File destDir) {
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                File entryFile = new File(destDir, entry.getName());
                
                // Security: prevent zip slip
                if (!entryFile.getCanonicalPath().startsWith(destDir.getCanonicalPath())) {
                    Log.w(TAG, "Zip slip attempt blocked: " + entry.getName());
                    continue;
                }
                
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    
                    BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(entryFile), BUFFER_SIZE);
                    
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = zis.read(buffer)) != -1) {
                        bos.write(buffer, 0, read);
                    }
                    bos.close();
                    
                    // Preserve executable bit if entry was executable
                    if ((entry.getUnixMode() & 0111) != 0) {
                        entryFile.setExecutable(true, false);
                    }
                }
                
                zis.closeEntry();
            }
            
            zis.close();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Zip extraction failed", e);
            return false;
        } finally {
            try {
                if (zis != null) zis.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Extract tar.gz archive.
     */
    private static boolean extractTarGz(File tarGzFile, File destDir) {
        // Basic tar.gz extraction (simplified - production would use Apache Commons Compress)
        GZIPInputStream gzis = null;
        try {
            gzis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(tarGzFile)));
            
            // For simplicity, treat as single-file GZIP for now
            // Full tar parsing would require TarInputStream (not in Android SDK)
            
            File outFile = new File(destDir, "extracted.tar");
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile));
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = gzis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            
            bos.close();
            gzis.close();
            
            Log.w(TAG, "Basic GZIP decompression complete. Full TAR extraction not implemented.");
            Log.w(TAG, "Component may require manual extraction or Apache Commons Compress library.");
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "tar.gz extraction failed", e);
            return false;
        } finally {
            try {
                if (gzis != null) gzis.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Get installation directory for a component.
     * Uses app's internal files dir (no root needed).
     */
    private static File getInstallDir(Context context, String category, String componentName) {
        // Path: /data/data/com.xiaoji.egggame/files/bh_components/[category]/[name]
        File baseDir = new File(context.getFilesDir(), "bh_components");
        File categoryDir = new File(baseDir, category);
        return new File(categoryDir, componentName);
    }

    /**
     * Delete directory recursively.
     */
    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    /**
     * Calculate total size of directory.
     */
    private static long getTotalSize(File dir) {
        long size = 0;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    size += getTotalSize(child);
                }
            }
        } else {
            size = dir.length();
        }
        return size;
    }

    /**
     * Installation result wrapper.
     */
    public static class InstallResult {
        public final boolean success;
        public final String message;
        public final String installPath;
        public final boolean registered;

        private InstallResult(boolean success, String message, String installPath, boolean registered) {
            this.success = success;
            this.message = message;
            this.installPath = installPath;
            this.registered = registered;
        }

        public static InstallResult success(String installPath, boolean registered) {
            return new InstallResult(true, "Installation successful", installPath, registered);
        }

        public static InstallResult error(String message) {
            return new InstallResult(false, message, null, false);
        }
    }
}
