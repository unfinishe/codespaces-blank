package de.thomba.andropicsort.core

object NativeTransferPolicy {
    fun canUseFastPath(sourceName: String, targetName: String): Boolean {
        return sourceName == targetName
    }
}