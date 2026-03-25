# Series Guide Design

**Goal:** Add a `Гид по сериям` entry point to the series details screen so Android TV users can open a dedicated guide screen, browse seasons and episodes from LostFilm, and jump into the existing episode details flow without changing playback or favorite behavior.

## Scope

Included:
- A second action on the series details screen labeled `Гид по сериям`
- A dedicated TV screen for browsing the LostFilm episode guide
- Repository and parser support for loading `/series/<slug>/seasons`
- Navigation from a guide row into the existing details route for a selected episode
- Loading, empty, and retry states for the guide screen
- Unit tests for parser, repository normalization, and UI state/model behavior

Excluded:
- Inline guide rendering inside the existing details screen
- Direct playback from the guide screen
- Room persistence or DB migration for guide data in the first version
- Changes to torrent parsing, TorrServe integration, or favorite mutation behavior
- Changes to movie details behavior

## Product Behavior

### Entry point

The current details screen already supports a primary action and a vertical stack of secondary actions. We will add `Гид по сериям` as a secondary action for `ReleaseKind.SERIES`.

Behavior:
- Series details: show the existing favorite action and the new `Гид по сериям` action
- Movie details: do not show `Гид по сериям`
- Selecting `Гид по сериям` opens a new dedicated guide screen

### Guide screen

The guide screen is optimized for TV D-pad navigation and keeps the layout intentionally simple:
- series title at the top
- season sections below
- episode rows inside each season

Each episode row shows:
- season and episode number
- Russian episode title when available
- Russian release date
- watched marker when the episode is already marked as watched

When the user selects an episode row, the app navigates to the existing details route for that episode URL. Playback, TorrServe, watched marking, and favorite interactions continue to work through the already existing details screen.

### Focus behavior

When the guide opens from a specific episode details URL, initial focus should land on that same episode inside the guide if present. This keeps the context stable and prevents the user from being dropped at the top of a long list unexpectedly.

## Navigation Design

Add a new destination to the app navigation graph, for example `AppDestination.SeriesGuide`.

Route contract:
- input: the current `detailsUrl`
- output: none; normal back navigation returns to the previous screen

Flow:
1. User opens a series details screen
2. User selects `Гид по сериям`
3. App navigates to the guide route with the current details URL
4. Guide screen loads the normalized series guide
5. User selects an episode
6. App navigates to the existing details route for that episode URL

The guide screen is a sibling destination to the existing details screen rather than a nested inline state within it. This preserves clean boundaries and keeps the current details screen state contract small.

## Data Model

The existing `ReleaseSummary` model should not be reused for the guide screen because it carries feed-specific concerns such as `pageNumber` and `positionInPage`.

Add dedicated guide models:
- `SeriesGuide`
- `SeriesGuideSeason`
- `SeriesGuideEpisode`

Recommended structure:

```kotlin
data class SeriesGuide(
    val seriesTitleRu: String,
    val posterUrl: String?,
    val selectedEpisodeDetailsUrl: String?,
    val seasons: List<SeriesGuideSeason>,
)

data class SeriesGuideSeason(
    val seasonNumber: Int,
    val episodes: List<SeriesGuideEpisode>,
)

data class SeriesGuideEpisode(
    val detailsUrl: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitleRu: String?,
    val releaseDateRu: String,
    val isWatched: Boolean,
)
```

Add a new repository result contract:

```kotlin
sealed interface SeriesGuideResult {
    data class Success(val guide: SeriesGuide) : SeriesGuideResult
    data class Error(val message: String) : SeriesGuideResult
}
```

## Repository and Parser Design

Add a new repository method:

```kotlin
suspend fun loadSeriesGuide(detailsUrl: String): SeriesGuideResult
```

### URL normalization

The method accepts the currently opened `detailsUrl`, which may already point to a specific episode such as:

`https://www.lostfilm.today/series/Ted/season_2/episode_8/`

Inside the repository, normalize it to the series root:

`https://www.lostfilm.today/series/Ted/`

Then request:

`https://www.lostfilm.today/series/Ted/seasons`

This keeps the UI contract simple and avoids making callers discover the series root themselves.

### Parser reuse

The current `LostFilmSeasonEpisodesParser` already knows how to parse rows from the `/seasons` guide. We should extend that parser with a dedicated guide-oriented method such as `parseGuide(...)` rather than reusing the favorites-specific method directly.

Responsibilities:
- parse all episode rows from the guide page
- extract season number, episode number, details URL, episode title, release date, and watched state
- group episodes into ordered seasons
- preserve deterministic ordering for TV navigation

### Watched state

The guide should reuse the same watched-state strategy already used by favorite releases:
- inspect checked state from the HTML page itself
- supplement with AJAX watched marks when a serial id is available

This gives the guide the same notion of watched/unwatched as other app surfaces without introducing a second watched-state system.

### Caching strategy

The first version should not introduce Room persistence for the guide.

Reasons:
- avoids DB schema changes and migration risk
- keeps the change focused on the user-visible feature
- allows later caching as a second step without changing the screen contract

The guide screen will therefore rely on live loading with local `loading/error/retry` states.

## UI State Design

Add a dedicated `SeriesGuideUiState` rather than extending `DetailsUiState`.

Recommended state:

```kotlin
data class SeriesGuideUiState(
    val title: String = "",
    val seasons: List<SeriesGuideSeason> = emptyList(),
    val selectedEpisodeDetailsUrl: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
```

State rules:
- initial open: `isLoading = true`
- success: populate title and seasons, clear error
- empty guide: show an explicit empty state message
- failure: show retry action and keep the rest of the state empty

The details screen should not own this state. It only needs a navigation callback for the new action.

## Error Handling

The guide screen handles errors locally and does not alter the current details screen state.

Cases:
- network failure: show an error message and `Повторить`
- parse failure: show a generic guide-unavailable message and `Повторить`
- empty page: show `Список серий пока недоступен`

For the first version, partial rendering is unnecessary. The screen should either show full parsed content or a clear fallback state.

## Testing Strategy

### Parser tests

Add fixture-backed tests for the guide HTML:
- parses season sections correctly
- parses episode URLs, numbers, titles, and dates
- preserves watched markers
- groups rows into ordered seasons

### Repository tests

Add tests that verify:
- episode details URL is normalized to the series root before requesting `/seasons`
- repository returns `SeriesGuideResult.Success` on valid parsed content
- repository returns `SeriesGuideResult.Error` on fetch or parse failure
- current episode URL is preserved as the initial guide selection target

### UI and model tests

Add tests that verify:
- `Гид по сериям` appears as a secondary action only for series details
- guide screen renders loading, error, and content states
- selecting an episode row triggers navigation to the existing details route
- initial focus or initial selection resolves to the current episode when present

## Success Criteria

- Users can open `Гид по сериям` from a series details screen
- The guide screen shows seasons and episodes from LostFilm `/seasons`
- Selecting an episode opens the existing details screen for that episode
- Movies do not show the guide action
- The first version ships without a new DB migration
- Existing details playback and favorite behavior remain unchanged
