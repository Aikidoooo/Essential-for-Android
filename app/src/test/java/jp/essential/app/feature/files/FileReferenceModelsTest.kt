package jp.essential.app.feature.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReferenceModelsTest {
    @Test fun adaptsPreviewToPortraitAndLandscapeVideos() {
        val portrait = adaptivePreviewAspectRatio(1_080, 1_920)
        val landscape = adaptivePreviewAspectRatio(1_920, 1_080)

        assertEquals(0.5625f, portrait, 0.0001f)
        assertEquals(1.7777f, landscape, 0.0001f)
        assertTrue(isPortraitPreview(portrait))
        assertFalse(isPortraitPreview(landscape))
    }

    @Test fun clampsBrokenOrExtremeMetadata() {
        assertEquals(2.4f, adaptivePreviewAspectRatio(8_000, 1), 0.0001f)
        assertEquals(0.45f, adaptivePreviewAspectRatio(0, 8_000), 0.0001f)
    }

    @Test fun swapsDimensionsForRotatedPortraitVideo() {
        assertEquals(1_080 to 1_920, displayVideoDimensions(1_920, 1_080, 90))
        assertEquals(1_080 to 1_920, displayVideoDimensions(1_920, 1_080, 270))
        assertEquals(1_920 to 1_080, displayVideoDimensions(1_920, 1_080, 0))
    }
}
