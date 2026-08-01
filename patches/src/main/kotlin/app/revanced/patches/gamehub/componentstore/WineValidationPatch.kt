package app.revanced.patches.gamehub.componentstore

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION

// =========================================================================
// Wine Validation Patch — intercepts PcEnginePluginHostActivity.onCreate
// to validate that a Wine/Proton container is installed before launching
// PC games.
//
// If Wine is missing, shows an AlertDialog prompting the user to install
// components via the Component Store. The dialog offers a direct link to
// ComponentStoreActivity where they can download Wine, DXVK, VKD3D, etc.
//
// Injection site: PcEnginePluginHostActivity.onCreate(Bundle) head — runs
// before any Wine process initialization. Aborts onCreate (via finish() +
// early return-void) if Wine is not installed, preventing the "Failed to
// load PC engine plugin" error and giving users a clear next step.
//
// The hook reuses the existing ComponentStoreMenuHandler.validateWineOrPrompt
// method (returns true if Wine installed, false otherwise). When false, the
// dialog is shown and the patch short-circuits onCreate.
//
// GameHub 6.1.0 migration: WineActivity was replaced by a plugin system.
// The old com.xiaoji.egggame.features.winemu.WineActivity is now an
// activity-alias that redirects to LegacyPcEngineActivityTrampoline (which
// just relaunches the main activity). The actual PC game launch happens in
// com.xiaoji.egggame.plugin.pcengine.host.PcEnginePluginHostActivity.
// =========================================================================

private const val PC_ENGINE_ACTIVITY = "Lcom/xiaoji/egggame/plugin/pcengine/host/PcEnginePluginHostActivity;"
private const val HANDLER_CLASS = "Lapp/revanced/extension/gamehub/componentstore/ComponentStoreMenuHandler;"

@Suppress("unused")
val wineValidationPatch = bytecodePatch(
    name = "Wine validation on launch",
    description = "Validates that Wine/Proton is installed before launching " +
        "PC games. Shows a Component Store prompt if Wine is missing, " +
        "preventing the 'Failed to load PC engine plugin' error.",
) {
    compatibleWith(GAMEHUB_PACKAGE(GAMEHUB_VERSION))

    apply {
        // PcEnginePluginHostActivity.onCreate (GameHub 6.1.0+ plugin system)
        val onCreateMethod = firstMethod {
            definingClass == PC_ENGINE_ACTIVITY &&
                name == "onCreate" &&
                parameterTypes == listOf("Landroid/os/Bundle;") &&
                returnType == "V"
        }

        // Prepend validation at method head. PcEnginePluginHostActivity.onCreate
        // has .locals 7 with v5-v6 being a wide register pair (64-bit long).
        // We use p0 directly for Activity reference and v0 for boolean result
        // (v0 gets immediately overwritten by sget-object after our code).
        // If validateWineOrPrompt returns false, abort via finish() + return-void.
        onCreateMethod.addInstructions(
            0,
            """
                invoke-static {p0}, $HANDLER_CLASS->validateWineOrPrompt(Landroid/app/Activity;)Z
                move-result v0
                if-eqz v0, :wine_installed
                invoke-virtual {p0}, Landroid/app/Activity;->finish()V
                return-void
                :wine_installed
                nop
            """.trimIndent(),
        )
    }
}
