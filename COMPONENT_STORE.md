# BannerHub Component Store

## Overview

**Component Store** is a dynamic repository browser and installer system that allows users to download and install the latest versions of:

- **DXVK** (v3.0.2) - Vulkan-based D3D9/D3D10/D3D11 for Wine
- **VKD3D-Proton** (v3.0.1) - Vulkan-based D3D12 for Wine
- **Mesa Turnip** (v26.0.0-R8) - Vulkan driver for Qualcomm Adreno GPUs
- **VirGL Mesa** - VirtIO OpenGL/Vulkan acceleration
- **Proton-GE** (GE-Proton11-3) - Custom Wine build with patches
- **FEX-Emu** (FEX-2607) - Fast x86-64 emulator for ARM64
- **Box64** (v0.4.2) - x86-64 Linux emulator

Components are fetched from official GitHub releases, validated for architecture/GPU compatibility, and automatically registered with the offline component picker.

## Architecture

```
ComponentStore/
├── ComponentRepository.json         # Repository catalog with GitHub API URLs
├── ComponentRepositoryManager       # Loads and filters repositories
├── ComponentReleaseFetcher          # Fetches releases from GitHub API
├── ComponentDownloader              # Downloads files with progress tracking
├── ComponentInstaller               # Extracts and registers components
├── DeviceInfo                       # GPU/CPU architecture detection
├── MaliOptimizer                    # Mali-G710 optimization layer
└── ComponentStoreActivity           # Main UI
```

## Installation Paths

Components are installed to app internal storage (no root required):

```
/data/data/com.xiaoji.egggame/files/bh_components/
├── dxvk/
│   └── dxvk_official-v3.0.2/
├── turnip/
│   └── turnip_adreno-v26.0.0-rc08/
├── vkd3d/
├── virgl/
├── wine/
├── fex/
└── box64/
```

Installed components are automatically registered in `sp_winemu_unified_resources.xml` with proper EnvLayerEntity format, making them visible in the offline component picker.

## GPU Compatibility

### Mali GPUs (including Mali-G710)

**Compatible:**
- DXVK (all versions)
- VKD3D-Proton
- VirGL Mesa
- Wine/Proton containers
- FEX-Emu, Box64

**NOT Compatible:**
- **Mesa Turnip** - This is Qualcomm Adreno-only. The app will show a warning and hide Turnip repositories on Mali devices.

### Adreno GPUs (Qualcomm)

All components compatible, including Mesa Turnip drivers.

## Mali-G710 Optimizations

The `MaliOptimizer` class provides Mali-specific environment variables and configuration:

### Environment Variables Applied

```bash
# Mesa tuning
MESA_NO_ERROR=1
MESA_GLSL_CACHE_DISABLE=false
MESA_GLTHREAD=true

# Mali-G710 AFBC workaround
PANFROST_NO_AFBC=1                # Disable AFBC (fixes texture corruption)
PAN_MESA_DEBUG=sync               # Sync mode for stability

# Vulkan optimizations
MESA_VK_WSI_PRESENT_MODE=mailbox  # Reduce latency
vblank_mode=0                     # Disable vsync
```

### DXVK Configuration

Generated `dxvk.conf` for Mali:

```ini
# Memory management
dxvk.maxDeviceMemory = 2048
dxvk.maxSharedMemory = 512

# Pipeline optimizations
dxgi.maxFrameLatency = 1
dxgi.syncInterval = 0

# Shader compilation
dxvk.useAsync = true
dxvk.numCompilerThreads = 4

# Mali-G710 specific
d3d11.relaxedBarriers = true
dxvk.enableGraphicsPipelineLibrary = false
```

## Usage

### Launch Component Store

```bash
# Via adb (for testing)
adb shell am start -n com.xiaoji.egggame/app.revanced.extension.gamehub.componentstore.ComponentStoreActivity

# In production: Add "Component Store" row to Banner Tools or Profile screen
```

### Install a Component

1. Open Component Store
2. Browse repositories (filtered by GPU compatibility)
3. Select a repository (e.g., "DXVK (Official)")
4. Browse releases
5. Select a version
6. Download & Install

The component will be:
- Downloaded to cache
- Extracted to `/files/bh_components/[category]/[name]`
- Validated (architecture, required files, size)
- Registered in `sp_winemu_unified_resources.xml`
- Available in offline component picker immediately

### Manual Component Upload (Not Yet Implemented)

For custom/local components, future enhancement:

```java
// Add custom component from local file
ComponentCustomUploader.upload(context, 
    new File("/sdcard/my-dxvk-build.zip"),
    ComponentType.DXVK,
    "Custom DXVK Build");
```

## Repository Catalog Format

`extensions/gamehub/src/main/assets/component_repositories.json`:

```json
{
  "repositories": [
    {
      "id": "dxvk_official",
      "name": "DXVK (Official)",
      "category": "renderer",
      "componentType": 4,
      "gpuCompatibility": ["adreno", "mali", "generic"],
      "architecture": ["aarch64", "x86_64"],
      "apiUrl": "https://api.github.com/repos/doitsujin/dxvk/releases/latest",
      "extractPattern": {
        "tagName": "tag_name",
        "assets": "assets",
        "downloadUrl": "browser_download_url",
        "filter": ".*\\.tar\\.gz$"
      },
      "installPath": "dxvk",
      "fileFormat": "tar.gz",
      "validationRules": {
        "requiredFiles": ["x64/d3d9.dll", "x64/dxgi.dll"],
        "minSize": 1048576
      }
    }
  ]
}
```

## Integration with Existing Systems

### Offline Component Picker

Components installed via Component Store are automatically visible in:

1. **GPU Driver Picker** (ComponentType = 1)
2. **DXVK Picker** (ComponentType = 4)
3. **VKD3D Picker** (ComponentType = 5)
4. **Wine Container Picker** (ComponentType = 8)

The `OfflineComponentList.java` synthesizer reads `sp_winemu_unified_resources.xml` and includes both:
- Official GameHub catalog entries
- User-installed Component Store entries

### Order and Sorting

Custom components follow `OfflineComponentOrder.java` ranking:
- Known components: Use catalog position
- Custom components: `Integer.MAX_VALUE` (bottom of list)

## Known Limitations

1. **tar.zst format**: Not yet supported (requires zstd library). Affects vkd3d-proton v3.0.1.
   - **Workaround**: Download manually and extract, or add Apache Commons Compress dependency.

2. **tar.gz extraction**: Basic implementation (single-file GZIP decompression). Full tar parsing requires external library.
   - **Workaround**: Most GitHub releases provide ZIP alternatives.

3. **FEX-Emu/Box64**: Source-only releases require building from source.
   - **Workaround**: Look for pre-built binaries from community forks.

4. **Mali Panfrost driver**: Mali-G710 is not supported by Panfrost driver (only G610/G720).
   - **Workaround**: Use Android's stock Mali driver with Mesa environment tuning.

## Security

- **Zip Slip Protection**: Path traversal checks during extraction
- **Size Validation**: Minimum file size checks before installation
- **Network Security**: HTTPS-only, proper User-Agent, timeout enforcement
- **Permissions**: No root required - uses app internal storage

## Testing

```bash
# Build APK with Component Store
./gradlew build

# Install on device
adb install -r app-release.apk

# Launch Component Store
adb shell am start -n com.xiaoji.egggame/app.revanced.extension.gamehub.componentstore.ComponentStoreActivity

# Check installed components
adb shell cat /data/data/com.xiaoji.egggame/shared_prefs/sp_winemu_unified_resources.xml

# Verify Mali optimizations
adb shell cat /data/data/com.xiaoji.egggame/files/bh_offline_list.log
```

## Future Enhancements

1. **Custom Component Upload**: File picker for local ZIP/tar files
2. **Component Manager**: View/delete installed custom components
3. **Automatic Updates**: Check for newer versions of installed components
4. **Validation Signatures**: SHA256 checksum verification
5. **tar.zst Support**: Add zstd decompression
6. **Full TAR Parsing**: Apache Commons Compress integration
7. **Banner Tools Integration**: Add "Component Store" row to consolidated dialog
8. **Mali Driver Auto-Detection**: Panfrost vs stock driver selection

## Troubleshooting

### "No compatible repositories found"

- Check GPU detection: Should show "Mali" or "Adreno"
- Architecture should be "aarch64" for ARM64

### "Download failed: HTTP 403/404"

- GitHub API rate limit (60 req/hour unauthenticated)
- Wait 1 hour or add GitHub API token (not implemented)

### "Extraction failed"

- File format not supported (tar.zst)
- Corrupted download (re-download)

### "Component installed but not registered"

- SharedPreferences write failed (rare)
- Manually add to `sp_winemu_unified_resources.xml`

### Turnip drivers shown on Mali device

- Bug in GPU detection
- Check DeviceInfo logs: `adb logcat | grep BH-DeviceInfo`

## Credits

- **DXVK**: https://github.com/doitsujin/dxvk
- **VKD3D-Proton**: https://github.com/HansKristian-Work/vkd3d-proton
- **Mesa Turnip**: https://github.com/K11MCH1/AdrenoToolsDrivers
- **VirGL**: https://github.com/alexvorxx/Mesa-VirGL
- **Proton-GE**: https://github.com/GloriousEggroll/proton-ge-custom
- **FEX-Emu**: https://github.com/FEX-Emu/FEX
- **Box64**: https://github.com/ptitSeb/box64

## License

Component Store system: Part of BannerHub ReVanced (same license as base project)

Downloaded components: Retain their original licenses (check individual repositories)
