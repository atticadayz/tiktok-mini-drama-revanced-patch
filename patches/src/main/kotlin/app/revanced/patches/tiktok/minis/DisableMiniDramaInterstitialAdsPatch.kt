package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val INTERSTITIAL_PRELOADED_SHOW =
    "requestInterstitialAds, calling rewardADManager.show with preloaded cacheKey:"

private const val REWARD_EXTRA_PARAMS =
    "requestRewardAds, extraParamsJson:"

private const val AD_SHOW =
    "LX/13Zc;->show(ZLjava/lang/String;)V"

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * Do not disable the Mini Drama interstitial gate itself. Some Mini Drama flows
 * wait for the ad lifecycle to finish before advancing the episode.
 *
 * Instead, allow TikTok to call show(), then immediately call the ad manager's
 * own exit(true). That keeps TikTok's normal didExit/close callback path intact
 * while dismissing the ad immediately.
 */
@Suppress("unused")
val disableMiniDramaInterstitialAdsPatch = bytecodePatch(
    name = "Disable Mini Drama interstitial ads",
    description = "Immediately closes Mini Drama interstitial ads while preserving TikTok's normal close callback.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        val method = firstMethod(INTERSTITIAL_PRELOADED_SHOW, REWARD_EXTRA_PARAMS)
        val implementation = method.implementation
            ?: throw PatchException("TikTok combined Mini ad request method has no implementation")

        val showCalls = implementation.instructions.withIndex()
            .filter { (_, instruction) ->
                (instruction as? ReferenceInstruction)
                    ?.reference
                    ?.toString() == AD_SHOW
            }

        val interstitialShow = showCalls.firstOrNull()
            ?: throw PatchException("Could not find TikTok Mini interstitial show call")

        val invoke = interstitialShow.value as? FiveRegisterInstruction
            ?: throw PatchException("Unexpected TikTok Mini interstitial show instruction format")

        val managerRegister = invoke.registerC
        val completedRegister = invoke.registerD

        method.addInstructions(
            interstitialShow.index + 1,
            "invoke-interface {v$managerRegister, v$completedRegister}, LX/13Zc;->exit(Z)V",
        )
    }
}
