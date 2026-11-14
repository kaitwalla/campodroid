package dev.kait.pockethomecam

/** Display-only state: never regenerate the setup code while controllers are paired. */
internal data class PairingPresentation(val code: String, val status: String, val instructions: String)

internal fun pairingPresentation(code: String, paired: Boolean): PairingPresentation =
    if (paired) PairingPresentation(
        code = "Paired — setup code hidden",
        status = "Paired with Apple Home",
        instructions = "The accessory identity is saved on this phone. Start the camera service to make it available on Wi-Fi."
    ) else PairingPresentation(
        code = code,
        status = "Not paired",
        instructions = "Start the camera, then in Apple Home choose Add Accessory → More Options. Select CamPodroid and enter this setup code. This preview exposes a motion sensor only; video is not available yet."
    )
