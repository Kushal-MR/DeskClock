# 🕰️ DeskClock

An always-on smart desk display for Android — turn any old phone into a gorgeous bedside / desk clock with live weather, adaptive brightness, and Spotify "Now Playing" integration.

> **My first Android app!** I built this in Java (instead of Kotlin) because I was just starting out with Android development and Java was what I knew. The goal was to repurpose a spare phone sitting in a desk dock into something that actually looks and feels premium.

---

## ✨ What It Does

DeskClock runs in permanent landscape fullscreen and offers **three swipable clock faces**, each with its own visual personality. The app keeps the screen always on, automatically dims in dark rooms using the light sensor, and shows live weather & battery status across all faces.

### Face 1 — Modern StandBy
A clean, iOS 17 StandBy–inspired split-screen layout with large typography, a weather card (live temperature via [Open-Meteo](https://open-meteo.com/)), and date info. Comes with **6 color themes** (Neon Blaze, Cyberpunk, Purple Haze, Matcha, Rose Gold, Golden Hour) — long-press to switch.

### Face 2 — Retro Split-Flap
A vintage airport / train-station mechanical flip clock. Each digit animates with a satisfying top-and-bottom flap flip. Has its own set of **6 retro themes** (Classic Noir, Crimson, Neon Mint, Amber Glow, Violet, Arctic Blue).

### Face 3 — Spotify Now Playing
The fun one. When music starts playing on Spotify, the app **automatically swipes to this face** and shows:
- 🎵 Track name, artist, and album
- 🖼️ High-res album artwork
- 🌈 A dynamic ambient background glow — colors are extracted live from the album cover
- 📖 Rotating artist trivia facts pulled from Wikipedia (crossfades every 15 seconds)

No Spotify API keys or OAuth login needed — it reads the active media session directly from Android's notification system.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────┐
│                 MainActivity                  │
│  • Ambient light sensor → auto-brightness    │
│  • Open-Meteo weather polling (every 30 min) │
│  • Battery state BroadcastReceiver           │
│  • Spotify media session listener            │
└──────────────────┬───────────────────────────┘
                   │  pushes data via ClockFaceContract
                   ▼
          ┌─── ViewPager2 ───┐
          │                  │
   ┌──────┴──────┐  ┌───────┴───────┐  ┌────────────────┐
   │  Modern     │  │  Retro Split  │  │  Spotify Now   │
   │  StandBy    │  │  Flap         │  │  Playing       │
   │  Fragment   │  │  Fragment     │  │  Fragment      │
   └─────────────┘  └───────────────┘  └────────────────┘
```

**Key design decisions:**
- **`ClockFaceContract`** — a simple interface (`onBatteryUpdate`, `onWeatherUpdate`, `onSpotifyUpdate`) that all fragments implement. The Activity owns the services; fragments just render.
- **`ViewPager2`** with `offscreenPageLimit = 2` keeps all three fragments alive so data pushes reach them instantly.
- **`SplitFlapView`** — a fully custom `View` that draws the mechanical flap halves, divider line, and flip animation from scratch in `onDraw()`.
- **`SpotifyNotificationListener`** — a `NotificationListenerService` that hooks into Android's `MediaSessionManager` to read Spotify metadata without any API auth.
- **`SpotifyDataFetcher`** — fetches HD album art from iTunes Search API, extracts a color palette using Android's `Palette` library (with a saturation-boost algorithm), and pulls artist facts from Wikipedia's REST API.

---

## 📁 Project Structure

```
app/src/main/java/com/kushal/deskclock/
├── MainActivity.java                 # Host activity — sensors, weather, battery, pager
├── ClockFaceContract.java            # Interface for pushing data to fragments
├── ClockFacePagerAdapter.java        # ViewPager2 adapter (3 faces)
│
├── ModernStandByFragment.java        # Face 1 — iOS 17 style clock
├── ThemeManager.java                 # Color palettes for Face 1
├── CustomFontTextClock.java          # TextClock with custom Outfit font
│
├── RetroSplitFlapFragment.java       # Face 2 — flip clock controller
├── SplitFlapView.java                # Custom View — mechanical flap rendering
├── RetroThemeManager.java            # Color palettes for Face 2
│
├── SpotifyStandByFragment.java       # Face 3 — Now Playing display
├── SpotifyNotificationListener.java  # MediaSession listener service
├── SpotifyDataFetcher.java           # Album art, color extraction, Wikipedia facts
└── AmbientGlowView.java             # Custom View — blurred ambient background aura
```

---

## 📱 Setup

### Prerequisites
- Android Studio
- An Android device running **API 28+** (Android 9 Pie or newer)
- (For Spotify face) Spotify app installed on the same device

### Build & Run
1. Clone the repo
   ```bash
   git clone https://github.com/Kushal-MR/DeskClock.git
   ```
2. Open in Android Studio
3. Sync Gradle and run on your device

### Spotify Setup
The Spotify face requires **Notification Listener** permission so the app can read the active media session:

1. Install the Spotify app on the phone and log in
2. Open **Settings → Apps → Special app access → Notification access** (path varies by device)
3. Find **DeskClock** in the list and enable it
4. Play any song on Spotify — the app will auto-detect it and switch to the Now Playing face

> No Spotify developer account, API keys, or OAuth tokens needed. It works entirely through Android's local `MediaSessionManager`.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 11 |
| UI | Android Views, ViewPager2, Custom Views (`Canvas` / `onDraw`) |
| Fonts | [Outfit](https://fonts.google.com/specimen/Outfit) (Bold & Medium) |
| Weather | [Open-Meteo API](https://open-meteo.com/) (free, no key required) |
| Album Art | iTunes Search API |
| Color Extraction | AndroidX Palette |
| Artist Facts | Wikipedia REST API |
| Media Detection | `NotificationListenerService` + `MediaSessionManager` |
| Build System | Gradle (Kotlin DSL) |
| Min SDK | 28 (Android 9) |
| Target SDK | 36 |

---

## 📝 License

This is a personal project. Feel free to explore, learn from, or fork it.
