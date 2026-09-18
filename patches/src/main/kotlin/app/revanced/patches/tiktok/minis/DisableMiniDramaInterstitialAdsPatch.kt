package app.revanced.patches.tiktok.minis

import app.revanced.patcher.firstMethod
import app.revanced.patcher.patch.bytecodePatch

private const val INTERSTITIAL_PRELOADED_SHOW =
    "requestInterstitialAds, calling rewardADManager.show with preloaded cacheKey:"

private const val REWARD_EXTRA_PARAMS =
    "requestRewardAds, extraParamsJson:"

/**
 * TikTok 46.9.3 (com.zhiliaoapp.musically)
 *
 * Stability build:
 *
 * Earlier versions immediately called the low-level ad-manager exit() after the
 * Mini interstitial show() call. That is the same class of close operation that
 * has intermittently returned the user to the For You feed.
 *
 * For now this patch intentionally does not alter the interstitial lifecycle.
 * It only verifies that the expected Mini interstitial request method is still
 * present. The rewarded/timed Mini ads are handled by the separate
 * "Bypass Mini Drama rewarded ads" patch.
 *
 * This isolates the two ad paths so we can verify whether the intermittent
 * return-to-For-You behavior comes from the interstitial auto-exit.
 */
@Suppress("unused")
val disableMiniDramaInterstitialAdsPatch = bytecodePatch(
    name = "Disable Mini Drama interstitial ads",
    description = "Stability mode: leaves Mini interstitials on TikTok's normal close path while timed/rewarded Mini ads are handled separately.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        // Compatibility/fingerprint check only. Deliberately make no bytecode
        // changes to this path in the stability build.
        firstMethod(INTERSTITIAL_PRELOADED_SHOW, REWARD_EXTRA_PARAMS)
    }
}
