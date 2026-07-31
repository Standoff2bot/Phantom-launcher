package app.revanced.extension.gamehub.componentstore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a component repository source (GitHub releases, direct URLs, etc.)
 * with metadata about what components it provides, compatibility, and how to
 * fetch/parse releases.
 */
public class ComponentRepository {
    public final String id;
    public final String name;
    public final String category;
    public final String description;
    public final int componentType;
    public final List<String> gpuCompatibility;
    public final List<String> architecture;
    public final String apiUrl;
    public final ExtractionPattern extractPattern;
    public final String installPath;
    public final String fileFormat;
    public final ValidationRules validationRules;
    public final JSONObject metadata;

    public ComponentRepository(JSONObject json) throws Exception {
        this.id = json.getString("id");
        this.name = json.getString("name");
        this.category = json.getString("category");
        this.description = json.getString("description");
        this.componentType = json.getInt("componentType");
        
        this.gpuCompatibility = new ArrayList<>();
        JSONArray gpus = json.getJSONArray("gpuCompatibility");
        for (int i = 0; i < gpus.length(); i++) {
            this.gpuCompatibility.add(gpus.getString(i));
        }
        
        this.architecture = new ArrayList<>();
        JSONArray archs = json.getJSONArray("architecture");
        for (int i = 0; i < archs.length(); i++) {
            this.architecture.add(archs.getString(i));
        }
        
        this.apiUrl = json.getString("apiUrl");
        this.extractPattern = new ExtractionPattern(json.getJSONObject("extractPattern"));
        this.installPath = json.getString("installPath");
        this.fileFormat = json.getString("fileFormat");
        this.validationRules = new ValidationRules(json.getJSONObject("validationRules"));
        this.metadata = json.optJSONObject("metadata");
    }

    public static class ExtractionPattern {
        public final boolean multiple;
        public final String tagName;
        public final String assets;
        public final String downloadUrl;
        public final String fileName;
        public final String filter;
        public final String fallback;
        public final String directDownload;

        public ExtractionPattern(JSONObject json) throws Exception {
            this.multiple = json.optBoolean("multiple", false);
            this.tagName = json.optString("tagName", null);
            this.assets = json.optString("assets", null);
            this.downloadUrl = json.optString("downloadUrl", null);
            this.fileName = json.optString("fileName", null);
            this.filter = json.optString("filter", null);
            this.fallback = json.optString("fallback", null);
            this.directDownload = json.optString("directDownload", null);
        }
    }

    public static class ValidationRules {
        public final List<String> requiredFiles;
        public final long minSize;
        public final String checksumUrl;

        public ValidationRules(JSONObject json) throws Exception {
            this.requiredFiles = new ArrayList<>();
            JSONArray files = json.optJSONArray("requiredFiles");
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    this.requiredFiles.add(files.getString(i));
                }
            }
            this.minSize = json.optLong("minSize", 0);
            this.checksumUrl = json.optString("checksumUrl", null);
        }
    }

    /**
     * Check if this repository's components are compatible with the device GPU.
     */
    public boolean isGpuCompatible(String deviceGpu) {
        if (gpuCompatibility.contains("generic")) return true;
        return gpuCompatibility.contains(deviceGpu.toLowerCase());
    }

    /**
     * Check if this repository provides components for the device architecture.
     */
    public boolean isArchitectureCompatible(String deviceArch) {
        return architecture.contains(deviceArch.toLowerCase());
    }

    /**
     * Get warning message if there are compatibility concerns.
     */
    public String getCompatibilityWarning(String deviceGpu, String deviceArch) {
        if (metadata != null && metadata.has("note")) {
            return metadata.optString("note");
        }
        
        if (!isGpuCompatible(deviceGpu)) {
            return "⚠️ This component may not be compatible with your GPU (" + deviceGpu + ")";
        }
        
        if (!isArchitectureCompatible(deviceArch)) {
            return "⚠️ This component may not support your architecture (" + deviceArch + ")";
        }
        
        return null;
    }
}
