package app.revanced.patches.gamehub.componentstore

import app.revanced.patcher.patch.resourcePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION
import app.revanced.util.getNode
import org.w3c.dom.Element

// ============================================================================
// BannerHub Component Store - Manifest registration
//
// Registers ComponentStoreActivity to enable dynamic component repository
// browsing, downloading, and installation. This allows users to install
// latest versions of DXVK, Mesa Turnip, VKD3D, Wine/Proton, FEX-Emu, Box64,
// and other components from GitHub releases without app updates.
//
// The activity is exported=true to allow direct launch via adb for testing:
//   adb shell am start -n <pkg>/app.revanced.extension.gamehub.componentstore.ComponentStoreActivity
//
// Production integration: Add a "Component Store" row to Banner Tools or
// Profile screen (similar to GOG integration pattern).
// ============================================================================

private const val PKG = "app.revanced.extension.gamehub.componentstore"
private const val ACTIVITY = "$PKG.ComponentStoreActivity"

@Suppress("unused")
val componentStoreManifestPatch = resourcePatch(
    name = "Component Store manifest registration",
    description = "Registers ComponentStoreActivity for dynamic component " +
        "repository browsing and installation. Enables downloading latest " +
        "DXVK, Mesa, Wine, Proton, FEX, Box64 releases from official repos " +
        "with automatic offline component picker integration.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    apply {
        document("AndroidManifest.xml").use { dom ->
            val app = dom.getNode("application") as Element

            // Check if already registered (idempotent)
            val nodes = app.getElementsByTagName("activity")
            var exists = false
            for (i in 0 until nodes.length) {
                if ((nodes.item(i) as Element).getAttribute("android:name") == ACTIVITY) {
                    exists = true
                    break
                }
            }

            if (!exists) {
                val activity = dom.createElement("activity").apply {
                    setAttribute("android:name", ACTIVITY)
                    setAttribute("android:exported", "true") // Allow adb launch
                    setAttribute("android:theme", "@android:style/Theme.Black.NoTitleBar")
                    setAttribute(
                        "android:configChanges",
                        "orientation|screenSize|keyboardHidden",
                    )
                    // Follow device orientation (same as GOG activities)
                    setAttribute("android:screenOrientation", "behind")
                }
                app.appendChild(activity)
            }

            // Ensure INTERNET permission (required for GitHub API fetching)
            val requiredPerms = listOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
            )

            val perms = dom.documentElement.getElementsByTagName("uses-permission")
            val existingPerms = HashSet<String>()
            for (i in 0 until perms.length) {
                existingPerms.add((perms.item(i) as Element).getAttribute("android:name"))
            }

            for (perm in requiredPerms) {
                if (perm !in existingPerms) {
                    val permElem = dom.createElement("uses-permission").apply {
                        setAttribute("android:name", perm)
                    }
                    dom.documentElement.appendChild(permElem)
                }
            }
        }
    }
}
