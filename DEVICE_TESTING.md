# Galaxy S24 interoperability smoke test

This is an **experimental security-camera prototype**, not an alarm or monitoring system. Do not rely on it to protect a home. Tests below require a real Android phone and an Apple Home controller on the same reachable LAN. No cloud service or HAP bridge is required.

## Install and capture diagnostic logs

1. Download the `campodroid-debug-apk` artifact from the latest successful [Android CI](https://github.com/kaitwalla/campodroid/actions/workflows/android-ci.yml) run on `main`; unzip it on the Mac.
2. Enable developer options and USB debugging on the Galaxy S24, connect it to the Mac, then run `adb devices` and approve the prompt on the phone.
3. Install the APK: `adb install -r app-debug.apk` (adjust the filename to match the artifact).
4. Open **CamPodroid**. Grant camera and notification permission, tap **Start camera**, then leave it plugged into power with its display locked.
5. On the Mac, capture diagnostic log output: `adb logcat -v time -s PocketHomeCam:V CamPodroidHAP:V AndroidRuntime:E '*:S'`.

Expected capture behavior: app remains running with an ongoing notification; motion events log as `Motion active=true` and later `false`; H.264 keyframe diagnostics appear approximately every two seconds. Camera output-combination failures and unsupported encoders will show in the logs. A successful APK build does **not** prove the Galaxy S24 supports the selected simultaneous encoder/YUV outputs.

## HomeKit discovery and pairing

1. Connect the S24 and Apple controller to the same Wi-Fi/LAN; avoid client-isolating guest networks or VPNs during this test.
2. On macOS, run `dns-sd -B _hap._tcp local.`. Look for **CamPodroid** while its service is started.
3. If discovered, inspect `dns-sd -L CamPodroid _hap._tcp local.` and note the `id`, `sf`, `ci`, `pv`, and port (do not publish the setup code or controller secrets).
4. In Apple Home, use **Add Accessory → More Options**, choose CamPodroid if it appears, and enter the **setup code shown in the Android app**. With the experimental checkbox off, CamPodroid advertises a motion sensor.
5. If pairing succeeds, reopen the Android app to refresh pairing status. Test moving in front of the lens and allowing the movement to clear. Confirm whether the Apple Home sensor changes state.

Record the *first failing stage*: discovery, pair-setup, pair-verify, accessory database read, motion subscription, or event reception. Include OS versions, Wi-Fi topology, time, and **redacted** log lines around the failure. Do not reset the app's data casually: the accessory identity and controller pairing record are persisted in Android Keystore-backed storage; wiping data invalidates the previously paired accessory.

## Experimental live video (after motion-only validation)

1. Stop the camera service, check **Experimental Apple Home live video** in CamPodroid, then start the camera again. The preference applies only on service startup.
2. Confirm the `_hap._tcp` TXT record now reports category `ci=17` (IP camera). Observe whether Apple Home discovers the camera service; if already paired, accessory database changes may require a fresh accessory setup or configuration-number update (not yet implemented).
3. Attempt live view in Apple Home. Capture the **first failure**: camera characteristics, Setup Endpoints write (IID 24), subsequent Setup Endpoints read, Selected RTP Stream write (IID 25), or actual UDP video packets.
4. With a Mac on the same LAN, `sudo tcpdump -ni en0 udp` can confirm packet destinations and rates if the interface is `en0`; **do not share packet payloads or cryptographic key material**. Reproduce with normal and locked screens.
5. Stop the camera, turn the checkbox off, and restart to return to motion-only mode.

Experimental video is **not expected to be reliable yet**. Audio, snapshots, RTCP/SRTCP feedback and HKSV are absent; Apple Home may reject the stream or fail to render it even when encrypted UDP packets are sent. An advertised AAC capability is metadata only.

## Known gaps and expected failures

- Apple Home interoperability has **not yet been validated**. Discovery or pairing may fail; that is a useful test result.
- The opt-in camera path now has characteristic negotiation and H.264/SRTP UDP sending, **but has not rendered video in Apple Home**; snapshot, RTCP/SRTCP and interoperability work remains.
- **No HKSV**: no HAP Data Stream, recording management, fragmented MP4, or Home timeline clips.
- No restart-on-boot; relaunch the foreground camera service manually after a reboot.
- Pairing status refreshes when the Android activity resumes; it does not update live while pairing completes.
- The HAP server's exposed protocol and resource limits are still experimental. Test only on a trusted private LAN.

## Morning report template

| Check | Result / notes |
| --- | --- |
| Android / One UI version | |
| Apple Home controller and OS | |
| Encoder and YUV capture with screen locked | |
| Motion transitions in Logcat | |
| H.264 keyframes emitted | |
| Experimental camera characteristics / Setup Endpoints | |
| SRTP UDP packets during live-view attempt | |
| Live-view result (if any) | |
| mDNS `_hap._tcp` discovery | |
| Manual Apple Home pairing | |
| Apple Home motion state updates | |
| Errors / redacted logs | |
