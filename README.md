# Indoor AR Navigation

An Android/ARCore prototype for indoor navigation and accessibility assistance.
It combines camera-based relative localization, ARCore pose tracking, graph/A*
path planning, MediaPipe EfficientDet obstacle detection, depth-based corridor
and narrow-door analysis, voice commands, text-to-speech, and haptic feedback.

## Build

Open the project in Android Studio with an Android SDK installed, or run:

```powershell
./gradlew assembleDebug
```

The app requires an ARCore-compatible Android device. It requests camera,
microphone, network, and vibration permissions.

## Optional Baidu speech configuration

The repository intentionally does not contain a speech token. To enable Baidu
short-speech recognition, provide credentials as Gradle project properties:

```powershell
./gradlew assembleDebug `
  -PBAIDU_ASR_TOKEN=your-token `
  -PBAIDU_ASR_CUID=your-cuid
```

Do not commit tokens or `local.properties`. The localization server URL is
currently configured in `NavigationActivity.java` and may need to be changed
for the deployment environment.

## Project notes

See [`docs/navigation-architecture.md`](docs/navigation-architecture.md) for
the current module responsibilities and navigation flow.
