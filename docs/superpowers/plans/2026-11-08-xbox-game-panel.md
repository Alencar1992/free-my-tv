# Xbox Game Panel Implementation Plan

> **For agentic workers:** Execute common data models and home integration inline; independent source/vault/art and native Xbox page workers may use superpowers:dispatching-parallel-agents after the contracts below are frozen. Finish with one independent review.

**Goal:** Optional private OpenXBL connection, game picker/artwork and emoji statistics beside GTA countdown.
**Architecture:** Pure bounded JSON models, private encrypted credential vault, one read-only source with immutable snapshots and a separate bounded artwork loader. Native Activity for setup/search/details; small Canvas home panel with existing virtual navigation.
**Tech Stack:** Java8/Android31+, org.json, AES/GCM AndroidKeystore, HTTPS, current JDK17/Android34 Bash build.
**Spec:** docs/superpowers/specs/2026-11-08-xbox-game-panel-design.md

## Global Constraints
-1.7.0-gta.5/code14, io.freemytv, certificate f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308.
- No personal keys/account/game test values in public source/assets. No new exported credential/import endpoint. Only read APIs; fixed authenticated host, separate uncredentialed artwork host allowlist.
- Max1000 games,2000 achievements,10 achievement pages,4MiB JSON/page,100 APIcalls/hour;15min cache cadence and30sec explicit same-game refresh cooldown;8 game rows/page. HTTP429 backoff, no fake campaign progress or zero for absent playtime.
- Preserve preferences, countdown/calendar/gallery, feeds, MIT/credits and HomeMode/Adb/ModeJob/Watchdog/HomeReceiver/Screensaver.

## Review Focus
- Key secrecy across errors, logs, redirects, cache and UI; encrypted storage failure must not save plaintext.
- New-account/disconnect/selection during in-flight requests must reject stale data; private cache cannot appear under another account.
- Missing/partial statistics and pagination cannot become false100%/zero-time claims.
- Catalog/artwork size/queue bounds and fast search/page changes must not hang TV or exhaust memory.
- Header fits between countdown and corner buttons; DPAD/Back/accessibility keep apps/settings reachable, other skins unaffected.

## Frozen shared contracts
All classes package io.freemytv.
XboxData (root, pure):
- Game final String id,name,cover; final boolean xbox360; final int unlocked,total,earnedScore,totalScore (-1 unknown); constructor(String id,String name,String cover,boolean xbox360,int unlocked,int total,int earnedScore,int totalScore).
- Profile final String xuid,gamertag; parseProfile(String).
- Catalog final List<Game> games; String continuation; boolean truncated; parseCatalog(String).
- Achievement final String id; boolean unlocked; int score. AchievementPage final List<Achievement> items; String continuation; parseAchievements(String).
- Summary final Game game; int unlocked,total,earnedScore,totalScore; Map<String,String> stats; boolean partial; constructor(Game,int,int,int,int,Map<String,String>,boolean). time():String (e.g.14h53min or —), progress():String (e.g.27,9% or —). Pure helpers summary(Game,List<AchievementPage>,Map<String,String>,boolean complete), parseStats(String):Map<String,String>, metricLabel(String):String, search(List<Game>,String):List<Game>, coverUrl(String):String (canonical approved HTTPS or empty). All parse APIs unwrap optional object content. Inputs validated/bounded, immutable outputs.
XboxSource (source worker):
- static get(Context); snapshot():Snapshot; addListener(Runnable),removeListener(Runnable); connect(String key),disconnect(),select(String gameId),refresh(boolean force). Async operations, notifications main thread.
- Snapshot immutable final boolean configured,loading,stale; String message,profileName,selectedId; List<XboxData.Game> games; XboxData.Summary summary; long updatedAt. selected():Game-or-null. Default disconnected, no request until connect. Missing selected detail may use catalog summary with time unavailable. Snapshot summary always corresponds selectedId or null.
XboxArt (same source worker): static get(Context); addListener(Runnable),removeListener(Runnable); prefetch(List<XboxData.Game> latestVisible); get(XboxData.Game):Bitmap-or-null; clear(). No API headers; worker replace-window, memory12/disk24, max2MiB/artwork, downsample to~160x220, stale callback safety. Metadata-only public artwork; cache files private.
XboxActivity (native UI worker): public Activity, nonexported root manifest. Uses APIs above. No other shared edits. Root adds Personalization menu entry and home panel.

### Task1 — root pure data models
Files new XboxData.java, tests/XboxDataTest.java; runner registration.
- [x] RED tests validate known response shapes, missing stats vs zero, minutes formatting, full/partial achievement counting, duplicate pages, foreign image URL rejection, case/accent search and bounds.
- [x] Implement contracts; GREEN full model suite. Never use user's actual profile/key/test values as fixture defaults.

### Task2 — source/vault/art worker
Files new XboxSource.java, XboxVault.java, XboxArt.java; optional own helper/test.
- [x] Implement encrypted key save/load/clear, atomic private metadata cache and generation rejection.
- [x] Connect validates account before saving; history/select/detail read APIs, bounded pages/TTLs/API budget/429. Parse with root models; never print secret/body.
- [x] Separate bounded art window/cache and HTTPS host validation; notify on main only. No global builds/shared file changes or commits.

### Task3 — native UI worker
Files new XboxActivity.java and optional owned UI helper only.
- [x] Masked key connection, enabled/disconnect controls; clear field after submit; no intent credentials or plaintext persistence. Native DPAD skin styling.
- [x] Local searchable/paged history rows with art+name; commit game selection; detail emoji stats and truthful unknown/partial/cached states; explicit refresh.
- [x] Resume/pause listeners/art windows properly; no global build/shared edits or commits.

### Task4 — root home integration
Files XboxHeaderView.java, HomeView.java, PersonalizationActivity.java, AndroidManifest.xml.
- [x] Add compact650x118 art/emoji summary panel beside countdown; separate invalidation/listeners and CTA when disconnected.
- [x] Add Z_XBOX focus Up from top/Down return, OK page, TalkBack virtual item. Preserve all prior keyboard paths and skins.
- [x] Nonexported XboxActivity :home and Personalizar home Xbox entry; bumpversion14.

### Task5 — verification/publication
- [x] Full suites/build/same cert/alignment/privacy/protected-file checks; independent review and material fixes.
- [x] Docs explain key entry, history limitations, unavailable metrics and lack of futureGTA guarantee; no credentials in source.
- [ ] Publish source and signed same-key APK/package/source/tag/release; verify actual public downloads; preserve earlier versions.
- [ ] Handoff install-r, private connection and FarCry test on TV, no direct device modification.
