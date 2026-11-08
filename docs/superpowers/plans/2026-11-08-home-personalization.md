# Home Personalization Implementation Plan

> **For agentic workers:** Execute root integration inline; use superpowers:dispatching-parallel-agents for independent feed and native-editor files after common interfaces exist. Use test-driven development for behavior models and a final independent code review.

**Goal:** Same-key in-place update with private home editing, public YouTube/trailer discovery,15sec GTA slides and neon focus.
**Architecture:** Private preferences plus Android-free ordering/URL/parser models. Native editor owns remote-friendly controls; a bounded background feed source supplies the existing recommendation renderer. Existing launcher/ADB/recovery mechanisms remain unchanged.
**Tech Stack:** Java8 syntax, Android31+, Canvas/native widgets, standard HTTPS/XML/JSON, existing Bash build, JDK17/SDK34.
**Spec:** docs/superpowers/specs/2026-11-08-home-personalization-design.md (approved).

## Global Constraints
- Version1.7.0-gta.2/code11, io.freemytv, existing keystore/certificate f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308.
- No uninstall/data clearing, no direct TV changes. Preserve MIT/credits/assets and HomeMode/Adb/ModeJob/Watchdog/HomeReceiver/Screensaver.
- Personal channel URLs/config outside Git/APK. HTTPS official YouTube hosts only; no account credentials or global HTTP exception.
-12 favorite sources,8 trailer sources,5 items/source,100 merged videos;15-minute polling, bounded cache/network and stale-generation rejection.
- Cloud proxy denies YouTube CONNECT403; do not bypass or claim live-feed validation.

## Review Focus
- Empty dock and removed app packages must keep settings/All Apps accessible.
- Invalid imports, hostile URLs, oversized XML/DTD, and off-host redirects must fail without new privileges or network exposure.
- Settings changes during an in-flight request must not publish removed-channel results.
- Partial network failure must retain safe cached data and distinguish stale/empty/loading states.
- Legacy feed default and existing skin/private settings/signature must survive updating.

### Task1 — common models and private prefs (root)
Files: new AppOrder.java, ChannelRef.java, Personalization.java; HomeApps.java; new AppOrderTest.java and ChannelRefTest.java.
- [ ] Test ordering/dedup/empty/default/missing/limit/move and URL normalization/import validation first; run RED.
- [ ] Implement pure helpers and private preference facade; run GREEN.
- [ ] Add HomeApps.editableApps(Context) metadata-only and selectedPackages(Context); adapt shelf/others to selection while preserving logo behavior/defaults.
- [ ] Verify full existing+new model suites; commit scoped files.

### Shared interfaces (frozen for workers)
- AppOrder.visible(List<String> configuredOrNull,List<String> defaults,Set<String> available):List<String>; add/remove/move returning new lists, MAX=7.
- ChannelRef.normalize(String):canonical HTTPS YouTube/channel-or-handle URL, key(String), channelId(String):String-or-null, validChannelId(String):boolean; decodeImport(String base64):List<String> strict favorites JSON, max12.
- Personalization (Android facade): appOrder(Context):List<String>-or-null(default), saveApps(Context,List<String>), restoreApps(Context); favorites(Context):List<String>, saveFavorites(Context,List<String>); publicDiscovery(Context),setPublicDiscovery(Context,boolean); trailersEnabled(Context),setTrailersEnabled(Context,boolean); trailerSources(Context):List<Trailer>,saveTrailerSources(Context,List<Trailer>),restoreTrailerSources(Context).
- Personalization.Trailer immutable final fields pkg,label,channel,official; constructor(String pkg,String label,String channel,boolean official). Manual sources must be false. Defaults only verified NetflixBrasil/PrimeVideoBR/disneyplusbr/StreamMaxBR for corresponding installed dock apps; saved sources filter to installed launchable apps.
- HomeApps.editableApps(Context):List<HomeApps.Item> sorted metadata-only (kind,key,pkg,label); HomeApps.selectedPackages(Context):List<String> enabled current dock order.

### Task2 — public feed domain (worker, no other files)
Files: new PublicVideos.java, DiscoverySource.java and any small independent helper; tests/PublicVideosTest.java and tests/DiscoveryStateTest.java if useful.
PublicVideos.Source immutable ref,label,pkg,trailer,official; constructor(String ref,String label,String pkg,boolean trailer,boolean official).
PublicVideos.Video immutable id,channelId,channelTitle,title,thumbnail,url,publishedAt,favorite,trailer,serviceLabel; IDs canonical, urls constructed; parse/resolve/merge APIs documented in file.
DiscoverySource API: static get(Context); addListener(Runnable),removeListener(Runnable); refresh(List<PublicVideos.Source>,boolean force); videos():List<PublicVideos.Video>; revision():String; status():Status (immutable fields loading,stale,failed,completed,total,updatedAt,count). No prefs or Recs dependency.
- [ ] Write meaningful parser/URL/merge/cache-generation tests; see RED before implementing.
- [ ] Implement strict Atom/XML parsing, official host redirects, cache-first background refresh and bounded partial results with generation cancellation.
- [ ] Keep source config private; report live-network limitation. Do not touch global test runner or shared output directories.

### Task3 — editor domain (worker, no other files)
File: new PersonalizationActivity.java only, optional UI helper owned by worker.
API: static final EXTRA_IMPORT="favorite_channels_import"; receives base64 string through internal Activity intent forwarded by exported SettingsActivity; shows review/cancel and saves only after explicit OK. No direct mode/home changes.
- [ ] Implement themed large native controls with D-pad focus for ordered dock (max7), add/remove/move/default, favorites(max12), public-vs-legacy source selection, trailers enable/sources(max8), and refresh.
- [ ] Use stable prefs APIs and metadata-only installed app list; query apps off UI thread. Every preference field independently preserved; imported6 links never hardcoded.
- [ ] Refresh button calls Recs.get(this).refreshPublicNow() (root implements). Ordinary save uses Recs.get(this).settingsChanged() (root implements).
- [ ] Keep new Activity internal/nonexported; root handles manifest+Settings entry. No global build while another worker builds.

### Task4 — integration and GTA focus (root)
Files: Recs.java, HomeView.java, SettingsActivity.java, app/AndroidManifest.xml, tests/run-java-tests.sh, existing Skin/Dream if needed.
- [ ] Register and run worker tests. Adapt Recs to explicit public/legacy source mode with distinct favorites/trailer flags, canonical video dedup, source revisions, cache state and existing thumbnail limits.
- [ ] Add Personalizar home MAIN row and validated import forwarding; update row counts/layout/accessibility consistently.
- [ ] Add new tab names/filter when public mode active; refresh empty/loading/stale messages; keep legacy tabs/defaults unchanged when configured.
- [ ] Make empty dock safe for reset/back/movement/activation and All Apps. Add GTA-only lifted Spring focus plus cached gradient neon strokes for dock and All Apps, preserve other skins.
- [ ] Bump version; full Java/Python/build/signature/alignment and source-privacy/protected-file checks.

### Task5 — review, publication, handoff (root)
- [ ] Independent whole-change review, fix material findings with targeted tests and green suite.
- [ ] Document actual user-reported .1 TV test separately from pending new UI/feeds test; update same-signature instructions with install -r and no clearing/uninstall.
- [ ] Publish source to gta-vi and artifact update in downloads using existing GitHub credentials; release metadata links to downloads branch (uploads.github.com asset auth unavailable).
- [ ] Keep .1 assets; create versioned .2 APK/package/source/hash/public certificate; verify public download bytes and signature.
- [ ] Provide private six-channel import command inline for review on TV (no publication), plus guide for dock/trailers/focus checks and return to Original. No signing-key re-export needed.
