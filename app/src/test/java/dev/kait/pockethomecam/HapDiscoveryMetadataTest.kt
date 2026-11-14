package dev.kait.pockethomecam

import org.junit.Assert.assertEquals
import org.junit.Test

class HapDiscoveryMetadataTest {
    @Test fun changingCameraServiceChangesAdvertisedConfiguration() {
        assertEquals("10", HapDiscoveryMetadata.category(false))
        assertEquals("17", HapDiscoveryMetadata.category(true))
        assertEquals("1", HapDiscoveryMetadata.configurationNumber(false))
        assertEquals("2", HapDiscoveryMetadata.configurationNumber(true))
    }
}
