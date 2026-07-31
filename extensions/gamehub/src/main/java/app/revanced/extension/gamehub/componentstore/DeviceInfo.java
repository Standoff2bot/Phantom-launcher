package app.revanced.extension.gamehub.componentstore;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Detects device hardware information: GPU vendor/model and CPU architecture.
 * Used for component compatibility filtering.
 */
public class DeviceInfo {
    private static final String TAG = "BH-DeviceInfo";
    
    private static String cachedGpu = null;
    private static String cachedArch = null;

    /**
     * Get GPU vendor/family (adreno, mali, generic).
     */
    public static String getGpuFamily() {
        if (cachedGpu != null) return cachedGpu;
        
        try {
            // Method 1: Check /proc/cpuinfo for GPU hints
            String cpuinfo = readFile("/proc/cpuinfo");
            if (cpuinfo != null) {
                if (cpuinfo.toLowerCase().contains("adreno")) {
                    cachedGpu = "adreno";
                    return cachedGpu;
                }
                if (cpuinfo.toLowerCase().contains("mali")) {
                    cachedGpu = "mali";
                    return cachedGpu;
                }
            }
            
            // Method 2: Check /sys/class/kgsl/kgsl-3d0/gpu_model (Adreno)
            String kgslModel = readFile("/sys/class/kgsl/kgsl-3d0/gpu_model");
            if (kgslModel != null && !kgslModel.isEmpty()) {
                cachedGpu = "adreno";
                Log.i(TAG, "Detected Adreno GPU: " + kgslModel.trim());
                return cachedGpu;
            }
            
            // Method 3: Check /sys/devices for mali
            File[] sysDevices = new File("/sys/devices").listFiles();
            if (sysDevices != null) {
                for (File dev : sysDevices) {
                    if (dev.getName().contains("mali") || dev.getName().contains("gpu")) {
                        String devName = readFile(new File(dev, "uevent").getAbsolutePath());
                        if (devName != null && devName.toLowerCase().contains("mali")) {
                            cachedGpu = "mali";
                            Log.i(TAG, "Detected Mali GPU from " + dev.getName());
                            return cachedGpu;
                        }
                    }
                }
            }
            
            // Method 4: Build.HARDWARE hints
            String hardware = Build.HARDWARE.toLowerCase();
            if (hardware.contains("qcom") || hardware.contains("qualcomm")) {
                cachedGpu = "adreno";
                return cachedGpu;
            }
            if (hardware.contains("exynos") || hardware.contains("kirin") || 
                hardware.contains("mediatek") || hardware.contains("mt")) {
                cachedGpu = "mali";
                return cachedGpu;
            }
            
        } catch (Exception e) {
            Log.w(TAG, "GPU detection failed", e);
        }
        
        // Fallback
        cachedGpu = "generic";
        Log.i(TAG, "GPU family unknown, using generic");
        return cachedGpu;
    }

    /**
     * Get CPU architecture (aarch64, x86_64, etc).
     */
    public static String getArchitecture() {
        if (cachedArch != null) return cachedArch;
        
        try {
            // Primary: os.arch system property
            String osArch = System.getProperty("os.arch");
            if (osArch != null) {
                osArch = osArch.toLowerCase();
                if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                    cachedArch = "aarch64";
                    return cachedArch;
                }
                if (osArch.contains("x86_64") || osArch.contains("amd64")) {
                    cachedArch = "x86_64";
                    return cachedArch;
                }
                if (osArch.contains("arm")) {
                    cachedArch = "arm";
                    return cachedArch;
                }
            }
            
            // Fallback: Build.SUPPORTED_ABIS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String[] abis = Build.SUPPORTED_ABIS;
                if (abis != null && abis.length > 0) {
                    String primary = abis[0].toLowerCase();
                    if (primary.contains("arm64") || primary.contains("aarch64")) {
                        cachedArch = "aarch64";
                        return cachedArch;
                    }
                    if (primary.contains("x86_64")) {
                        cachedArch = "x86_64";
                        return cachedArch;
                    }
                }
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Architecture detection failed", e);
        }
        
        // Default fallback for Android
        cachedArch = "aarch64";
        Log.i(TAG, "Architecture detection uncertain, assuming aarch64");
        return cachedArch;
    }

    /**
     * Get detailed GPU model if detectable (e.g., "Mali-G710", "Adreno 730").
     */
    public static String getGpuModel() {
        try {
            // Adreno: /sys/class/kgsl/kgsl-3d0/gpu_model
            String kgslModel = readFile("/sys/class/kgsl/kgsl-3d0/gpu_model");
            if (kgslModel != null && !kgslModel.isEmpty()) {
                return "Adreno " + kgslModel.trim();
            }
            
            // Mali: try to parse from /proc/mali/version or device tree
            String maliVer = readFile("/proc/mali/version");
            if (maliVer != null && !maliVer.isEmpty()) {
                return "Mali " + maliVer.trim();
            }
            
        } catch (Exception e) {
            Log.w(TAG, "GPU model detection failed", e);
        }
        
        return getGpuFamily();
    }

    /**
     * Check if device has Mali-G710 specifically (user's GPU from memory).
     */
    public static boolean isMaliG710() {
        String model = getGpuModel().toLowerCase();
        return model.contains("mali") && (model.contains("g710") || model.contains("g-710"));
    }

    private static String readFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return null;
            
            BufferedReader reader = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get device summary string for display.
     */
    public static String getDeviceSummary() {
        return "GPU: " + getGpuModel() + " | Arch: " + getArchitecture();
    }
}
