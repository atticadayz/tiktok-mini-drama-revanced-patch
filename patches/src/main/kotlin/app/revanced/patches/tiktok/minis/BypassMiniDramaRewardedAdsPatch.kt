package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * Previous builds tried to close the ad immediately after request/start/show().
 * That can fire too early because TikTok opens the rewarded-ad UI asynchronously.
 *
 * This version hooks the actual Mini rewarded-ad "onRewardAdShow" callback.
 * By the time this callback runs, TikTok considers the ad visible. We then call
 * the ad manager's own exit(true), allowing TikTok's normal didExit/reward-close
 * lifecycle to fire after the UI is really present.
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Closes TikTok Minis rewarded ads as soon as TikTok reports the ad is actually shown.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        val delegateA = firstMethod {
            definingClass == "LX/13Sm;" &&
                name == "onRewardAdShow" &&
                parameterTypes.size == 1
        }

        val delegateB = firstMethod {
            definingClass == "LX/13Sn;" &&
                name == "onRewardAdShow" &&
                parameterTypes.size == 1
        }

        listOf(delegateA, delegateB).forEach { method ->
            // p1 is the LX/13Zc rewarded-ad manager. This callback itself calls
            // getAdID()/getVideoDuration() on p1 in TikTok 46.9.3, confirming
            // that it is the live ad-manager instance.
            method.addInstructions(
                0,
                """
                    const/4 v0, 0x1
                    invoke-interface {p1, v0}, LX/13Zc;->exit(Z)V
                """.trimIndent(),
            )
        }
    }
}
