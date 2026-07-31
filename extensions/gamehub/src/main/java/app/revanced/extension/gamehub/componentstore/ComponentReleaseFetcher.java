package app.revanced.extension.gamehub.componentstore;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fetches available component releases from a ComponentRepository's API URL.
 * Supports GitHub API JSON parsing with configurable extraction patterns.
 */
public class ComponentReleaseFetcher {
    private static final String TAG = "BH-ReleaseFetcher";
    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "BannerHub-ComponentStore/1.0";

    /**
     * Fetch available releases from a repository.
     * 
     * @param repo The repository to fetch from
     * @return List of ComponentRelease objects
     */
    public static List<ComponentRelease> fetchReleases(ComponentRepository repo) {
        List<ComponentRelease> releases = new ArrayList<>();
        
        try {
            String json = fetchJson(repo.apiUrl);
            if (json == null) return releases;
            
            // Determine if response is array (multiple releases) or object (single)
            if (json.trim().startsWith("[")) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    ComponentRelease rel = parseRelease(repo, array.getJSONObject(i));
                    if (rel != null) releases.add(rel);
                    
                    // Limit to avoid excessive parsing
                    if (releases.size() >= 20) break;
                }
            } else {
                JSONObject obj = new JSONObject(json);
                ComponentRelease rel = parseRelease(repo, obj);
                if (rel != null) releases.add(rel);
            }
            
            Log.i(TAG, "Fetched " + releases.size() + " releases for " + repo.id);
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch releases for " + repo.id, e);
        }
        
        return releases;
    }

    /**
     * Parse a single release object according to repository's extraction pattern.
     */
    private static ComponentRelease parseRelease(ComponentRepository repo, JSONObject releaseObj) {
        try {
            ComponentRepository.ExtractionPattern pattern = repo.extractPattern;
            
            // Extract version/tag
            String version = null;
            if (pattern.tagName != null) {
                version = releaseObj.optString(pattern.tagName, null);
            }
            if (version == null) {
                version = releaseObj.optString("tag_name", releaseObj.optString("name", "unknown"));
            }
            
            // Extract download URL
            String downloadUrl = null;
            long fileSize = 0;
            String fileName = null;
            
            // Direct download URL (tarball_url, zipball_url)
            if (pattern.directDownload != null) {
                downloadUrl = releaseObj.optString(pattern.directDownload, null);
                fileName = version + "." + repo.fileFormat;
            }
            // Assets array with filtering
            else if (pattern.assets != null) {
                JSONArray assets = releaseObj.optJSONArray(pattern.assets);
                if (assets != null) {
                    Pattern filterPattern = null;
                    Pattern fallbackPattern = null;
                    
                    if (pattern.filter != null) {
                        filterPattern = Pattern.compile(pattern.filter);
                    }
                    if (pattern.fallback != null) {
                        fallbackPattern = Pattern.compile(pattern.fallback);
                    }
                    
                    // Try primary filter first
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString(pattern.fileName != null ? pattern.fileName : "name");
                        
                        if (filterPattern != null && filterPattern.matcher(name).find()) {
                            downloadUrl = asset.optString(pattern.downloadUrl != null ? pattern.downloadUrl : "browser_download_url");
                            fileSize = asset.optLong("size", 0);
                            fileName = name;
                            break;
                        }
                    }
                    
                    // Fallback pattern if primary didn't match
                    if (downloadUrl == null && fallbackPattern != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString(pattern.fileName != null ? pattern.fileName : "name");
                            
                            if (fallbackPattern.matcher(name).find()) {
                                downloadUrl = asset.optString(pattern.downloadUrl != null ? pattern.downloadUrl : "browser_download_url");
                                fileSize = asset.optLong("size", 0);
                                fileName = name;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                Log.w(TAG, "No download URL found for " + repo.id + " " + version);
                return null;
            }
            
            // Extract description/notes
            String description = releaseObj.optString("body", "");
            if (description.length() > 200) {
                description = description.substring(0, 197) + "...";
            }
            
            return new ComponentRelease(
                repo.id,
                version,
                downloadUrl,
                fileName != null ? fileName : "download." + repo.fileFormat,
                fileSize,
                description,
                repo.componentType,
                releaseObj.optString("published_at", null)
            );
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse release from " + repo.id, e);
            return null;
        }
    }

    /**
     * Fetch JSON from URL with proper headers and timeout.
     */
    private static String fetchJson(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "HTTP " + code + " for " + urlString);
                return null;
            }
            
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            
            return sb.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch " + urlString, e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Represents a single component release available for download.
     */
    public static class ComponentRelease {
        public final String repositoryId;
        public final String version;
        public final String downloadUrl;
        public final String fileName;
        public final long fileSize;
        public final String description;
        public final int componentType;
        public final String publishedAt;

        public ComponentRelease(String repositoryId, String version, String downloadUrl,
                              String fileName, long fileSize, String description,
                              int componentType, String publishedAt) {
            this.repositoryId = repositoryId;
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.description = description;
            this.componentType = componentType;
            this.publishedAt = publishedAt;
        }

        /**
         * Get human-readable file size.
         */
        public String getFileSizeFormatted() {
            if (fileSize <= 0) return "Unknown size";
            
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
            if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
            return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
        }

        /**
         * Generate a unique component name for storage.
         */
        public String getComponentName() {
            // Format: repoId-version (e.g., "dxvk_official-v3.0.2")
            return repositoryId + "-" + version.replaceAll("[^a-zA-Z0-9._-]", "_");
        }
    }
}
