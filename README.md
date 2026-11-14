# Pocket Home Cam

Kotlin Android project for repurposing an old phone as a **direct Apple Home camera**, with local processing and no central bridge. Initial development device: Samsung Galaxy S24.

## Current status (v0.1.0-dev)

**This is a capture and protocol-foundation prototype, not a usable HomeKit camera yet.** Do not deploy as a security system.

Implemented:

- Android app to grant camera permission, start/stop a visible foreground camera service.
- Camera2 capture directed to a hardware MediaCodec H.264 encoder (720p/15 FPS initially).
- Concurrent low-resolution YUV frame analysis with sampled grayscale frame-difference motion and hysteresis.
- Bounded, memory-only encoded frame ring buffer for future recording prebuffer.
- Pure Kotlin HAP TLV8 codec with JVM unit tests.
- Screen can be locked after **user-initiated** start; exact behavior requires on-device validation.

Not implemented:

- **No video is transmitted or viewable** yet (encoded frames currently only enter a ring buffer).
- No HAP accessory server, cryptographic pairing, discovery, SRTP, Apple Home live view, audio, object classification or HomeKit Secure Video.
- No automatic camera restart after reboot; Android 15 restricts launching camera foreground services from `BOOT_COMPLETED`.

## Build

Requires Android Studio with JDK 17 and Android SDK 35, plus Gradle 8.11.1. Open this folder as a project and let Android Studio sync the Gradle files. No Gradle wrapper is bundled in this revision.

Install on a physical Android device, grant camera (and notification) permission, press **Start camera**, then lock the screen. Inspect `adb logcat -s PocketHomeCam` for motion transitions and camera failures. Stop the service from the app. The camera's operating status is not currently reflected back to the activity, so the button's text is optimistic.

## Development priorities

1. Test actual S24 capture output combination and handle unsupported FPS/capture configurations.
2. Implement HAP SRP pair setup, pair verify, ChaCha20-Poly1305 encrypted framing, mDNS and persistent identity.
3. Expose a simple HomeKit motion sensor; then add camera stream negotiation and SRTP.
4. Implement HKSV recording/HDS/fMP4 and prebuffer validation.
5. Add TFLite inference, camera zones, settings UI, audio, recovery tests and thermal safeguards.

Design document: [`DESIGN.md`](DESIGN.md).

Pylo reference: https://github.com/tfausak/Pylo (0BSD), reviewed at commit `0e2ae33e1a370c0971d9a2a7c6d023633f71a5a1`.

No Pylo code has been copied into this repository; the implementation is informed by its architecture and public protocol behavior.

## CI and pull requests

Every PR into `main` and every push to `main` runs [Android CI](.github/workflows/android-ci.yml): Android app compilation, the `hap-core` JVM tests, Android local JVM unit tests (motion state/prebuffer), and debug APK assembly. HTML test reports and a successful debug APK are uploaded as workflow artifacts. The workflow can also be run manually.

To **prevent merging** a PR with failing tests, configure GitHub branch protection or a ruleset on `main` requiring the `Build and JVM unit tests` check. CI itself cannot enforce that setting. No device or Apple Home interoperability tests are part of CI yet.
