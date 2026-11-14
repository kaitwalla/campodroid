package dev.kait.pockethomecam

/** Increment configuration number when the advertised accessory service topology changes. */
internal object HapDiscoveryMetadata {
    fun category(experimentalCamera: Boolean) = if (experimentalCamera) "17" else "10"
    fun configurationNumber(experimentalCamera: Boolean) = if (experimentalCamera) "2" else "1"
}
