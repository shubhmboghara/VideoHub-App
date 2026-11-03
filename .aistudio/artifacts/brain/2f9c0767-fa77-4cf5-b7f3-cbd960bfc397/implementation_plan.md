# Fix Download Visuals, Subscription Thumbnails & Foreground Service Crash

Comprehensive architectural plan to eliminate the `ForegroundServiceDidNotStartInTimeException` crash in `DownloadService`, fix the thumbnail mix-up when subscribing from the player watch screen, and enrich download cards with media thumbnails and video/audio resolution pills.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following implementation decisions have been aligned based on user clarification:
> - **Download Cards**: Active and completed downloading items will display a prominent 16:9 thumbnail, format badge (`VIDEO` vs `AUDIO`/`MUSIC`), and resolution/bitrate pill (e.g. `1080p`, `320kbps M4A`) alongside the direct cancel (X) action.
> - **Subscription Feed Items**: Items in the subscriptions view will present the video's actual 16:9 thumbnail with a small circular channel avatar badge and title.
> - **Download Service Stability**: Foreground service lifecycle will be strictly hardened so `startForeground()` is called synchronously in `onCreate()` and at the top of `onStartCommand()`, preventing ANRs and `RemoteServiceException`.

---

## 1. Overview & Core Concept

### What It Does
1. **Immediate Foreground Service Startup**: Ensures `DownloadService` immediately satisfies Android 8.0–14+ foreground service timing contracts by invoking `startForeground(...)` synchronously in `onCreate()`. This prevents crashes when downloads start or when cancel intents are dispatched.
2. **Proper Channel vs. Video Thumbnail Mapping**: Corrects `PlayerScreenMetadata` where subscribing from the watch screen was saving the video's thumbnail into `ChannelEntity.thumbnailUrl`. It will now store the channel's actual avatar/logo, ensuring the subscription avatar strip and subscriptions feed display the correct artwork.
3. **Download Item Media Identification**: Enhances `DownloadProgressTracker`, `DownloadService`, and `ActiveDownloadCard` in `DownloadsScreen.kt` to transmit and display rich thumbnails, media type badges (`VIDEO` / `AUDIO`), quality/resolution labels, and a cancel button.

### Target Audience & Benefit
Users downloading offline videos or music and navigating subscriptions will experience zero background crashes, clear visual feedback of what content and quality is downloading, and accurate channel branding across the subscription feed.

---

## 2. User Experience & Visual Design

### Key User Flows

#### A. Starting and Managing Downloads
1. User taps download on any video or audio stream in player or feeds.
2. `DownloadService` starts immediately with an ongoing notification. No crash occurs even if cancelled quickly.
3. In the **Downloads** tab, the active download card shows:
   - **Visual Thumbnail**: 72×44dp rounded preview image of the video or album art.
   - **Media Badges**: A pill badge with an icon indicating either `VIDEO` (cyan/blue tint) with resolution (`1080p`, `720p`, etc.) or `AUDIO` (violet/purple tint) with bitrate (`320 kbps`, `128 kbps`).
   - **Progress & Speed**: Real-time progress bar, downloaded MB / total MB, and download percentage.
   - **Instant Cancel**: Accessible cancel icon button with ripple effect that stops the job immediately and clears temp files.

#### B. Subscribing from the Video Player & Viewing Subscriptions
1. While watching a video, the user taps **Subscribe**.
2. The channel's true avatar URL (`uploaderAvatars` or channel avatar) is recorded in Room (`ChannelEntity.thumbnailUrl`), not the video's thumbnail.
3. Upon opening the **Subscriptions** tab:
   - The top horizontal channel carousel displays the circular creator avatars.
   - The video list displays each video's 16:9 thumbnail, accompanied by a small circular creator avatar badge, title, view count, and relative time.

---

## 3. Key Product Decisions & Trade-Offs

- **Immediate `startForeground` in `onCreate()` vs Lazy in `onStartCommand()`**:
  - *Chosen Approach*: Call `startForeground()` synchronously within `onCreate()` using a persistent notification channel and update the notification in `onStartCommand()`.
  - *Why*: Android throws `ForegroundServiceDidNotStartInTimeException` if `startForeground()` is not called within milliseconds after `startForegroundService()`. Cancel intents or extraction delays previously caused the service to return before calling `startForeground()`, causing fatal crashes.
- **Passing Metadata in `DownloadProgressTracker`**:
  - *Chosen Approach*: Extend `DownloadProgress` data class with `thumbnailUrl`, `isAudioOnly`, and `quality` fields, propagating them directly from `DownloadService`.
  - *Why*: Prevents `DownloadsScreen` from querying disk or making network requests just to display the thumbnail and format badge while downloading.
- **Channel Avatar Sanitization in Subscription Persistence**:
  - *Chosen Approach*: In `PlayerScreenMetadata`, obtain `avatarUrl` from `streamInfo.uploaderAvatars` or fetch fallback channel details rather than reusing the video's `thumbnailUrl`.
  - *Why*: Directly eliminates the root cause of the channel avatar vs video thumbnail confusion.

---

## 4. Technical Architecture & Data Strategy

### System Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           VideHub Application                               │
├──────────────────────────────┬──────────────────────────────────────────────┤
│       Player Screen          │             Downloads Screen                 │
│  ┌────────────────────────┐  │  ┌────────────────────────────────────────┐  │
│  │  PlayerScreenMetadata  │  │  │          ActiveDownloadCard            │  │
│  │  - Subscribe Button    │  │  │  - 16:9 Thumbnail Image (AsyncImage)   │  │
│  │  - Channel Avatar URL  │  │  │  - [VIDEO 1080p] / [AUDIO 320k] Badges │  │
│  └───────────┬────────────┘  │  │  - Progress Bar & Cancel Button        │  │
│              │               │  └───────────────────▲────────────────────┘  │
│              ▼               │                      │ Flow Observation      │
│  ┌────────────────────────┐  │  ┌───────────────────┴────────────────────┐  │
│  │  ChannelDao (Room DB)  │  │  │     DownloadProgressTracker            │  │
│  │  - stores true avatar  │  │  │     - holds title, thumb, type, res    │  │
│  └───────────┬────────────┘  │  └───────────────────▲────────────────────┘  │
│              │               │                      │ progress updates      │
│              ▼               │  ┌───────────────────┴────────────────────┐  │
│  ┌────────────────────────┐  │  │         DownloadService                │  │
│  │   SubscriptionsScreen  │  │  │  - onCreate() -> startForeground()    │  │
│  │  - Top: Channel Avatars│  │  │  - onStartCommand() -> handles CANCEL │  │
│  │  - List: Video Thumbs  │  │  │  - Downloads stream to storage         │  │
│  └────────────────────────┘  │  └────────────────────────────────────────┘  │
└──────────────────────────────┴──────────────────────────────────────────────┘
```

### Files & Modules Involved
1. **`DownloadService.kt`**:
   - Call `startForeground(NOTIFICATION_ID, notification)` inside `onCreate()`.
   - In `onStartCommand()`, safely handle `CANCEL_` intents without violating foreground service lifecycle.
   - Send `thumbnailUrl`, `isAudioOnly`, and `quality` to `DownloadProgressTracker`.
2. **`DownloadProgressTracker.kt`**:
   - Add `isAudioOnly: Boolean = false`, `quality: String? = null`, and ensure `thumbnailUrl` is preserved and updated.
3. **`DownloadsScreen.kt`**:
   - Update `ActiveDownloadCard` layout with `AsyncImage` thumbnail preview, type/resolution badges, and cancel button.
4. **`PlayerScreenMetadata.kt`**:
   - Fix subscription insertion to store `avatarUrl` (creator avatar), never the video thumbnail.
5. **`SubscriptionsScreen.kt`**:
   - Ensure video cards display the video thumbnail and channel avatar badge clearly.
