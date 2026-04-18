package de.thomba.andropicsort.core

import kotlin.math.abs

object TimestampPreservationPolicy {
    private const val DEFAULT_TOLERANCE_MILLIS = 2_000L

    fun sourceTimestampToPreserve(lastModifiedMillis: Long?): Long? {
        return lastModifiedMillis?.takeIf { it > 0 }
    }

    fun isPreserved(
        sourceMillis: Long?,
        targetMillis: Long?,
        toleranceMillis: Long = DEFAULT_TOLERANCE_MILLIS,
    ): Boolean {
        val source = sourceTimestampToPreserve(sourceMillis) ?: return false
        val target = sourceTimestampToPreserve(targetMillis) ?: return false
        return abs(source - target) <= toleranceMillis
    }
}