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
 * Stability rollback.
 *
 * v0.2.22 tried to fast-forward the Mini interstitial/scroll-ad countdown from
 * inside the rewarded-ad container's one-time show routine. Some Mini/SEA-drama
 * ad variants appear to perform additional asynchronous setup there. Forcing
 * the timer completion while that setup is still active can re-enter TikTok's
 * ad callbacks and cause an ANR/crash or unwind the Mini surface.
 *
 * This patch therefore makes no behavioral change to the interstitial path.
 * The working Mini rewarded/timed-ad bypass remains in the separate
 * "Bypass Mini Drama rewarded ads" patch.
 */
@Suppress("unused")
val disableMiniDramaInterstitialAdsPatch = bytecodePatch(
    name = "Mini Drama interstitial stability",
    description = "Stability mode: does not alter Mini interstitial/scroll ads while the separate rewarded-ad bypass remains active.",
) {
    compatibleWith("com.zhiliaoapp.musically"("46.9.3"))

    apply {
        // Fingerprint/compatibility check only.
        firstMethod(INTERSTITIAL_PRELOADED_SHOW, REWARD_EXTRA_PARAMS)
    }
}
