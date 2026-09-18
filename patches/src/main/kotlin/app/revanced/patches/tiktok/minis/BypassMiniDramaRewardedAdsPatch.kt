package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * v0.2.16 closed from the Mini delegate's onRewardAdShow callback. That callback
 * is invoked from inside RewardAdContainer.b(), before the container has fully
 * finished its one-time "shown" routine. Re-entering the exit lifecycle there
 * can kick the user out of the Mini player or hang the UI.
 *
 * This patch instead waits until the rewarded-ad container's show routine is
 * completely finished, then invokes the same close handler used by the actual
 * X button:
 *
 *   RewardAdContainer.HS()
 *   GmtRewardAdContainer.WS()
 *
 * It is additionally scoped to the Mini rewarded delegate (LX/13Sm;) so normal
 * TikTok feed ads and unrelated rewarded-ad surfaces are not auto-closed.
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Automatically uses TikTok's own X-button close path after a Mini rewarded ad finishes opening.",
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

        fun returnIndex(method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod): Int =
            method.implementation!!.instructions
                .withIndex()
                .lastOrNull { (_, instruction) -> instruction.opcode == Opcode.RETURN_VOID }
                ?.index
                ?: throw PatchException("Could not find rewarded-ad show routine return")

        // Standard RewardAdContainer uses LX/1RJW as the concrete manager.
        // Its 'delegate' field is the Mini bridge delegate. Only auto-close
        // when that delegate is LX/13Sm (Mini rewarded ad).
        standardContainer.addInstructions(
            returnIndex(standardContainer),
            """
                iget-object v0, p0, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->LLJJL:LX/1RI9;
                instance-of v1, v0, LX/1RJW;
                if-eqz v1, :mini_close_done
                check-cast v0, LX/1RJW;
                iget-object v0, v0, LX/1RJW;->delegate:LX/1RJo;
                instance-of v0, v0, LX/13Sm;
                if-eqz v0, :mini_close_done
                invoke-virtual {p0}, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->HS()V
                :mini_close_done
            """.trimIndent(),
        )

        // GMT rewarded ads use LX/1RJV. Its LLJJIII field is the same bridge
        // delegate slot.
        gmtContainer.addInstructions(
            returnIndex(gmtContainer),
            """
                iget-object v0, p0, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->LLJZIJLIL:LX/1RIh;
                instance-of v1, v0, LX/1RJV;
                if-eqz v1, :mini_close_done
                check-cast v0, LX/1RJV;
                iget-object v0, v0, LX/1RJV;->LLJJIII:LX/1RJo;
                instance-of v0, v0, LX/13Sm;
                if-eqz v0, :mini_close_done
                invoke-virtual {p0}, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->WS()V
                :mini_close_done
            """.trimIndent(),
        )
    }
}
