# Home Season And Episode Card Overlay Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `Сезон N, серия M` onto series poster cards on `Home` as a bottom overlay and remove the duplicate season/episode row from the lower info panel without changing rail layout or TV navigation behavior.

**Architecture:** Keep the existing `Home` screen composition and state flow intact. `PosterCard` becomes the owner of season/episode rendering for series items with complete metadata, `BottomInfoPanel` becomes descriptive-only, and verification stays focused on the existing `Home` Compose tests plus the current connected focus-navigation regression test.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Robolectric Compose tests, Android instrumented Compose tests, Gradle Android app module

---

**Spec Reference:** `docs/superpowers/specs/2026-03-22-home-season-episode-card-overlay-design.md`

## Planned File Structure

- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
  Responsibility: Render a bottom-aligned season/episode overlay only for series items with complete metadata, inside the existing clipped card bounds.
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`
  Responsibility: Remove the duplicate `Сезон N, серия M` row while keeping title, episode title, and release date unchanged.
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
  Responsibility: Add focused Robolectric Compose coverage for the overlay presence, bottom-panel de-duplication, and non-series behavior.
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`
  Responsibility: Re-run the existing connected focus-navigation regression to confirm the card overlay does not disturb remote focus behavior.

## Chunk 1: Home Card Metadata Ownership

### Task 1: Write failing Home UI tests for the new metadata placement

**Files:**
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Extend `HomeScreenTest.kt` with a failing series-card test that expects both a dedicated overlay test tag and exactly one `Сезон 9, серия 13` text node.**

Use a targeted tag contract in the assertion, for example:

```kotlin
composeRule.onNodeWithTag("poster-meta:$firstDetailsUrl").assertExists()
assertEquals(
    1,
    composeRule.onAllNodesWithText("Сезон 9, серия 13").fetchSemanticsNodes().size,
)
```

This should fail before implementation because the overlay tag does not exist yet, even though the lower panel still renders the text.

- [ ] **Step 2: Add a failing movie-card test proving a non-series item does not expose the overlay tag.**

Use the existing seeded movie item and assert:

```kotlin
composeRule.onNodeWithTag("poster-meta:$secondDetailsUrl").assertDoesNotExist()
```

- [ ] **Step 3: Run the focused Robolectric test class and verify it fails for the expected reasons.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: FAIL because `PosterCard` does not yet expose the overlay tag and the new assertions are not satisfied.

### Task 2: Implement the poster-card overlay and remove the lower-panel duplicate

**Files:**
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt`
- Modify: `app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt`
- Modify: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`

- [ ] **Step 1: Add a small label helper in `PosterCard.kt` that returns formatted season/episode text only for series items with both numbers present.**

Keep it local to the card file, for example:

```kotlin
private fun seasonEpisodeLabel(item: ReleaseSummary): String? =
    if (
        item.kind == ReleaseKind.SERIES &&
        item.seasonNumber != null &&
        item.episodeNumber != null
    ) {
        "Сезон ${item.seasonNumber}, серия ${item.episodeNumber}"
    } else {
        null
    }
```

- [ ] **Step 2: Render a bottom-aligned overlay inside the existing `PosterCard` bounds with a dark translucent background, TV-readable text, and a stable test tag.**

Use a structure like:

```kotlin
seasonEpisodeLabel(item)?.let { label ->
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(Color(0xB3000000))
            .testTag("poster-meta:${item.detailsUrl}")
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label, color = Color.White)
    }
}
```

Keep the watched badge untouched in the top-right corner and do not change card width, height, shape, or focus scaling.

- [ ] **Step 3: Remove the `Сезон N, серия M` `Text` block from `BottomInfoPanel.kt` and leave title, episode title, and release date behavior unchanged.**

- [ ] **Step 4: Re-run the focused Robolectric test class and confirm the new Home UI checks pass.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: `BUILD SUCCESSFUL`

### Task 3: Verify regressions and checkpoint the change

**Files:**
- Test: `app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt`
- Test: `app/src/androidTest/java/com/kraat/lostfilmnewtv/ui/HomeScreenTest.kt`

- [ ] **Step 1: Re-run the focused unit test class once more after the implementation settles to confirm no flaky expectations remain.**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.kraat.lostfilmnewtv.ui.home.HomeScreenTest"
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: If a connected Android test target is available, run the existing `HomeScreen` instrumented test class to verify focus movement still passes with the overlay present.**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kraat.lostfilmnewtv.ui.HomeScreenTest
```

Expected: `BUILD SUCCESSFUL`

If no connected device or emulator is available, record that the connected-TV focus regression remains an explicit verification gap before merging.

- [ ] **Step 3: Commit the focused Home overlay change.**

Run:

```powershell
git add app/src/main/java/com/kraat/lostfilmnewtv/ui/components/PosterCard.kt app/src/main/java/com/kraat/lostfilmnewtv/ui/home/BottomInfoPanel.kt app/src/test/java/com/kraat/lostfilmnewtv/ui/home/HomeScreenTest.kt
git commit -m "feat: move home season metadata onto poster cards"
```
