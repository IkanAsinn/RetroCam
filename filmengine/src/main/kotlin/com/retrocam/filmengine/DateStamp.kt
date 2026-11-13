package com.retrocam.filmengine

import android.graphics.Bitmap
import java.time.LocalDate

/** 'YY MM DD and DD MM 'YY. */
enum class DateStampFormat { YY_MM_DD, DD_MM_YY }

object DateStampRenderer {
    /** Pure, e.g. "'26 11 13". */
    fun text(date: LocalDate, format: DateStampFormat): String = TODO("phase 3")

    /** 7-segment, bottom-right, drawn in place. */
    fun draw(target: Bitmap, text: String, color: Rgb): Unit = TODO("phase 3")
}
