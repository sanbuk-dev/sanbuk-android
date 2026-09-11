# Sanbuk Android SDK

Official Android SDK for [Sanbuk](https://sanbuk.com) — publisher ad serving in the CPA model.

**[راهنمای فارسی →](README.fa.md)**

> **Status: first preview — [v0.1.0](https://github.com/sanbuk-dev/sanbuk-android/releases/latest).** Banner, native, interstitial and rewarded all work. Not on Maven Central: the `.aar` is self-contained, so you drop it in. The Unity and Flutter shells come later.

## Installing

Download `sanbuk-android-0.1.0.aar` from the [latest release](https://github.com/sanbuk-dev/sanbuk-android/releases/latest), put it in your `libs/` folder, and add:

```kotlin
dependencies {
    implementation(files("libs/sanbuk-android-0.1.0.aar"))
}
```

The artifact declares no dependencies and pulls in no libraries of its own — no androidx,
no networking library, no JSON parser. That is deliberate: plenty of the builds we care
about cannot reach a Maven repository, and an artifact that throws `NoClassDefFoundError`
on first launch is worse than no artifact.

One thing it does need, and every Kotlin project already has: **the Kotlin standard
library.** The SDK is written in Kotlin, so its classes reference `kotlin.jvm.internal.*`.
A `files(...)` dependency carries no metadata, so nothing adds the stdlib on your behalf.
If your app uses Kotlin — almost all do — you already have it and there is nothing to do.
**If your app is pure Java, add it:**

```kotlin
implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
```

Without it the app compiles and installs, then dies on the first Sanbuk call. The Unity
package ships the stdlib as a file for exactly this reason, since a Unity project has no
Kotlin of its own.

```kotlin
// once, in Application.onCreate
Sanbuk.init(context, mediaCode = "YOUR-MEDIA-CODE")
```

**Let us draw it** — a view that fetches, renders, counts the view and routes the click:

```kotlin
SanbukAdView(context).apply { load(placementCode = "HOME-TOP") }
```

**Draw it yourself** — the same ad as data, styled by your own UI:

```kotlin
val ad = Sanbuk.loadAd(placementCode = "HOME-TOP")   // headline, image, cta, colours
// ...render it with your own views...
ad.recordImpression()   // when it is really on screen
ad.click()              // when the user taps it
```

**Full screen** — an interstitial between two moments, or a rewarded ad someone opted into:

```kotlin
SanbukFullscreen.load(context, "LEVEL-END") { ad -> pending = ad }

pending?.show(activity, callbacks = object : SanbukFullscreen.Callbacks {
    override fun onClosed() = startNextLevel()
})

SanbukFullscreen.loadRewarded(context, "EXTRA-LIFE") { ad ->
    ad?.show(activity, callbacks = object : SanbukFullscreen.Callbacks {
        override fun onReward() = grantExtraLife()
    })
}
```

---

## Data first: the server describes an ad, your app draws it

Sanbuk never sends markup. No HTML, no pre-rendered banner, no hidden webview. The reply is a description — headline, body, image, CTA wording, brand colour — and what you do with it is yours.

That is the point of this SDK rather than an incidental detail. An ad drawn with your own fonts, spacing and animation looks like part of your app, which is not something a network that ships a rendered banner can offer you. `SanbukAdView` is a convenience over the same data for apps that would rather not spend an afternoon on it, not a different path.

The trade is honest: when we draw, we can also measure. Under `SanbukAdView` the impression is reported only once at least half the ad was on screen for a full second. When you draw, you decide when `recordImpression()` fires, and we cannot verify it — so media integrated that way are marked as such and watched a little more closely.

## The publisher picks the format, your code picks the drawing

`banner`, `native`, `interstitial` and `rewarded` are chosen when the slot is registered in the Sanbuk panel, and the answer tells you which one came back. Reporting, per-format commission and creative eligibility all hang off that registered value, so it is not something a client may declare.

## Two modules, one implementation

| Module | What lives there | Depends on Android? |
|---|---|---|
| `core` | wire format, offline queue, frequency counters, viewability rule, install identity | no — plain Kotlin/JVM |
| `sdk` | views, Custom Tabs, storage, lifecycle | yes |

Everything that decides anything is in `core`. A rule that lives in a shell has to be written again for every shell — Unity, Flutter, React Native — and shells update at different speeds, so the copies drift. One implementation, many thin bindings.

A side benefit: `core` compiles and tests on any JDK, with no Android SDK in sight.

## What is built

| Piece | Job | Tests |
|---|---|---|
| `ServeUrl` | builds the ad request, carrying the full mobile context | 6 |
| `AdParser` | reads the reply; no malformed input can throw | 8 |
| `ImpressionQueue` | offline queue, bounded by size and age, deduplicated on event id | 7 |
| `ImpressionUrl` | adds the `eid` the server recognises a retry by | 3 |
| `FrequencyCounters` | counts, prunes on the server's window, survives process death | 8 |
| `Viewability` | at least 50% of the pixels for a continuous second (MRC) | 5 |
| `ClickLauncher` | Custom Tab, falling back to the browser — never a WebView | 4 |
| `Storage` | install identity and counters that outlive the process | 4 |
| `ViewabilityTracker` | feeds the rule from both geometry changes and a clock | 4 |
| `FullscreenPolicy` | when an interstitial may interrupt, and when it may be closed | 6 |
| `RewardPolicy` | whether a rewarded ad was actually watched | 5 |
| `FullscreenActivity` | the full-screen screen: gates, countdown, reward, dismissal | 8 |

**70 tests, zero failures.** Release AAR: **113 KB, no dependencies.**

## One self-contained artifact

The AAR carries `core`'s classes rather than depending on a second module. A Gradle
dependency is fine for a build that can reach Maven Central; plenty cannot — a Unity plugin
in particular ships as a dropped-in `.aar` with no resolution step at all — and an artifact
that throws `NoClassDefFoundError` on first launch is worse than no artifact.

`core` stays a real module so the same sources are compiled and unit-tested on a plain JVM.

## Five rules this SDK is built on

**1. Never crash, never die.** No exception from this SDK may reach the host app. The worst allowed outcome is an empty box. A crash we caused is a one-star review for the publisher and an uninstall for us.

**2. No I/O on the main thread.** An ANR during cold start is the fastest way to be removed.

**3. Decisions stay on the server.** Matching, pacing, frequency caps, trial periods, budgets — all of it is server-side and stays there. This SDK asks, draws, and reports. A version shipped to phones is frozen for months; any rule hardened here lives with us for years.

**4. Clicks open a real browser.** Custom Tabs, never an in-app `WebView`. A separate cookie jar breaks first-party attribution, and in a CPA network broken attribution means the publisher did the work and earns nothing.

**5. No advertising identifier.** No GAID, no IDFA. Just a random, resettable `install_id` used for frequency capping and as the rate-limit key that still means something behind carrier NAT.

## Building

```bash
./gradlew build
```

`core` needs only a JDK and can be tested alone (`./gradlew :core:test`). The Android layer
needs the Android SDK; point `local.properties` at it:

```
sdk.dir=/path/to/Android/sdk
```

## License

Apache-2.0
