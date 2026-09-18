# TikTok Mini Drama interstitial ReVanced patch

This repository is prepared for **ReVanced Manager 2.x**.

## Target

- TikTok: **46.9.3**
- Package: `com.zhiliaoapp.musically`
- Version code: `2024609030`
- Target APK SHA-256: `56dc6309c1d485aaa0f1b04d097d57364371c7241567156a70ce1bf373ee34ed`

The APK itself is intentionally **not** included in this repository.

## What the patch changes

TikTok's Mini Drama player has an episode-switch interstitial configuration object:

`VePlayerEpisodeSwitchInterstitialConfig`

The relevant decision path reads its `enabled:Z` field. The patch anchors on TikTok's diagnostic string:

`[minis.interstitial.frequency][decision] currentContext=`

and replaces only the exact `enabled` field read with `false`.

That makes TikTok follow its own "interstitial disabled" path. It does not blanket-disable the main TikTok feed and it does not block the Mini Drama player itself.

## ReVanced Manager source

After the GitHub Action builds successfully, add this URL to ReVanced Manager:

`https://raw.githubusercontent.com/atticadayz/tiktok-mini-drama-revanced-patch/main/patches.json`

Then select TikTok 46.9.3, choose **Disable Mini Drama interstitial ads**, patch, and install.
