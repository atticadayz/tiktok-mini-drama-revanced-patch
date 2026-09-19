package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * Mini interstitials use a different delegate from Mini rewarded ads:
 *
 *   LX/13Sn -> minis.interstitialAdClose / minis.interstitialAdError
 *
 * The earlier interstitial implementation called the low-level ad-manager
 * exit() API and could occasionally unwind the entire Mini surface back to the
 * For You feed. This version deliberately does NOT close the interstitial.
 *
 * Instead it only completes TikTok's own required-watch countdown as soon as
 * the Mini interstitial has finished opening. For scroll-gated ads this should
 * remove the wait/scroll lock immediately, while leaving TikTok's normal swipe
 * navigation and close lifecycle intact.
 */
@Suppress("unused")
val disableMiniDramaInterstitialAdsPatch = bytecodePatch(
    name = "Unlock Mini Drama scroll ads",
    description = "Completes the timer on Mini interstitial/scroll ads so scrolling can continue immediately without force-closing the Mini player.",
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
                ?: throw PatchException("Could not find Mini interstitial show routine return")

        // Standard rewarded-ad container. Only act when the delegate is the
        // Mini interstitial delegate (LX/13Sn). Fast-forward the native
        // countdown, but do not call HS()/exit().
        standardContainer.addInstructions(
            returnIndex(standardContainer),
            """
                iget-object v0, p0, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->LLJJL:LX/1RI9;
                instance-of v1, v0, LX/1RJW;
                if-eqz v1, :mini_interstitial_done
                check-cast v0, LX/1RJW;
                iget-object v0, v0, LX/1RJW;->delegate:LX/1RJo;
                instance-of v0, v0, LX/13Sn;
                if-eqz v0, :mini_interstitial_done

                iget-object v2, p0, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->LLLIIIL:LX/1RIO;
                instance-of v0, v2, LX/0uRs;
                if-eqz v0, :mini_interstitial_done
                check-cast v2, LX/0uRs;
                iget-wide v0, v2, LX/0uRs;->LJIIIZ:J
                iput-wide v0, v2, LX/0uRs;->LIZLLL:J
                invoke-virtual {v2}, LX/0uRs;->LIZLLL()V

                :mini_interstitial_done
            """.trimIndent(),
        )

        // GMT container equivalent. Again, finish only the timer; leave
        // navigation/closing to TikTok so a swipe does not unwind Mini Drama.
        gmtContainer.addInstructions(
            returnIndex(gmtContainer),
            """
                iget-object v0, p0, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->LLJZIJLIL:LX/1RIh;
                instance-of v1, v0, LX/1RJV;
                if-eqz v1, :mini_interstitial_done
                check-cast v0, LX/1RJV;
                iget-object v0, v0, LX/1RJV;->LLJJIII:LX/1RJo;
                instance-of v0, v0, LX/13Sn;
                if-eqz v0, :mini_interstitial_done

                iget-object v2, p0, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->LLLLIILLL:LX/1RJU;
                instance-of v0, v2, LX/1RIk;
                if-eqz v0, :mini_interstitial_done
                check-cast v2, LX/1RIk;
                iget-wide v0, v2, LX/1RIk;->LLJJIII:J
                iput-wide v0, v2, LX/1RIk;->LLJJI:J
                invoke-static {}, Landroid/os/Message;->obtain()Landroid/os/Message;
                move-result-object v3
                const/16 v0, 0x3e9
                iput v0, v3, Landroid/os/Message;->what:I
                invoke-virtual {v2, v3}, LX/1RIk;->handleMsg(Landroid/os/Message;)V

                :mini_interstitial_done
            """.trimIndent(),
        )
    }
}
