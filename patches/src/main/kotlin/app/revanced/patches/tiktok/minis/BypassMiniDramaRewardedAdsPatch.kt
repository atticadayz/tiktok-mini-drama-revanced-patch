package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val REWARD_REQUEST_START =
    "requestRewardAds, start, adInstanceUniqueId:"

private const val REWARD_PRELOADED_SHOW =
    "requestRewardAds, calling rewardADManager.show with preloaded cacheKey:"

private const val REWARD_SHOW =
    "LX/13Zc;->show(ZLjava/lang/String;)V"

private const val REWARD_START =
    "LX/13Zc;->start(ZLjava/util/HashMap;Ljava/util/HashMap;Ljava/util/HashMap;Ljava/util/List;)V"

private const val COMPLETION_SUCCESS =
    "Lcom/bytedance/sdk/xbridge/registry/core/model/idl/CompletionBlock;->onSuccess(Lcom/bytedance/sdk/xbridge/registry/core/model/idl/XBaseResultModel;Ljava/lang/String;)V"

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * Mini Drama commonly uses a rewarded-video ad rather than an interstitial.
 * This patch prevents the reward ad manager from starting/showing the video,
 * lets TikTok complete the JS bridge request normally, then emits the same
 * rewardedVideoAdClose event TikTok emits after a completed rewarded video
 * with isEnded=true.
 *
 * In this exact TikTok build requestRewardAds has 27 registers / 4 incoming
 * parameters, so p0 == v23 (adInstanceUniqueId) and p3 == v26 (Minis context).
 */
@Suppress("unused")
val bypassMiniDramaRewardedAdsPatch = bytecodePatch(
    name = "Bypass Mini Drama rewarded ads",
    description = "Skips TikTok Minis rewarded-video ads and reports them as completed.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        val method = firstMethod(REWARD_REQUEST_START, REWARD_PRELOADED_SHOW)
        val implementation = method.implementation
            ?: throw PatchException("TikTok reward-ad request method has no implementation")

        val adCalls = implementation.instructions.withIndex()
            .filter { (_, instruction) ->
                val reference = (instruction as? ReferenceInstruction)
                    ?.reference
                    ?.toString()
                reference == REWARD_SHOW || reference == REWARD_START
            }
            .map { it.index }

        if (adCalls.isEmpty()) {
            throw PatchException("Could not find TikTok Minis rewarded-ad show/start calls")
        }

        // Keep TikTok's normal success/result-model path, but prevent the actual
        // ad manager from launching a video.
        adCalls.forEach { index ->
            method.replaceInstruction(index, "nop")
        }

        val successCallbacks = implementation.instructions.withIndex()
            .filter { (_, instruction) ->
                (instruction as? ReferenceInstruction)
                    ?.reference
                    ?.toString() == COMPLETION_SUCCESS
            }
            .map { it.index }

        if (successCallbacks.isEmpty()) {
            throw PatchException("Could not find TikTok Minis rewarded-ad success callbacks")
        }

        // Insert from the end so earlier instruction indices remain stable.
        successCallbacks.sortedDescending().forEach { index ->
            method.addInstructions(
                index + 1,
                """
                    new-instance v0, LX/02y2;
                    invoke-direct {v0}, LX/02y2;-><init>()V

                    new-instance v1, Lorg/json/JSONObject;
                    invoke-direct {v1}, Lorg/json/JSONObject;-><init>()V
                    iput-object v1, v0, LX/02y2;->element:Ljava/lang/Object;

                    const-string v2, "isEnded"
                    const/4 v3, 0x1
                    invoke-virtual {v1, v2, v3}, Lorg/json/JSONObject;->put(Ljava/lang/String;Z)Lorg/json/JSONObject;

                    move-object v6, v23
                    const-string v2, "adId"
                    invoke-virtual {v1, v2, v6}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

                    const-string v2, "minis.rewardedVideoAdClose"
                    new-instance v4, Lkotlin/jvm/internal/AwS532S0100000_29_I1;
                    const/16 v3, 0x507
                    invoke-direct {v4, v0, v3}, Lkotlin/jvm/internal/AwS532S0100000_29_I1;-><init>(LX/02y2;I)V

                    move-object v5, v26
                    invoke-static {v5, v2, v1, v4}, LX/13Sh;->LJFF(LX/13Se;Ljava/lang/String;Lorg/json/JSONObject;Lkotlin/jvm/functions/Function0;)V
                """.trimIndent(),
            )
        }
    }
}
