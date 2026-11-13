package com.retrocam.profiles

import com.retrocam.filmengine.FrameFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraProfileSerializationTest {
    @Test
    fun roundTrips() {
        val profile = CameraProfile(
            id = "test_cam", name = "Test Cam", family = CameraFamily.COMPACT, release = "1.0",
            exposureEv = 0f, blackLift = 0f, contrast = 1f,
            grain = GrainSpec(0.4f, 1.5f), vignette = VignetteSpec(0.3f, 0.5f),
            halation = HalationSpec(0f, "#FF3300"), chromaticAberration = 0f, edgeSoftness = 0f,
            flash = null, flareStyle = FlareStyleSpec.NONE, frame = FrameFormat.FULL,
            dateStampColor = "#F28C28", card = CardSpec(CardBody.COMPACT, "#2B2733", "#E2683A"),
        )
        val json = Json.encodeToString(profile)
        assertEquals(profile, Json.decodeFromString<CameraProfile>(json))
    }
}
