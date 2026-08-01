package app.revanced.patches.gamehub.componentstore

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.gamehub.GAMEHUB_PACKAGE
import app.revanced.patches.gamehub.GAMEHUB_VERSION

// =========================================================================
// Wine Validation Patch — intercepts WineActivity.onCreate to validate
// that a Wine/Proton container is installed before launching PC games.
//
// If Wine is missing, shows an AlertDialog prompting the user to install
// components via the Component Store. The dialog offers a direct link to
// ComponentStoreActivity where they can download Wine, DXVK, VKD3D, etc.
//
// Injection site: WineActivity.onCreate(Bundle) head — runs before any
// Wine process initialization. Aborts onCreate (via early return-void) if
// Wine is not installed, preventing the "Failed to load PC engine plugin"
// error and giving users a clear next step.
//
// The hook reuses the existing ComponentStoreMenuHandler.validateWineOrPrompt
// method (returns true if Wine installed, false otherwise). When false, the
// dialog is shown and the patch short-circuits onCreate.
// =========================================================================

private const val WINE_ACTIVITY = "Lcom/xiaoji/egggame/features/winemu/WineActivity;"
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
        // WineActivity.onCreate is stable (non-obfuscated) across 6.0.4→6.1.0
        val onCreateMethod = firstMethod {
            definingClass == WINE_ACTIVITY &&
                name == "onCreate" &&
                parameterTypes == listOf("Landroid/os/Bundle;") &&
                returnType == "V"
        }

        // Prepend validation at method head. WineActivity.onCreate has 19-20
        // locals depending on version, so p0 is a high register — we move it
        // to v0 first (proven pattern from vibrationPatch, perfOverlayPatch).
        // If validateWineOrPrompt returns false, abort via return-void.
        onCreateMethod.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                invoke-static {v0}, $HANDLER_CLASS->validateWineOrPrompt(Landroid/app/Activity;)Z
                move-result v1
                if-eqz v1, :wine_installed
                invoke-virtual {v0}, Landroid/app/Activity;->finish()V
                return-void
                :wine_installed
                nop
            """.trimIndent(),
        )
    }
}
