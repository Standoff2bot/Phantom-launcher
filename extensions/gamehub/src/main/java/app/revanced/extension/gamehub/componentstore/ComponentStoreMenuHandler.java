package app.revanced.extension.gamehub.componentstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

/**
 * Handler for Component Store menu integration and validation checks.
 * Provides two main functions:
 * 1. Launch Component Store from Banner Tools
 * 2. Validate Wine installation before game launch
 */
public class ComponentStoreMenuHandler {
    private static final String TAG = "BH-ComponentStoreMenu";
    
    /**
     * Launch Component Store activity from any context.
     * Called when user taps "Component Store" in Banner Tools.
     */
    public static void launchComponentStore(Context context) {
        try {
            Intent intent = new Intent();
            intent.setClassName(
                context.getPackageName(),
                "app.revanced.extension.gamehub.componentstore.ComponentStoreActivity"
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "Launched Component Store");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch Component Store", e);
        }
    }
    
    /**
     * Check if Wine/Proton container is installed.
     * Returns true if at least one Wine container exists in bh_components/wine/
     */
    public static boolean isWineInstalled(Context context) {
        File wineDir = new File(context.getFilesDir(), "bh_components/wine");
        if (!wineDir.exists() || !wineDir.isDirectory()) {
            return false;
        }
        
        File[] containers = wineDir.listFiles();
        if (containers == null || containers.length == 0) {
            return false;
        }
        
        // Check if at least one container has wine/bin/wine executable
        for (File container : containers) {
            File wineExe = new File(container, "bin/wine");
            if (wineExe.exists()) {
                Log.i(TAG, "Found Wine container: " + container.getName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Show dialog prompting user to install Wine components.
     * Called when game launch is attempted without Wine installed.
     */
    public static void showWineRequiredDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(activity)
                    .setTitle("Wine Container Required")
                    .setMessage("PC games require a Wine/Proton container to run.\n\n" +
                              "The Component Store can install:\n" +
                              "• Wine/Proton containers\n" +
                              "• DXVK (DirectX 9/10/11)\n" +
                              "• VKD3D-Proton (DirectX 12)\n" +
                              "• Box64/FEX (x86-64 emulation)\n\n" +
                              "Would you like to open the Component Store now?")
                    .setPositiveButton("Open Component Store", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            launchComponentStore(activity);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .setCancelable(true)
                    .show();
                
                Log.i(TAG, "Showed Wine required dialog");
            }
        });
    }
    
    /**
     * Validate Wine installation before game launch.
     * Returns true if Wine is installed, false otherwise.
     * If false, shows installation dialog.
     */
    public static boolean validateWineOrPrompt(Activity activity) {
        if (isWineInstalled(activity)) {
            return true;
        }
        
        Log.w(TAG, "Wine not installed - showing prompt");
        showWineRequiredDialog(activity);
        return false;
    }
    
    /**
     * Get Wine installation status summary for debugging.
     */
    public static String getWineStatusSummary(Context context) {
        File wineDir = new File(context.getFilesDir(), "bh_components/wine");
        
        if (!wineDir.exists()) {
            return "Wine directory not found";
        }
        
        File[] containers = wineDir.listFiles();
        if (containers == null || containers.length == 0) {
            return "No Wine containers installed";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Installed Wine containers:\n");
        for (File container : containers) {
            File wineExe = new File(container, "bin/wine");
            if (wineExe.exists()) {
                summary.append("✓ ").append(container.getName()).append("\n");
            } else {
                summary.append("✗ ").append(container.getName()).append(" (incomplete)\n");
            }
        }
        
        return summary.toString();
    }
}
