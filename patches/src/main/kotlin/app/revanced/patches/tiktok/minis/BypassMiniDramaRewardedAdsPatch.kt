package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val STANDARD_COMPLETION_CHECK =
    "LX/1RIO;->LIZ()Z"

private const val GMT_COMPLETION_CHECK =
    "LX/1RJU;->LIZ()Z"

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * v0.2.18 successfully reached the real X-button path, but the X path detected
 * that the reward timer was unfinished and opened TikTok's "You're so close"
 * retention popup.
 *
 * This version keeps the real X-button path, but for Mini rewarded ads only it
 * makes the X-button completion check read as already complete. That reproduces
 * the path TikTok itself takes when the timer has genuinely finished:
 *
 *   ad opens normally
 *   -> Mini-only auto X
 *   -> timer-complete check returns true
 *   -> no retention popup
 *   -> TikTok performs its normal completed-ad exit/close lifecycle
 *
 * Normal TikTok feed ads are not targeted.
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Auto-closes Mini rewarded ads through TikTok's own completed X-button path without showing the unfinished-timer popup.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        val standardShow = firstMethod {
            definingClass == "Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;" &&
                name == "b" &&
                returnType == "V" &&
                parameterTypes.isEmpty()
        }

        val standardClose = firstMethod {
            definingClass == "Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;" &&
                name == "HS" &&
                returnType == "V" &&
                parameterTypes.isEmpty()
        }

        val gmtShow = firstMethod {
            definingClass == "Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;" &&
                name == "b" &&
                returnType == "V" &&
                parameterTypes.isEmpty()
        }

        val gmtClose = firstMethod {
            definingClass == "Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;" &&
                name == "WS" &&
                returnType == "V" &&
                parameterTypes.isEmpty()
        }

        fun returnIndex(method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod): Int =
            method.implementation!!.instructions
                .withIndex()
                .lastOrNull { (_, instruction) -> instruction.opcode == Opcode.RETURN_VOID }
                ?.index
                ?: throw PatchException("Could not find rewarded-ad show routine return")

        fun completionMoveResult(
            method: app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod,
            reference: String,
        ): Pair<Int, Int> {
            val instructions = method.implementation!!.instructions
            val invokeIndex = instructions.withIndex()
                .firstOrNull { (_, instruction) ->
                    (instruction as? ReferenceInstruction)
                        ?.reference
                        ?.toString() == reference
                }
                ?.index
                ?: throw PatchException("Could not find Mini rewarded-ad completion check: $reference")

            val moveIndex = invokeIndex + 1
            val move = instructions[moveIndex] as? OneRegisterInstruction
                ?: throw PatchException("Unexpected completion-check result instruction")

            return moveIndex to move.registerA
        }

        // First: only for a Mini rewarded-ad delegate, make the real X-button
        // path believe the required watch duration has already completed.
        val (standardResultIndex, standardResultRegister) =
            completionMoveResult(standardClose, STANDARD_COMPLETION_CHECK)

        standardClose.addInstructions(
            standardResultIndex + 1,
            """
                iget-object v10, p0, Lcom/ss/android/ugc/aweme/ui/RewardAdContainer;->LLJJL:LX/1RI9;
                instance-of v11, v10, LX/1RJW;
                if-eqz v11, :mini_completion_done
                check-cast v10, LX/1RJW;
                iget-object v10, v10, LX/1RJW;->delegate:LX/1RJo;
                instance-of v10, v10, LX/13Sm;
                if-eqz v10, :mini_completion_done
                const/4 v$standardResultRegister, 0x1
                :mini_completion_done
            """.trimIndent(),
        )

        val (gmtResultIndex, gmtResultRegister) =
            completionMoveResult(gmtClose, GMT_COMPLETION_CHECK)

        gmtClose.addInstructions(
            gmtResultIndex + 1,
            """
                iget-object v13, p0, Lcom/ss/android/ugc/aweme/rich/reward/ui/GmtRewardAdContainer;->LLJZIJLIL:LX/1RIh;
                instance-of v14, v13, LX/1RJV;
                if-eqz v14, :mini_completion_done
                check-cast v13, LX/1RJV;
                iget-object v13, v13, LX/1RJV;->LLJJIII:LX/1RJo;
                instance-of v13, v13, LX/13Sm;
                if-eqz v13, :mini_completion_done
                const/4 v$gmtResultRegister, 0x1
                :mini_completion_done
            """.trimIndent(),
        )

        // Second: after the ad has completely finished its normal one-time
        // "shown" routine, automatically invoke the same method as the X button.
        // The delegate checks keep this scoped to Mini rewarded ads.
        standardShow.addInstructions(
            returnIndex(standardShow),
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

        gmtShow.addInstructions(
            returnIndex(gmtShow),
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
