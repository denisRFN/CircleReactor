# Circle Reactor

Android-only test automation app for your own 9-circle reaction game.

It captures the screen, auto-detects 9 colored circles, watches their center colors, and uses Android Accessibility gestures to tap the circle that changes color.

## Expected Latency

On a decent Android phone the target is roughly 80-200 ms:

- screen frame: 16-33 ms
- 9-zone color check: under 5 ms
- accessibility tap dispatch: usually 30-120 ms

## How To Run

Open `D:\CircleReactor` in Android Studio and press Run on an Android phone.

Required on the phone:

1. Enable `Circle Reactor` in Android Accessibility settings.
2. Start screen detector in the app and accept screen capture permission.
3. Open your own game.
4. Keep the 9 circles visible and stable for 1-2 seconds so auto-detection can lock on.

## Detection Model

This does not use AI because AI would be slower and unnecessary. The app uses fast image processing:

- downscaled screen capture at 360 px width;
- saturated/bright connected-component detection;
- keeps the 9 largest circle-like components;
- samples only the center area of each circle;
- taps when the sampled color differs strongly from baseline.

## Important

Use this only for your own game or QA test apps. Do not use it to automate third-party games or services.


## Build Notes

This project needs Android Studio or a local Gradle + JDK 17 setup. The current machine only exposes Java 8 and no `gradle` command in PATH, so command-line build cannot run here yet.

Recommended first run:

1. Install Android Studio.
2. Open `D:\CircleReactor`.
3. Let Android Studio install the Android Gradle plugin and SDK 35.
4. Connect your Android phone with USB debugging enabled.
5. Press Run.

## Tuning For Your Game

If it detects too slowly or taps wrong targets, tune these values in `app/src/main/java/com/pricelens/circlereactor/vision/CircleDetector.kt`:

- `activationThreshold`: lower means more sensitive.
- `minTapIntervalMs`: lower means faster repeated taps.
- `br > 75f && sat > 0.16f`: controls automatic circle detection.

For best speed, keep the 9 circles in fixed positions and use a strong active color change.

## Online APK Build With GitHub Actions

This repository is ready to build a debug APK online without Android Studio. After you push it to GitHub, the workflow in `.github/workflows/android-debug-apk.yml` runs automatically.

Steps:

1. Create an empty GitHub repository, for example `circle-reactor`.
2. Push this folder to that repository.
3. Open the GitHub repo and go to `Actions`.
4. Open the latest `Android Debug APK` workflow run.
5. Download the artifact named `circle-reactor-debug-apk`.
6. Inside the downloaded zip you will find `app-debug.apk`.
7. Send the APK to your Android phone and install it.

The workflow uses hosted Linux runners, JDK 17, Android SDK 35, and Gradle 8.10.2, so you do not need local Gradle or Android Studio just to produce the APK.

