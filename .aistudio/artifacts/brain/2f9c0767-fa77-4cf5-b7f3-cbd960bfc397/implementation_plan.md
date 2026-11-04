# VideoHub — Second-Pass Music Player UI Refinement

Fine-tune the VideoHub Now Playing screen to eliminate the harsh rectangular card appearance, blend the ambient blurred artwork seamlessly into the dark background, fix the duration state pipeline so actual media duration appears reliably once metadata loads, optimize foreground artwork scaling across aspect ratios, and boost secondary icon legibility.

## User Review & Critical Decisions

> [!IMPORTANT]
> This second pass implements the 7 exact targeted refinements while strictly preserving VideoHub's core visual concept and architecture:
> 1. **Seamless Ambient Artwork Container**: Replaces the prominent dark rectangular `Card` with a continuous ambient artwork zone featuring soft rounded corners and subtle edge blending that naturally merges with the dark background.
> 2. **Maximized Artwork Proportions**: Optimizes foreground artwork dimensions so landscape, portrait, and square thumbnails occupy more available space without distorting or cropping, reducing unnecessary vertical gaps.
> 3. **Reliable Duration Resolution (`--:--` Fix)**: Integrates `rememberUpdatedState` and ExoPlayer timeline/state listener hooks so metadata and stream duration instantly populate the seekbar instead of remaining stuck on placeholder states.
> 4. **Enhanced Secondary Control Visibility**: Increases inactive icon opacity (from 60% to 80%) for Shuffle and Repeat, ensuring comfortable visibility against dark backgrounds while keeping active states distinct.

---

## 1. Overview & Core Concept

- **What It Does**: Polishes the music player interface into a seamless, high-end listening experience where album artwork feels like an organic part of the screen rather than a boxed-in widget.
- **Target Audience**: VideoHub users listening to music tracks, podcasts, and video audio who expect accurate playback metrics and balanced visual proportions.
- **Key Value**: Delivers organic artwork ambience, instant true duration display, and balanced controls without changing the underlying architecture or navigation.

---

## 2. User Experience & Visual Design

### Key Refinements

1. **Continuous Ambient Artwork Canvas**:
   - Eliminates the heavy dark rectangular card border and drop shadow.
   - The blurred background layer gently blends into the dark background with smooth 22dp rounded bounds and soft edge falloff.
   - The original foreground thumbnail sits centered with native proportions, 16dp rounded corners, and a subtle soft shadow.
2. **Proportion & Spacing Polish**:
   - Reduces excess vertical padding (from 18dp to 12dp) between seekbar and control rows, giving the artwork more vertical breathing room.
   - Foreground thumbnail scales to 94% of available container space while strictly preserving aspect ratio (never stretched or squashed).
3. **Accurate Duration Presentation**:
   - Duration resolution uses `rememberUpdatedState` for `durationProvider` and attaches listeners to ExoPlayer's `onPlaybackStateChanged` and `onTimelineChanged`.
   - As soon as stream metadata or ExoPlayer duration resolves, the seekbar updates from loading placeholder to the real duration (e.g., `3:45`).
4. **Track Information & Control Legibility**:
   - Track title maintains high-contrast bold typography with 2-line capacity and ellipsis.
   - Channel name is clearly separated below with an accessible tap target.
   - Inactive Shuffle and Repeat icons have improved contrast (80% opacity) for effortless scanning in dark mode.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Organic Ambient Canvas vs. Enclosed Card**
  - *Chosen Approach*: Remove `Card` container elevation and dark scrim box; use a dedicated `Box` with soft corner clipping and atmospheric radial blur that naturally dissipates into the dark background.
  - *Why*: The user reported the previous card looked like a dark rectangular border. This change creates a cohesive Apple Music / Spotify ambient aesthetic.
- **Decision 2: State-Updated Duration Pipeline**
  - *Chosen Approach*: Wrap `durationProvider` with `rememberUpdatedState` in `NowPlayingScreen` and trigger duration queries directly on ExoPlayer's `onTimelineChanged` and `onPlaybackStateChanged(STATE_READY)` events.
  - *Why*: Prevents stale closure capture in the background LaunchedEffect loop, ensuring that as soon as `streamInfo` or ExoPlayer extracts duration, the UI reflects it immediately.

---

## 4. Technical Architecture & Component Layout

```
┌────────────────────────────────────────────────────────┐
│ Top Bar: [Collapse]  [NOW PLAYING / VideoHub]  [Actions]│
├────────────────────────────────────────────────────────┤
│ Flexible Content Area (weight = 1f):                   │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ AmbientArtworkArea (BoxWithConstraints)          │  │
│  │  - Seamless background blur (no harsh card frame) │  │
│  │  - Soft corner clipping (22dp)                    │  │
│  │                                                  │  │
│  │  ┌────────────────────────────────────────────┐  │  │
│  │  │ Foreground: AsyncImage (Fit, AspectRatio)  │  │  │
│  │  │ Native proportions, max fill, 16dp radius  │  │  │
│  │  └────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────────┤
│ Title (2-line max) & Channel Link | [Like] [Lyrics] [More]│
├────────────────────────────────────────────────────────┤
│ MusicSeekBar: [Elapsed] ────●──────────── [Duration]   │
│  (Instant update from streamInfo/ExoPlayer timeline)   │
├────────────────────────────────────────────────────────┤
│ Controls: [Shuffle]  [Prev]   [PLAY/PAUSE]   [Next]  [Repeat] │
│  (Secondary controls boosted to 80% contrast)          │
└────────────────────────────────────────────────────────┘
```

### Files to Modify

1. **`NowPlayingScreen.kt`**:
   - Refactor `AdaptiveArtworkContainer` to eliminate the dark `Card` wrapper and scrim, creating an organic ambient area.
   - Add `rememberUpdatedState(durationProvider)` and update `duration` inside `Player.Listener` on timeline/state changes.
   - Boost secondary icon inactive contrast in `NowPlayingPlaybackControlsRow`.
   - Tighten vertical spacers for optimal screen responsiveness.
2. **`MusicModeUI.kt`**:
   - Ensure `durationProvider` dynamically evaluates the latest `mediaPlayer?.duration` and `streamInfo?.duration`.
