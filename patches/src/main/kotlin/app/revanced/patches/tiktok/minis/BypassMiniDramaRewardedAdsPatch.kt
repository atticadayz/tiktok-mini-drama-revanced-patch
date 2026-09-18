package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction

private const val REWARD_REQUEST_START =
    "requestRewardAds, start, adInstanceUniqueId:"

private const val REWARD_PRELOADED_SHOW =
    "requestRewardAds, calling rewardADManager.show with preloaded cacheKey:"

private const val INTERSTITIAL_PRELOADED_SHOW =
    "requestInterstitialAds, calling rewardADManager.show with preloaded cacheKey:"

private const val REWARD_EXTRA_PARAMS =
    "requestRewardAds, extraParamsJson:"

private const val AD_SHOW =
    "LX/13Zc;->show(ZLjava/lang/String;)V"

private const val AD_START =
    "LX/13Zc;->start(ZLjava/util/HashMap;Ljava/util/HashMap;Ljava/util/HashMap;Ljava/util/List;)V"

private fun exitInstruction(instruction: Any): String {
    return when (instruction) {
        is FiveRegisterInstruction -> {
            val manager = instruction.registerC
            val completed = instruction.registerD
            "invoke-interface {v$manager, v$completed}, LX/13Zc;->exit(Z)V"
        }

        is RegisterRangeInstruction -> {
            val manager = instruction.startRegister
            val completed = manager + 1
            "invoke-interface/range {v$manager .. v$completed}, LX/13Zc;->exit(Z)V"
        }

        else -> throw PatchException("Unexpected TikTok rewarded-ad instruction format")
    }
}

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * TikTok contains two Mini rewarded-ad request implementations in this build.
 * The previous test only intercepted one of them.
 *
 * This version leaves show()/start() intact and immediately invokes exit(true)
 * on the same ad-manager instance. TikTok therefore performs its own normal
 * didExit callback, including rewardedVideoAdClose/isEnded state, instead of
 * leaving the Mini Drama player waiting for an ad lifecycle that never finishes.
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Immediately completes and closes TikTok Minis rewarded-video ads using TikTok's own ad lifecycle.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        // Newer/static Mini rewarded-ad request path.
        val rewardMethod = firstMethod(REWARD_REQUEST_START, REWARD_PRELOADED_SHOW)
        val rewardImplementation = rewardMethod.implementation
            ?: throw PatchException("TikTok reward-ad request method has no implementation")

        val rewardCalls = rewardImplementation.instructions.withIndex()
            .filter { (_, instruction) ->
                when ((instruction as? ReferenceInstruction)?.reference?.toString()) {
                    AD_SHOW, AD_START -> true
                    else -> false
                }
            }

        if (rewardCalls.isEmpty()) {
            throw PatchException("Could not find TikTok rewarded-ad show/start calls")
        }

        rewardCalls.sortedByDescending { it.index }.forEach { match ->
            rewardMethod.addInstructions(
                match.index + 1,
                exitInstruction(match.value),
            )
        }

        // Older/combined Mini ad request path. This method contains one
        // interstitial show followed by the rewarded start/show path, so skip
        // the first show and auto-close the later rewarded calls.
        val combinedMethod = firstMethod(
            REWARD_REQUEST_START,
            INTERSTITIAL_PRELOADED_SHOW,
            REWARD_EXTRA_PARAMS,
        )
        val combinedImplementation = combinedMethod.implementation
            ?: throw PatchException("TikTok combined Mini ad request method has no implementation")

        val combinedShowCalls = combinedImplementation.instructions.withIndex()
            .filter { (_, instruction) ->
                (instruction as? ReferenceInstruction)?.reference?.toString() == AD_SHOW
            }

        val combinedStartCalls = combinedImplementation.instructions.withIndex()
            .filter { (_, instruction) ->
                (instruction as? ReferenceInstruction)?.reference?.toString() == AD_START
            }

        val combinedRewardCalls =
            combinedShowCalls.drop(1) + combinedStartCalls

        if (combinedRewardCalls.isEmpty()) {
            throw PatchException("Could not find TikTok combined rewarded-ad show/start calls")
        }

        combinedRewardCalls.sortedByDescending { it.index }.forEach { match ->
            combinedMethod.addInstructions(
                match.index + 1,
                exitInstruction(match.value),
            )
        }
    }
}
