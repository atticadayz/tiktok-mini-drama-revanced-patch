package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * v0.2.18 successfully reached TikTok's own X-button close path, but the
 * rewarded-ad countdown was still unfinished. TikTok therefore showed its
 * retention popup ("You're so close!" / "Keep watching") instead of closing.
 *
 * This version advances TikTok's own countdown controller to its completed
 * state first, allowing its normal completion callback to run, and only then
 * invokes the same X-button close handler.
 *
 * The behavior remains scoped to the Mini rewarded-ad delegate so normal feed
 * ads are not auto-closed.
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Completes the Mini rewarded-ad countdown, then automatically follows TikTok's own X-button close path.",
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

                # Fast-forward the real TikTok countdown controller to its
                # duration, then run its normal completion routine. That routine
                # sets the finished flag and invokes TikTok's completion callback.
                iget-object v2, p0, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->LLLIIIL:LX/1RIO;
                instance-of v0, v2, LX/0uRs;
                if-eqz v0, :mini_close_now
                check-cast v2, LX/0uRs;
                iget-wide v0, v2, LX/0uRs;->LJIIIZ:J
                iput-wide v0, v2, LX/0uRs;->LIZLLL:J
                invoke-virtual {v2}, LX/0uRs;->LIZLLL()V

                :mini_close_now
                invoke-virtual {p0}, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->HS()V
                :mini_close_done
            """.trimIndent(),
        )

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

                # GMT uses a different countdown controller. Put its elapsed
                # time at the configured duration, then send the same timer tick
                # message (1001) it normally uses. The handler performs the real
                # completion transition and notifies its observers.
                iget-object v2, p0, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->LLLLIILLL:LX/1RJU;
                instance-of v0, v2, LX/1RIk;
                if-eqz v0, :mini_close_now
                check-cast v2, LX/1RIk;
                iget-wide v0, v2, LX/1RIk;->LLJJIII:J
                iput-wide v0, v2, LX/1RIk;->LLJJI:J
                invoke-static {}, Landroid/os/Message;->obtain()Landroid/os/Message;
                move-result-object v3
                const/16 v0, 0x3e9
                iput v0, v3, Landroid/os/Message;->what:I
                invoke-virtual {v2, v3}, LX/1RIk;->handleMsg(Landroid/os/Message;)V

                :mini_close_now
                invoke-virtual {p0}, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->WS()V
                :mini_close_done
            """.trimIndent(),
        )
    }
}
