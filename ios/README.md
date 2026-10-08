# iOS client

This directory contains the first native iOS navigation client. The Android
application remains in the repository root's `app/` module.

## Current iOS scope

- ARKit world tracking and a live camera background
- Capture the current camera frame and send it to the relative-localization API
- Project returned path and doorplate nodes into the AR world
- Select a doorplate destination and plan a route with the Android app's
  minimum-spanning-tree graph and A* search approach
- Draw route segments and speak basic Chinese turn and arrival prompts
- Save the selected scene number on the device

Depth obstacle detection, narrow-door and corridor analysis, automatic
relocalization, voice-command recognition, haptic settings, bilingual UI,
advanced accessibility behavior, and production diagnostics still need to be
ported and tested.

## Configure the backend

Open `Config/Backend.xcconfig` and replace the example URL with the public HTTPS
origin for the localization service. The service must implement:

```text
POST /api/v1/localization/query_relative/{sceneId}
Content-Type: multipart/form-data
file: query.jpg
```

It must return the same `code`, `data.relative_map`, and node fields as the
Android app. The Android development URL (`172.18.36.70`) is a private-network
address and is not suitable for an App Store release.

## Open and run

1. Open `ios/IndoorNav.xcodeproj` in Xcode on a Mac.
2. Set a real HTTPS backend in `Config/Backend.xcconfig`.
3. Choose an ARKit-capable iPhone or iPad as the run destination.
4. Select a signing team in the target's Signing & Capabilities settings.
5. Run the `IndoorNav` scheme and allow camera access.

The project targets iOS 16 and requires ARKit. App Store publication still
needs a production bundle identifier, app icon, privacy review, device testing,
and the remaining feature ports.
