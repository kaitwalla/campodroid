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
- HomeKit setup-code/QR-URI primitives and pairing attempt throttle, with JVM unit tests. The app now displays the manual setup code and pairing status; QR display is deferred.
- Pure Kotlin SRP-6a server handshake (3072-bit group, SHA-512), with an independent client-side round-trip test. Still not exposed to the network.
- HAP pair-setup M1/M3/M5 protocol state machine, SRP proof validation, encrypted identity exchange primitives, Ed25519 signature checks, and failure tests. **Not networked or device-tested.**
- HAP pair verification using ephemeral X25519 and authenticated Ed25519 controller signatures; directional session keys and authenticated HAP frame transport, tested with fragmented TCP delivery.
- Experimental Android Keystore-backed identity/controller store and LAN `_hap._tcp` listener. Only a placeholder motion-sensor accessory is advertised; camera characteristics and HKSV streaming are absent. This has **not** been exercised with Apple Home.
- Motion-analysis transitions are wired to HAP MotionDetected (`aid=1`, `iid=8`) with authenticated `PUT /characteristics` event subscriptions. This notification path is unit-tested but not Apple Home interoperability-tested.
- HAP accessory metadata now declares formats and the Identify characteristic, and authenticated `GET /characteristics?id=1.8` reports current motion state. Unsupported ID requests are rejected; full multi-characteristic HAP query handling is still pending.
- Separate pure Kotlin `:media-rtp` module parses AVC Annex B/AVCC NAL units and packetizes H.264 with RFC 6184 single-NAL/FU-A RTP framing. This is tested independently; the app does **not** serve RTP/SRTP video yet.
- H.264 sample assembly caches SPS/PPS from encoder config buffers and includes them when constructing keyframe access units, with JVM tests for late-joining viewers and encoder reset. The live capture callback and SRTP sender are not connected yet.
- MediaCodec output now passes through the sample assembler on the encoder thread and exposes an access-unit callback (currently logging keyframes for hardware diagnosis). No RTP packets are sent over the network.
- SRTP AES-CM 128-bit session key derivation (zero derivation rate) is implemented in `:media-rtp` and tested against RFC 3711 Appendix B.3 known-answer vectors. This **is not** an SRTP sender; packet encryption, authentication, rollover, RTCP and replay handling remain unimplemented.
- Experimental outbound `AES_CM_128_HMAC_SHA1_80` SRTP protection now encrypts/authenticates strictly ordered basic RTP packets, with RFC 3711 AES-CM vector and sequence-rollover tests. It intentionally rejects extension/CSRC formats, gaps and replay; it has no HAP negotiation, network transport or SRTCP yet.
- A JVM-only `SrtpVideoSession` links H.264 access units through RTP packetization and SRTP protection, with MTU and framing tests. It emits protected in-memory packets only; the app still does not negotiate or send a live stream.
- Pure Kotlin HomeKit camera TLV8 capabilities and guarded Setup Endpoints / Selected Stream parsing now exist in `:hap-core`, with codec-resolution and malformed-input tests. The camera service is deliberately **not advertised** until UDP media transport and session lifecycle are implemented.
- Bounded, asynchronous UDP transmission of encrypted SRTP packets exists as an isolated `:media-rtp` component with a loopback datagram test. It is **not yet wired into the Android HAP server**; no camera network streaming is enabled until authenticated endpoint setup, lifecycle and RTCP are integrated.
- HomeKit Setup Endpoints response serialization now carries the negotiated session ID, video crypto, accessory IPv4 ports and SSRCs, with nested TLV8 round-trip tests. The response is not served until a stream lifecycle manager is in place.
- A single-camera stream lifecycle state machine gates START/END to the current 16-byte session ID, rejects reconfigure and stale session commands, and is covered by JVM transition tests. Transport cleanup remains the Android integration's responsibility.
- Experimental `HapCameraStreamCoordinator` allocates per-session UDP sockets, validates stream IDs, derives SRTP keys and starts/stops the bounded video sender; JVM loopback tests exercise the complete setup/start/video/stop path. The live app does not expose camera characteristics or invoke this coordinator yet.
- Screen can be locked after **user-initiated** start; exact behavior requires on-device validation.

Not implemented:

- **No video is transmitted or viewable** yet (encoded frames currently only enter a ring buffer).
- HAP pairing/discovery now has an experimental Android integration. Apple Home compatibility is unverified; no SRTP, Apple Home camera live view, audio, object classification or HomeKit Secure Video.
- No automatic camera restart after reboot; Android 15 restricts launching camera foreground services from `BOOT_COMPLETED`.

## Build

Requires Android Studio with JDK 17 and Android SDK 35, plus Gradle 8.11.1. Open this folder as a project and let Android Studio sync the Gradle files. No Gradle wrapper is bundled in this revision.

Install on a physical Android device, grant camera (and notification) permission, press **Start camera**, then lock the screen. For experimental Home pairing, open Apple Home → Add Accessory → More Options, select CamPodroid and enter the setup code shown in the Android app. Pairing has **not** been validated against a real Apple Home controller. Inspect `adb logcat -s PocketHomeCam CamPodroidHAP` for motion transitions and pairing/server failures. Stop the service from the app. The camera's operating status is not currently reflected back to the activity, so the button's text is optimistic.

## Development priorities

1. Test actual S24 capture output combination and handle unsupported FPS/capture configurations.
2. Validate Android pairing/discovery on the S24 and harden HAP characteristics, concurrency, rate limiting, controller management, and transport error handling before adding RTP video.
3. Expose a simple HomeKit motion sensor; then add camera stream negotiation and SRTP.
4. Implement HKSV recording/HDS/fMP4 and prebuffer validation.
5. Add TFLite inference, camera zones, settings UI, audio, recovery tests and thermal safeguards.

Design document: [`DESIGN.md`](DESIGN.md).

Galaxy S24 validation steps and expected failures: [`DEVICE_TESTING.md`](DEVICE_TESTING.md).

Pylo reference: https://github.com/tfausak/Pylo (0BSD), reviewed at commit `0e2ae33e1a370c0971d9a2a7c6d023633f71a5a1`.

No Pylo code has been copied into this repository; the implementation is informed by its architecture and public protocol behavior.

## CI and pull requests

Every PR into `main` and every push to `main` runs [Android CI](.github/workflows/android-ci.yml): Android app compilation, the `hap-core` JVM tests, Android local JVM unit tests (motion state/prebuffer), and debug APK assembly. HTML test reports and a successful debug APK are uploaded as workflow artifacts. The workflow can also be run manually.

To **prevent merging** a PR with failing tests, configure GitHub branch protection or a ruleset on `main` requiring the `Build and JVM unit tests` check. CI itself cannot enforce that setting. No device or Apple Home interoperability tests are part of CI yet.
