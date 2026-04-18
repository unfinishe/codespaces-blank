package de.thomba.andropicsort.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeTransferPolicyTest {
    @Test
    fun `fast path is allowed when source and target names are identical`() {
        assertTrue(NativeTransferPolicy.canUseFastPath("photo.jpg", "photo.jpg"))
    }

    @Test
    fun `fast path is blocked when rename would be required`() {
        assertFalse(NativeTransferPolicy.canUseFastPath("photo.jpg", "photo_1.jpg"))
    }

    @Test
    fun `fast path remains case sensitive for different names`() {
        assertFalse(NativeTransferPolicy.canUseFastPath("Photo.jpg", "photo.jpg"))
    }

    @Test
    fun `fast path supports uppercase and mixed case identical names`() {
        assertTrue(NativeTransferPolicy.canUseFastPath("IMG_0001.JPG", "IMG_0001.JPG"))
        assertTrue(NativeTransferPolicy.canUseFastPath("MyHoliday.Heic", "MyHoliday.Heic"))
    }
}