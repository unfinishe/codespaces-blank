package de.thomba.andropicsort.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimestampPreservationPolicyTest {
    @Test
    fun `source timestamp is null for null or non-positive values`() {
        assertNull(TimestampPreservationPolicy.sourceTimestampToPreserve(null))
        assertNull(TimestampPreservationPolicy.sourceTimestampToPreserve(0L))
        assertNull(TimestampPreservationPolicy.sourceTimestampToPreserve(-5L))
    }

    @Test
    fun `source timestamp is preserved for positive values`() {
        assertEquals(1_700_000_000_000L, TimestampPreservationPolicy.sourceTimestampToPreserve(1_700_000_000_000L))
    }

    @Test
    fun `timestamps are considered preserved within tolerance`() {
        assertTrue(
            TimestampPreservationPolicy.isPreserved(
                sourceMillis = 1_700_000_000_000L,
                targetMillis = 1_700_000_001_500L,
            )
        )
    }

    @Test
    fun `timestamps are not preserved outside tolerance or when missing`() {
        assertFalse(
            TimestampPreservationPolicy.isPreserved(
                sourceMillis = 1_700_000_000_000L,
                targetMillis = 1_700_000_005_000L,
            )
        )
        assertFalse(TimestampPreservationPolicy.isPreserved(sourceMillis = null, targetMillis = 1L))
        assertFalse(TimestampPreservationPolicy.isPreserved(sourceMillis = 1L, targetMillis = null))
    }
}