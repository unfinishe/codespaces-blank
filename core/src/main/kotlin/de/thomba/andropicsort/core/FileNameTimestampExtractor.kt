package de.thomba.andropicsort.core

import java.time.LocalDateTime

object FileNameTimestampExtractor {
    private val dateTimePatterns = listOf(
        Regex("""(?i)(?:^|\D)(20\d{2})(\d{2})(\d{2})[_-](\d{2})(\d{2})(\d{2})(?:\d{3})?(?:\D|$)"""),
        Regex("""(?i)(?:^|\D)(20\d{2})[-_.](\d{2})[-_.](\d{2})[ _-](\d{2})[.:-](\d{2})[.:-](\d{2})(?:\D|$)"""),
        Regex("""(?i)whatsapp\s+(?:image|video)\s+(20\d{2})-(\d{2})-(\d{2})\s+at\s+(\d{2})\.(\d{2})\.(\d{2})"""),
    )

    private val dateOnlyPatterns = listOf(
        Regex("""(?i)(?:^|\D)(20\d{2})(\d{2})(\d{2})(?:\D|$)"""),
        Regex("""(?i)(?:^|\D)(20\d{2})[-_.](\d{2})[-_.](\d{2})(?:\D|$)"""),
    )

    fun extract(fileName: String?): LocalDateTime? {
        val name = fileName?.trim().takeUnless { it.isNullOrBlank() } ?: return null

        dateTimePatterns.forEach { pattern ->
            val match = pattern.find(name) ?: return@forEach
            val (year, month, day, hour, minute, second) = match.destructured
            buildDateTime(year, month, day, hour, minute, second)?.let { return it }
        }

        dateOnlyPatterns.forEach { pattern ->
            val match = pattern.find(name) ?: return@forEach
            val (year, month, day) = match.destructured
            buildDateTime(year, month, day, "00", "00", "00")?.let { return it }
        }

        return null
    }

    private fun buildDateTime(
        year: String,
        month: String,
        day: String,
        hour: String,
        minute: String,
        second: String,
    ): LocalDateTime? {
        return runCatching {
            LocalDateTime.of(
                year.toInt(),
                month.toInt(),
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt(),
            )
        }.getOrNull()
    }
}