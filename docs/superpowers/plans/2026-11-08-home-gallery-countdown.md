# Home Gallery and Countdown Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, with a final independent source review. The user supplied exact behavior and requested implementation; retain the existing inline execution workflow.

**Goal:** Rotate the11 bundled photos on GTA home every15 seconds and show a date-aware launch badge beside GTA VI.
**Architecture:** Android-free calendar and slideshow state models, worker-decoded artwork and a small dedicated countdown View. HomeView owns lifecycle/bitmaps; Wallpaper owns rendering the same overlay/crop per asset.
**Tech Stack:** Java8 syntax, java.time, Android31+ Canvas/Handler, existing Bash Android34 build.
**Spec:** docs/superpowers/specs/2026-11-08-home-gallery-countdown-design.md

## Global Constraints
- Countdown target2026-11-19T00:00 America/Sao_Paulo; message HOJE É O GRANDE DIA! on that calendar day, hidden from2026-11-20T00:00.
- DD/HH/MM/SS; exact wall clock recalculation, no server. Same licensed GTA-skin fonts/palette.
- Version1.7.0-gta.4/code13, io.freemytv, existing signing certificate f555ccc7fe855e1bcf8c7bc96eecc523838dc856aaa5a0b3388a6ef89aad7308.
- Preserve preferences,11 assets/credits/MIT and original recovery files. No direct TV install/uninstall/data clearing.

## Review Focus
- Midnight transitions must follow Brasília even when device/JVM default zone differs.
- Paused/destroyed homes reject late artwork callbacks and never schedule extra background work.
- Corrupt/missing images skip safely; all failures leave graphite and stop I/O; one survivor is not repeatedly decoded.
- Repeated resumes do not leak bitmap references/timers or reset focus/picks.
- Badge disappears without covering navigation/buttons and updates only its small own layer.

### Task1: Pure models and tests
Files: LaunchCountdown.java, HomeWallpaperCycle.java; tests/LaunchCountdownTest.java, tests/HomeWallpaperCycleTest.java; tests/run-java-tests.sh.
Interfaces: LaunchCountdown.at(long epochMillis):State with phase, days/hours/minutes/seconds and digits(); phases COUNTING/LAUNCH_DAY/HIDDEN. HomeWallpaperCycle.resume(long uptime),pause(),request(long):Request(asset,token),current(Request),complete(Request,boolean,long):boolean,delay(long):long; negative delay means no pending switch. Interval15_000.
- [x] Add failing boundary tests:1ms before target, both midnights, full launch day, default-zone independence, resume with clock jumped.
- [x] Add failing cycle tests:15s exact threshold, no duplicate in-flight load, pause/resume rejects old completion, skip failures/one survivor/all failed.
- [x] Observe RED, implement minimal models, register and run full Java suite.

### Task2: Native integration
Files: LaunchCountdownView.java, HomeView.java, Wallpaper.java.
- [x] Generalize Wallpaper.home(assets,path), preserving existing crop/overlay and legacy overload.
- [x] Connect cycle loading on worker with generation check before/after decode, delayed recycle and one timer per visible home; crossfade800ms via existing frame clock.
- [x] Add small noninteractive badge beside GTA title: tnum digits, palette border, labels/message; start/stop handler with home, hide on All Apps sheet and from20Nov.
- [x] Build and review transitions, bitmap release, layer/layout bounds and other-skin preservation.

### Task3: Review and delivery
Files: version manifest, README, guide, VALIDACAO; artifacts in separate downloads branch.
- [x] Bumpcode13/namegta.4; tests/build/signature/alignment/privacy and protected-file checks.
- [x] Independent source review; fix material findings and verify scoped changes.
- [ ] Publish source/tag/release and same-key APK/package/sources, retain old versions. Verify downloaded APK/package bytes/signature.
- [ ] Deliver download/update commands plus physical acceptance test. Record .3 feed success separately from untested new countdown/gallery.
