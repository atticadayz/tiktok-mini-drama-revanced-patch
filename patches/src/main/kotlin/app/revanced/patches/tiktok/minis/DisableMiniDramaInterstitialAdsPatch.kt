package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val INTERSTITIAL_DECISION_LOG =
    "[minis.interstitial.frequency][decision] currentContext="

private const val ENABLED_FIELD =
    "Lcom/ss/android/ugc/aweme/minis/ads/VePlayerEpisodeSwitchInterstitialConfig;->enabled:Z"

@Suppress("unused")
val disableMiniDramaInterstitialAdsPatch = bytecodePatch(
    name = "Disable Mini Drama interstitial ads",
    description = "Disables TikTok Minis ads shown between Mini Drama episodes.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        val method = firstMethod(INTERSTITIAL_DECISION_LOG)

        val match = method.implementation!!.instructions
            .withIndex()
            .firstOrNull { (_, instruction) ->
                instruction.opcode == Opcode.IGET_BOOLEAN &&
                    (instruction as? ReferenceInstruction)
                        ?.reference
                        ?.toString() == ENABLED_FIELD
            } ?: throw PatchException(
                "Could not find TikTok Minis episode-switch interstitial enabled check"
            )

        val destinationRegister =
            (match.value as TwoRegisterInstruction).registerA

        method.replaceInstruction(
            match.index,
            "const/4 v$destinationRegister, 0x0",
        )
    }
}
