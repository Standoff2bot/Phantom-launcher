package app.revanced.extension.gamehub.componentstore;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the catalog of component repositories loaded from
 * assets/component_repositories.json. Provides filtering by category,
 * GPU compatibility, and architecture.
 */
public class ComponentRepositoryManager {
    private static final String TAG = "BH-ComponentRepoMgr";
    private static final String CATALOG_FILE = "component_repositories.json";
    
    private final List<ComponentRepository> repositories = new ArrayList<>();
    private JSONObject metadata;
    
    private static ComponentRepositoryManager instance;

    private ComponentRepositoryManager() {}

    public static synchronized ComponentRepositoryManager getInstance() {
        if (instance == null) {
            instance = new ComponentRepositoryManager();
        }
        return instance;
    }

    /**
     * Load repository catalog from assets. Call once at app startup or when
     * ComponentStore is opened.
     */
    public synchronized void loadCatalog(Context context) {
        if (!repositories.isEmpty()) return; // already loaded
        
        try {
            InputStream is = context.getAssets().open(CATALOG_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            JSONObject root = new JSONObject(sb.toString());
            this.metadata = root.optJSONObject("metadata");
            
            JSONArray repos = root.getJSONArray("repositories");
            for (int i = 0; i < repos.length(); i++) {
                try {
                    ComponentRepository repo = new ComponentRepository(repos.getJSONObject(i));
                    repositories.add(repo);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse repository entry " + i + ": " + e);
                }
            }
            
            Log.i(TAG, "Loaded " + repositories.size() + " repositories from catalog");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load component repository catalog", e);
        }
    }

    /**
     * Get all repositories.
     */
    public List<ComponentRepository> getAllRepositories() {
        return new ArrayList<>(repositories);
    }

    /**
     * Filter repositories by category.
     */
    public List<ComponentRepository> getByCategory(String category) {
        List<ComponentRepository> result = new ArrayList<>();
        for (ComponentRepository repo : repositories) {
            if (repo.category.equalsIgnoreCase(category)) {
                result.add(repo);
            }
        }
        return result;
    }

    /**
     * Filter repositories compatible with device GPU and architecture.
     */
    public List<ComponentRepository> getCompatible(String deviceGpu, String deviceArch) {
        List<ComponentRepository> result = new ArrayList<>();
        for (ComponentRepository repo : repositories) {
            if (repo.isGpuCompatible(deviceGpu) && repo.isArchitectureCompatible(deviceArch)) {
                result.add(repo);
            }
        }
        return result;
    }

    /**
     * Get repository by ID.
     */
    public ComponentRepository getById(String id) {
        for (ComponentRepository repo : repositories) {
            if (repo.id.equals(id)) {
                return repo;
            }
        }
        return null;
    }

    /**
     * Get metadata from catalog (version, categories, notes).
     */
    public JSONObject getMetadata() {
        return metadata;
    }

    /**
     * Get all available categories.
     */
    public List<String> getCategories() {
        List<String> cats = new ArrayList<>();
        for (ComponentRepository repo : repositories) {
            if (!cats.contains(repo.category)) {
                cats.add(repo.category);
            }
        }
        return cats;
    }
}
