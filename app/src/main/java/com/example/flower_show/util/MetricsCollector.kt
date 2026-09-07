package com.example.flower_show.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MetricsCollector — In-memory metrics accumulator with statistical summaries.
 *
 * Replaces ad-hoc Log.d("FlowerMetrics") with structured collection.
 * On app exit, write summary report to cache dir and logcat.
 */
object MetricsCollector {

    private val samples = mutableMapOf<String, MetricSeries>()

    /** Record a metric value for a given key. Thread-safe. */
    @Synchronized
    fun record(key: String, value: Long) {
        samples.getOrPut(key) { MetricSeries() }.add(value)
    }

    /** Record a metric value with a string label instead of numeric value. */
    @Synchronized
    fun recordLabel(key: String, label: String) {
        samples.getOrPut(key) { MetricSeries() }.addLabel(label)
    }

    /** Generate a formatted report string. */
    @Synchronized
    fun summary(): String {
        if (samples.isEmpty()) return "[MetricsCollector] No samples recorded."

        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("===== Metrics Report (${df.format(Date())}) =====")
        sb.appendLine()

        // Group by metric name (before first '|')
        val grouped = samples.entries.groupBy { it.key.substringBefore('|') }

        for ((metricName, entries) in grouped) {
            for ((key, series) in entries.sortedBy { it.key }) {
                if (series.labels.isNotEmpty()) {
                    sb.appendLine("[$key]  samples=${series.count}  labels=${series.labels.joinToString(", ")}")
                } else {
                    val avg = series.avg()
                    sb.appendLine(
                        "[$key]  samples=${series.count}  " +
                        "avg=${formatValue(metricName, avg)}  p50=${formatValue(metricName, series.p50())}  " +
                        "p95=${formatValue(metricName, series.p95())}  min=${formatValue(metricName, series.min)}  " +
                        "max=${formatValue(metricName, series.max)}"
                    )
                }
            }

            if (metricName == "cache_bytes") {
                val cachedBytes = entries
                    .filter { it.key.contains("source=cache") }
                    .sumOf { it.value.sum() }
                val upstreamBytes = entries
                    .filter { it.key.contains("source=upstream") }
                    .sumOf { it.value.sum() }
                val totalBytes = cachedBytes + upstreamBytes
                if (totalBytes > 0) {
                    val hitRatio = cachedBytes.toDouble() / totalBytes * 100.0
                    sb.appendLine(
                        "  -> Cache hit ratio: ${"%.1f".format(hitRatio)}% " +
                            "(${formatBytes(cachedBytes)} cached / ${formatBytes(totalBytes)} total)"
                    )
                }
            }

            // Auto-compare cached vs uncached for video_startup
            if (metricName == "video_startup") {
                val cached = entries.find { it.key.contains("cached=true") }?.value
                val uncached = entries.find { it.key.contains("cached=false") }?.value
                if (cached != null && uncached != null && cached.count > 0 && uncached.count > 0) {
                    val uncachedAvg = uncached.avg()
                    // 平均值为 0 时避免除零产生 Infinity/NaN 输出。
                    if (uncachedAvg > 0.0) {
                        val diff = uncachedAvg - cached.avg()
                        val pct = (diff / uncachedAvg * 100).toInt()
                        sb.appendLine("  -> Cache improvement: avg ${formatMs(cached.avg())} vs ${formatMs(uncachedAvg)} (saved ${formatMs(diff.toLong())}, -${pct}%)")
                    }
                }
            }
            if (metricName == "search_query") {
                val weighted = entries.find { it.key.contains("weighted") }?.value
                val levenshtein = entries.find { it.key.contains("levenshtein") }?.value
                if (weighted != null && levenshtein != null && weighted.count > 0 && levenshtein.count > 0) {
                    val diff = levenshtein.avg() - weighted.avg()
                    sb.appendLine("  -> Levenshtein overhead: +${formatMs(diff.toLong())} per query")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("======================================================")
        return sb.toString()
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    private fun formatValue(metricName: String, value: Number): String = when {
        metricName.endsWith("_bytes") -> formatBytes(value)
        // 结果条数之类的计数指标不是毫秒，直接按数字输出
        metricName.endsWith("_count") -> value.toLong().toString()
        else -> formatMs(value)
    }

    private fun formatMs(ms: Number): String = when {
        ms.toLong() < 1000 -> "${ms.toLong()}ms"
        else -> "${"%.1f".format(ms.toDouble() / 1000.0)}s"
    }

    private fun formatBytes(bytes: Number): String {
        val rawBytes = bytes.toDouble()
        return when {
            rawBytes < 1024.0 -> "${rawBytes.toLong()}B"
            rawBytes < 1024.0 * 1024.0 -> "${"%.1f".format(rawBytes / 1024.0)}KB"
            rawBytes < 1024.0 * 1024.0 * 1024.0 -> "${"%.1f".format(rawBytes / 1024.0 / 1024.0)}MB"
            else -> "${"%.1f".format(rawBytes / 1024.0 / 1024.0 / 1024.0)}GB"
        }
    }

    /**
     * Thread-safe metrics series with percentile computation.
     */
    private class MetricSeries {
        private val values = mutableListOf<Long>()
        val labels = mutableListOf<String>()

        @Synchronized
        fun add(value: Long) { values.add(value) }

        @Synchronized
        fun addLabel(label: String) { labels.add(label) }

        val count: Int @Synchronized get() = if (labels.isNotEmpty()) labels.size else values.size
        val min: Long @Synchronized get() = if (values.isEmpty()) 0 else values.min()
        val max: Long @Synchronized get() = if (values.isEmpty()) 0 else values.max()

        @Synchronized
        fun avg(): Double = if (values.isEmpty()) 0.0 else values.average()

        @Synchronized
        fun sum(): Long = values.sum()

        @Synchronized
        fun p50(): Long = percentile(50)

        @Synchronized
        fun p95(): Long = percentile(95)

        private fun percentile(p: Int): Long {
            if (values.isEmpty()) return 0
            val sorted = values.sorted()
            val idx = (p / 100.0 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
    }
}
