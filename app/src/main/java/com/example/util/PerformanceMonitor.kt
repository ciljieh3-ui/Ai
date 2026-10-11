package com.example.util

import android.content.Context
import java.io.File

data class MemoryStats(
    val usedMemoryMb: Long,
    val maxMemoryMb: Long,
    val freeMemoryMb: Long,
    val usedPercentage: Int
)

object PerformanceMonitor {
    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        val percentage = if (maxMemory > 0) ((usedMemory.toFloat() / maxMemory.toFloat()) * 100).toInt() else 0

        return MemoryStats(
            usedMemoryMb = usedMemory,
            maxMemoryMb = maxMemory,
            freeMemoryMb = maxMemory - usedMemory,
            usedPercentage = percentage.coerceIn(0, 100)
        )
    }

    fun clearCache(context: Context): Long {
        var bytesFreed = 0L
        try {
            val cacheDir = context.cacheDir
            bytesFreed += deleteDir(cacheDir)
            val externalCache = context.externalCacheDir
            if (externalCache != null) {
                bytesFreed += deleteDir(externalCache)
            }
            System.gc()
        } catch (_: Exception) {}
        return bytesFreed / (1024 * 1024)
    }

    private fun deleteDir(dir: File?): Long {
        if (dir == null || !dir.isDirectory) return 0L
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            if (child.isDirectory) {
                total += deleteDir(child)
            } else {
                total += child.length()
                child.delete()
            }
        }
        return total
    }
}
