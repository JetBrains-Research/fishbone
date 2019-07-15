package org.jetbrains.bio.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ExperimentHelper {
    companion object {
        fun timestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH:mm:ss"))
    }
}