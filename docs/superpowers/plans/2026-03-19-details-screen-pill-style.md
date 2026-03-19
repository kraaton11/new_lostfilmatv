# Details Screen Pill Style Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the pill-style quality-section visuals from the provided file into the current details screen without breaking existing behavior or screen entry points.

**Architecture:** Keep the current details screen contracts intact and restrict the change to the torrent-quality presentation inside `DetailsScreen.kt`. Reuse the existing torrent row models and focus plumbing, but restyle the section and its row controls to reflect the new visual direction.

**Tech Stack:** Kotlin, Jetpack Compose, Android TV focus APIs

---

## Chunk 1: Prepare The UI Change

### Task 1: Map the files and preserved behavior

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`
- Verify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsTorrentModels.kt`

- [ ] **Step 1: Confirm the preserved public contracts**
- [ ] **Step 2: Identify which visual pieces come from `DetailsScreen(1).kt`**
- [ ] **Step 3: Keep both actions available: raw link and TorrServe**

## Chunk 2: Implement The Restyle

### Task 2: Restyle the torrent quality section

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/details/DetailsScreen.kt`

- [ ] **Step 1: Update local palette/constants needed for the pill style**
- [ ] **Step 2: Replace the current section heading with the compact style**
- [ ] **Step 3: Update the torrent row layout so it visually borrows the pill language**
- [ ] **Step 4: Preserve focus traversal and active/busy message behavior**

## Chunk 3: Verify

### Task 3: Re-run focused verification

**Files:**
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/details/DetailsTorrentModelsTest.kt`

- [ ] **Step 1: Run `.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.details.DetailsTorrentModelsTest"`**
- [ ] **Step 2: Run `.\gradlew.bat :app:assembleDebug`**
- [ ] **Step 3: Confirm the APK output path still exists**
