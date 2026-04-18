package de.thomba.andropicsort.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileNameTimestampExtractorTest {
    @Test
    fun `extracts timestamp from camera style filename`() {
        val result = FileNameTimestampExtractor.extract("IMG_20240102_153045.jpg")

        assertEquals(LocalDateTime.of(2024, 1, 2, 15, 30, 45), result)
    }

    @Test
    fun `extracts timestamp from screenshot style filename`() {
        val result = FileNameTimestampExtractor.extract("Screenshot_2024-03-17-21-14-33.png")

        assertEquals(LocalDateTime.of(2024, 3, 17, 21, 14, 33), result)
    }

    @Test
    fun `extracts timestamp from pixel style filename with milliseconds`() {
        val result = FileNameTimestampExtractor.extract("PXL_20240506_070809123.jpg")

        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8, 9), result)
    }

    @Test
    fun `extracts timestamp from whatsapp style filename`() {
        val result = FileNameTimestampExtractor.extract("WhatsApp Image 2023-12-24 at 08.09.10.jpeg")

        assertEquals(LocalDateTime.of(2023, 12, 24, 8, 9, 10), result)
    }

    @Test
    fun `extracts date only filename as start of day`() {
        val result = FileNameTimestampExtractor.extract("photo_20240210.jpeg")

        assertEquals(LocalDateTime.of(2024, 2, 10, 0, 0, 0), result)
    }

    @Test
    fun `returns null for lowercase uppercase mixed case null and blank non-matches`() {
        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8, 9), FileNameTimestampExtractor.extract("img_20240506_070809.jpg"))
        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8, 9), FileNameTimestampExtractor.extract("IMG_20240506_070809.JPG"))
        assertEquals(LocalDateTime.of(2024, 5, 6, 7, 8, 9), FileNameTimestampExtractor.extract("MyTrip_20240506_070809.Heic"))
        assertNull(FileNameTimestampExtractor.extract(null))
        assertNull(FileNameTimestampExtractor.extract(""))
        assertNull(FileNameTimestampExtractor.extract("   "))
        assertNull(FileNameTimestampExtractor.extract("holiday_photo_final.jpg"))
    }
}