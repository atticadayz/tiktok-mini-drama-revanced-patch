package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * v0.2.16 closed the ad from the Mini delegate's onRewardAdShow callback.
 * That callback is invoked from inside RewardAdContainer.b(), before the
 * container's own "ad shown" routine has completely returned. Closing from
 * inside that callback can re-enter the ad lifecycle, which can kick the user
 * out of the Mini player or hang the app.
 *
 * This version waits until the rewarded-ad container has finished its complete
 * one-time show routine. Immediately before that routine returns, it calls the
 * exact same close handler used by the container's X button:
 *
 *   RewardAdContainer.HS()
 *   GmtRewardAdContainer.WS()
 *
 * This intentionally follows TikTok's own close-button path instead of calling
 * the lower-level ad-manager exit() API directly.
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Automatically follows TikTok's own rewarded-ad X-button close path after the ad finishes opening.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        val standardContainer = firstMethod {
            definingClass == "Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;" &&
                name == "b" &&
                returnType == "V" &&
                parameterTypes.isEmpty()
        }

        val gmtContainer = firstMethod {
            definingClass == "Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;" &&
                name == "b" &&
                returnType == "V" &&
                parameterTypes.isEmpty()
        }

        fun patchCloseAtEnd(
            method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod,
            closeInstruction: String,
        ) {
            val returnIndex = method.implementation!!.instructions
                .withIndex()
                .lastOrNull { (_, instruction) -> instruction.opcode == Opcode.RETURN_VOID }
                ?.index
                ?: throw PatchException("Could not find rewarded-ad show routine return")

            method.addInstructions(returnIndex, closeInstruction)
        }

        // HS() and WS() are the same methods invoked by the actual close/X
        // branches in each container's onClick(View) implementation.
        patchCloseAtEnd(
            standardContainer,
            "invoke-virtual {p0}, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->HS()V",
        )

        patchCloseAtEnd(
            gmtContainer,
            "invoke-virtual {p0}, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->WS()V",
        )
    }
}
