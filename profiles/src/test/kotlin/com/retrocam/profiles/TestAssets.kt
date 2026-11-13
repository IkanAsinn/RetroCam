package com.retrocam.profiles

import java.io.File
import kotlinx.serialization.json.Json

/** Reads the shipped assets straight from the source tree (JVM tests run with the module dir as cwd). */
internal object TestAssets {
    val dir: File = File("src/main/assets")
    val profilesDir: File = File(dir, "profiles")
    val lutsDir: File = File(dir, "luts")
    val json = Json { ignoreUnknownKeys = true }

    /** PRD table order (v1.1 types sit inside their family tables). */
    val prdOrder = listOf(
        "gold_snap", "power_800", "green_snap", "mono_snap", "pop_simple", "lebox_red", "aqua",
        "classic_200", "studio_400", "vivid_100", "street_400", "airy_400", "budget_200", "luxe_compact",
        "instant_square", "instant_mini",
        "grit_400", "silver_100",
        "slide_100", "night_800t", "cross_process",
        "plastic_120", "expired_90s", "half_frame", "purple_haze",
    )

    val profileFiles: List<File>
        get() = profilesDir.listFiles { f -> f.extension == "json" && f.name != "index.json" }!!.sortedBy { it.name }

    fun parse(file: File): CameraProfile = json.decodeFromString(file.readText())

    fun profiles(): List<CameraProfile> = profileFiles.map(::parse)

    /** Repository over the source-tree assets (no Android needed for all()/byId()). */
    fun repository() = ProfileRepository(AssetSource { path -> File(dir, path).inputStream() })
}
