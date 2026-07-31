package app.revanced.extension.gamehub.componentstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * BannerHub Component Store - Dynamic component repository browser and installer.
 * 
 * Provides UI for:
 * - Browsing available component repositories
 * - Viewing releases from GitHub/direct sources
 * - Downloading and installing components
 * - Device compatibility filtering
 * 
 * Components are installed to app internal storage (no root required) and
 * automatically registered in sp_winemu_unified_resources.xml for offline
 * component picker integration.
 */
public class ComponentStoreActivity extends Activity {
    private static final String TAG = "BH-ComponentStore";
    
    private LinearLayout mainLayout;
    private TextView deviceInfoText;
    private LinearLayout repoListContainer;
    
    private ComponentRepositoryManager repoManager;
    private String deviceGpu;
    private String deviceArch;
    
    private ComponentDownloader currentDownloader;
    private ProgressDialog downloadProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Detect device info
        deviceGpu = DeviceInfo.getGpuFamily();
        deviceArch = DeviceInfo.getArchitecture();
        
        // Initialize repository manager
        repoManager = ComponentRepositoryManager.getInstance();
        repoManager.loadCatalog(this);
        
        // Build UI
        buildUI();
        
        // Load repositories
        loadRepositories();
    }

    private void buildUI() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        mainLayout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        // Title
        TextView title = new TextView(this);
        title.setText("BannerHub Component Store");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        mainLayout.addView(title);
        
        // Device info
        deviceInfoText = new TextView(this);
        deviceInfoText.setText(DeviceInfo.getDeviceSummary());
        deviceInfoText.setTextSize(12);
        deviceInfoText.setGravity(Gravity.CENTER);
        deviceInfoText.setPadding(0, 0, 0, dp(16));
        mainLayout.addView(deviceInfoText);
        
        // Description
        TextView desc = new TextView(this);
        desc.setText("Download and install renderers, GPU drivers, Wine containers, and emulators from official repositories. " +
                    "Components are automatically integrated with the offline component picker.");
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, dp(16));
        mainLayout.addView(desc);
        
        // Repository list container
        repoListContainer = new LinearLayout(this);
        repoListContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(repoListContainer);
        
        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }

    private void loadRepositories() {
        repoListContainer.removeAllViews();
        
        // Show Mali-specific warning if detected
        if (DeviceInfo.isMaliG710()) {
            TextView warning = new TextView(this);
            warning.setText("⚠️ Mali-G710 detected: Turnip drivers are NOT compatible (Adreno-only). " +
                          "DXVK, VKD3D, and Wine components should work normally.");
            warning.setTextSize(13);
            warning.setPadding(dp(8), dp(8), dp(8), dp(8));
            warning.setBackgroundColor(0xFFFFEECC);
            repoListContainer.addView(warning);
            
            addSpacer(repoListContainer, dp(8));
        }
        
        // Get compatible repositories
        List<ComponentRepository> repos = repoManager.getCompatible(deviceGpu, deviceArch);
        
        if (repos.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No compatible repositories found for your device.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, 0);
            repoListContainer.addView(empty);
            return;
        }
        
        // Group by category
        List<String> categories = repoManager.getCategories();
        for (String category : categories) {
            List<ComponentRepository> categoryRepos = new ArrayList<>();
            for (ComponentRepository repo : repos) {
                if (repo.category.equals(category)) {
                    categoryRepos.add(repo);
                }
            }
            
            if (categoryRepos.isEmpty()) continue;
            
            // Category header
            TextView categoryHeader = new TextView(this);
            categoryHeader.setText(getCategoryDisplayName(category));
            categoryHeader.setTextSize(18);
            categoryHeader.setPadding(0, dp(16), 0, dp(8));
            repoListContainer.addView(categoryHeader);
            
            // Repository cards
            for (ComponentRepository repo : categoryRepos) {
                addRepositoryCard(repo);
            }
        }
    }

    private void addRepositoryCard(final ComponentRepository repo) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(0xFFEEEEEE);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);
        
        // Repository name
        TextView name = new TextView(this);
        name.setText(repo.name);
        name.setTextSize(16);
        card.addView(name);
        
        // Description
        TextView desc = new TextView(this);
        desc.setText(repo.description);
        desc.setTextSize(12);
        desc.setPadding(0, dp(4), 0, dp(8));
        card.addView(desc);
        
        // Compatibility warning if any
        String warning = repo.getCompatibilityWarning(deviceGpu, deviceArch);
        if (warning != null) {
            TextView warnText = new TextView(this);
            warnText.setText(warning);
            warnText.setTextSize(11);
            warnText.setPadding(0, 0, 0, dp(8));
            warnText.setTextColor(0xFFFF6600);
            card.addView(warnText);
        }
        
        // Browse button
        Button browseBtn = new Button(this);
        browseBtn.setText("Browse Releases");
        browseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browseRepository(repo);
            }
        });
        card.addView(browseBtn);
        
        repoListContainer.addView(card);
    }

    private void browseRepository(final ComponentRepository repo) {
        final ProgressDialog progress = ProgressDialog.show(this, 
            "Loading", "Fetching releases from " + repo.name + "...", true);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<ComponentReleaseFetcher.ComponentRelease> releases = 
                        ComponentReleaseFetcher.fetchReleases(repo);
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                            if (releases.isEmpty()) {
                                Toast.makeText(ComponentStoreActivity.this, 
                                    "No releases found", Toast.LENGTH_SHORT).show();
                            } else {
                                showReleasesDialog(repo, releases);
                            }
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.dismiss();
                            Toast.makeText(ComponentStoreActivity.this, 
                                "Failed to fetch releases: " + e.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void showReleasesDialog(final ComponentRepository repo, 
                                   final List<ComponentReleaseFetcher.ComponentRelease> releases) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(repo.name + " - Select Release");
        
        final String[] items = new String[releases.size()];
        for (int i = 0; i < releases.size(); i++) {
            ComponentReleaseFetcher.ComponentRelease r = releases.get(i);
            items[i] = r.version + " (" + r.getFileSizeFormatted() + ")";
        }
        
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showReleaseDetailsDialog(repo, releases.get(which));
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showReleaseDetailsDialog(final ComponentRepository repo, 
                                         final ComponentReleaseFetcher.ComponentRelease release) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(release.version);
        
        String message = "Repository: " + repo.name + "\n" +
                        "Version: " + release.version + "\n" +
                        "Size: " + release.getFileSizeFormatted() + "\n" +
                        "Type: " + getComponentTypeName(repo.componentType) + "\n\n" +
                        release.description;
        
        builder.setMessage(message);
        
        builder.setPositiveButton("Download & Install", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                downloadAndInstall(repo, release);
            }
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void downloadAndInstall(final ComponentRepository repo, 
                                   final ComponentReleaseFetcher.ComponentRelease release) {
        // Create download directory
        final File downloadDir = new File(getCacheDir(), "component_downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        // Show progress dialog
        downloadProgressDialog = new ProgressDialog(this);
        downloadProgressDialog.setTitle("Downloading");
        downloadProgressDialog.setMessage(release.version);
        downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadProgressDialog.setMax(100);
        downloadProgressDialog.setCancelable(true);
        downloadProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", 
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (currentDownloader != null) {
                        currentDownloader.cancel();
                    }
                }
            });
        downloadProgressDialog.show();
        
        // Start download
        currentDownloader = new ComponentDownloader();
        currentDownloader.download(release, downloadDir, new ComponentDownloader.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total, int percentage) {
                if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
                    if (percentage >= 0) {
                        downloadProgressDialog.setProgress(percentage);
                    }
                    downloadProgressDialog.setMessage(release.version + "\n" + 
                        formatBytes(downloaded) + (total > 0 ? " / " + formatBytes(total) : ""));
                }
            }

            @Override
            public void onComplete(File downloadedFile) {
                if (downloadProgressDialog != null) {
                    downloadProgressDialog.dismiss();
                }
                
                // Install
                installComponent(repo, release, downloadedFile);
            }

            @Override
            public void onError(String error) {
                if (downloadProgressDialog != null) {
                    downloadProgressDialog.dismiss();
                }
                Toast.makeText(ComponentStoreActivity.this, 
                    "Download failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void installComponent(final ComponentRepository repo, 
                                 final ComponentReleaseFetcher.ComponentRelease release,
                                 final File downloadedFile) {
        final ProgressDialog progress = ProgressDialog.show(this, 
            "Installing", "Extracting and registering component...", true);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ComponentInstaller.InstallResult result = 
                    ComponentInstaller.install(ComponentStoreActivity.this, downloadedFile, release, repo);
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();
                        
                        if (result.success) {
                            String msg = "Installation complete!\n\n" +
                                       "Path: " + result.installPath + "\n\n" +
                                       (result.registered ? 
                                        "✓ Registered with offline component picker" : 
                                        "⚠ Not registered (manual integration needed)");
                            
                            new AlertDialog.Builder(ComponentStoreActivity.this)
                                .setTitle("Success")
                                .setMessage(msg)
                                .setPositiveButton("OK", null)
                                .show();
                        } else {
                            new AlertDialog.Builder(ComponentStoreActivity.this)
                                .setTitle("Installation Failed")
                                .setMessage(result.message)
                                .setPositiveButton("OK", null)
                                .show();
                        }
                        
                        // Clean up downloaded file
                        if (downloadedFile.exists()) {
                            downloadedFile.delete();
                        }
                    }
                });
            }
        }).start();
    }

    private String getCategoryDisplayName(String category) {
        if (category.equals("renderer")) return "🎨 Renderers";
        if (category.equals("gpu_driver")) return "🖥️ GPU Drivers";
        if (category.equals("wine_container")) return "🍷 Wine Containers";
        if (category.equals("emulator")) return "⚙️ Emulators";
        return category;
    }

    private String getComponentTypeName(int type) {
        switch (type) {
            case 1: return "GPU Driver";
            case 2: return "CPU Emulator";
            case 4: return "DXVK Renderer";
            case 5: return "VKD3D Renderer";
            case 8: return "Wine Container";
            default: return "Unknown";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void addSpacer(LinearLayout container, int height) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height
        ));
        container.addView(spacer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentDownloader != null && currentDownloader.isDownloading()) {
            currentDownloader.cancel();
        }
        if (downloadProgressDialog != null && downloadProgressDialog.isShowing()) {
            downloadProgressDialog.dismiss();
        }
    }
}
