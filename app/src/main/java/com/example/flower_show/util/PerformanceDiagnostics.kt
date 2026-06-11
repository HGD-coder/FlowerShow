package com.example.flower_show.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Lightweight in-app performance diagnostics for devices where Macrobenchmark cannot run.
 *
 * Logcat tag: FlowerPerf
 * Report file: <cacheDir>/flower_performance_diagnostics.txt
 */
object PerformanceDiagnostics {
    const val TAG = "FlowerPerf"

    private const val MAX_RECENT_EVENTS = 500
    private const val FRAME_WINDOW_MS = 5_000L
    private const val TARGET_FRAME_NS = 16_666_667L
    private const val JANK_FRAME_NS = 25_000_000L
    private const val FROZEN_FRAME_NS = 700_000_000L

    private val lock = Any()
    private val recentEvents = ArrayDeque<String>()
    private val metricSamples = mutableMapOf<String, MutableList<Long>>()
    private val labelCounts = mutableMapOf<String, MutableMap<String, Int>>()

    @Volatile private var installed = false
    @Volatile private var frameMonitoring = false

    private var lastFrameTimeNs = 0L
    private var frameWindowStartMs = 0L
    private var frameCount = 0
    private var jankFrameCount = 0
    private var frozenFrameCount = 0
    private var droppedFrameEstimate = 0L
    private var totalFrameNs = 0L
    private var maxFrameNs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameMonitoring) return

            val nowMs = elapsedRealtimeMs()
            if (frameWindowStartMs == 0L) {
                frameWindowStartMs = nowMs
            }

            val previousFrameTimeNs = lastFrameTimeNs
            if (previousFrameTimeNs > 0L) {
                val frameDurationNs = (frameTimeNanos - previousFrameTimeNs).coerceAtLeast(0L)
                frameCount += 1
                totalFrameNs += frameDurationNs
                maxFrameNs = maxOf(maxFrameNs, frameDurationNs)
                if (frameDurationNs > JANK_FRAME_NS) {
                    jankFrameCount += 1
                }
                if (frameDurationNs > FROZEN_FRAME_NS) {
                    frozenFrameCount += 1
                }
                droppedFrameEstimate += (frameDurationNs / TARGET_FRAME_NS - 1L).coerceAtLeast(0L)
            }
            lastFrameTimeNs = frameTimeNanos

            if (nowMs - frameWindowStartMs >= FRAME_WINDOW_MS) {
                reportFrameWindow(force = false)
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        event(
            "diagnostics_installed",
            mapOf(
                "device" to "${Build.MANUFACTURER}_${Build.MODEL}",
                "sdk" to Build.VERSION.SDK_INT,
                "report" to reportFile(context).absolutePath,
            ),
        )
    }

    fun markActivityOnCreate(activity: Activity) {
        install(activity.applicationContext)
        val processStartMs = processStartElapsedRealtimeMs()
        val nowMs = elapsedRealtimeMs()
        recordDuration(
            "startup_activity_on_create",
            nowMs - processStartMs,
            mapOf("activity" to activity.javaClass.simpleName),
        )
        installFirstDrawListener(activity.window.decorView, processStartMs)
    }

    fun startFrameMonitoring() {
        if (frameMonitoring) return
        frameMonitoring = true
        resetFrameWindow()
        event("frame_monitor_start")
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFrameMonitoring() {
        if (!frameMonitoring) return
        frameMonitoring = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        reportFrameWindow(force = true)
        event("frame_monitor_stop")
        resetFrameWindow()
    }

    fun event(name: String, attributes: Map<String, Any?> = emptyMap()) {
        appendAndLog(buildLine(type = "event", name = name, value = null, attributes = attributes))
    }

    fun recordDuration(
        name: String,
        durationMs: Long,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        recordMetricSample(name, durationMs)
        appendAndLog(
            buildLine(
                type = "metric",
                name = name,
                value = "durationMs=${durationMs.coerceAtLeast(0L)}",
                attributes = attributes,
            ),
        )
    }

    fun recordSinceProcessStart(
        name: String,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        recordDuration(
            name = name,
            durationMs = elapsedRealtimeMs() - processStartElapsedRealtimeMs(),
            attributes = attributes,
        )
    }

    fun recordValue(
        name: String,
        value: Long,
        unit: String,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        recordMetricSample(name, value)
        appendAndLog(
            buildLine(
                type = "metric",
                name = name,
                value = "$unit=$value",
                attributes = attributes,
            ),
        )
    }

    fun recordLabel(
        name: String,
        label: String,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        synchronized(lock) {
            val counts = labelCounts.getOrPut(name) { mutableMapOf() }
            counts[label] = (counts[label] ?: 0) + 1
        }
        appendAndLog(
            buildLine(
                type = "label",
                name = name,
                value = "label=${sanitize(label)}",
                attributes = attributes,
            ),
        )
    }

    fun flushToDisk(context: Context, metricsCollectorSummary: String? = null): File? {
        return runCatching {
            val file = reportFile(context)
            file.writeText(buildReport(metricsCollectorSummary))
            logDebug("diagnostics_flushed path=${file.absolutePath}")
            file
        }.getOrElse { error ->
            logError("Failed to write diagnostics report", error)
            null
        }
    }

    fun reportPath(context: Context): String = reportFile(context).absolutePath

    fun elapsedRealtimeMs(): Long {
        return runCatching { SystemClock.elapsedRealtime() }
            .getOrElse { System.currentTimeMillis() }
    }

    private fun installFirstDrawListener(view: View, processStartMs: Long) {
        val listener = object : ViewTreeObserver.OnDrawListener {
            private var recorded = false

            override fun onDraw() {
                if (recorded) return
                recorded = true
                val firstDrawMs = elapsedRealtimeMs() - processStartMs
                recordDuration(
                    "startup_first_draw",
                    firstDrawMs,
                    mapOf("view" to view.javaClass.simpleName),
                )
                view.post {
                    if (view.viewTreeObserver.isAlive) {
                        view.viewTreeObserver.removeOnDrawListener(this)
                    }
                }
            }
        }
        view.viewTreeObserver.addOnDrawListener(listener)
    }

    private fun reportFrameWindow(force: Boolean) {
        val frames = frameCount
        if (frames <= 0) return

        val avgFrameMs = totalFrameNs.toDouble() / frames / 1_000_000.0
        val maxFrameMs = maxFrameNs / 1_000_000L
        val jankPercent = (jankFrameCount.toDouble() / frames * 100.0).roundToLong()
        recordDuration(
            "feed_frame_window",
            FRAME_WINDOW_MS,
            mapOf(
                "frames" to frames,
                "avgFrameMs" to "%.1f".format(Locale.US, avgFrameMs),
                "maxFrameMs" to maxFrameMs,
                "jankFrames" to jankFrameCount,
                "jankPercent" to jankPercent,
                "droppedFrameEstimate" to droppedFrameEstimate,
                "frozenFrames" to frozenFrameCount,
                "force" to force,
            ),
        )
        recordValue("feed_frame_jank_percent", jankPercent, "percent")
        recordValue("feed_frame_max_ms", maxFrameMs, "durationMs")
        resetFrameWindow(keepLastFrame = true)
    }

    private fun resetFrameWindow(keepLastFrame: Boolean = false) {
        if (!keepLastFrame) {
            lastFrameTimeNs = 0L
        }
        frameWindowStartMs = elapsedRealtimeMs()
        frameCount = 0
        jankFrameCount = 0
        frozenFrameCount = 0
        droppedFrameEstimate = 0L
        totalFrameNs = 0L
        maxFrameNs = 0L
    }

    private fun recordMetricSample(name: String, value: Long) {
        synchronized(lock) {
            metricSamples.getOrPut(name) { mutableListOf() }.add(value)
        }
    }

    private fun appendAndLog(line: String) {
        logDebug(line)
        synchronized(lock) {
            if (recentEvents.size >= MAX_RECENT_EVENTS) {
                recentEvents.removeFirst()
            }
            recentEvents.addLast("${timestamp()} $line")
        }
    }

    private fun buildReport(metricsCollectorSummary: String?): String {
        val metrics: Map<String, List<Long>>
        val labels: Map<String, Map<String, Int>>
        val events: List<String>
        synchronized(lock) {
            metrics = metricSamples.mapValues { it.value.toList() }
            labels = labelCounts.mapValues { it.value.toMap() }
            events = recentEvents.toList()
        }

        return buildString {
            appendLine("===== Flower Performance Diagnostics (${timestamp()}) =====")
            appendLine()
            appendLine("Logcat filter: FlowerPerf|FlowerMetrics|VideoPlayerManager")
            appendLine()
            appendLine("[Metric samples]")
            if (metrics.isEmpty()) {
                appendLine("No diagnostic metric samples recorded.")
            } else {
                metrics.toSortedMap().forEach { (name, values) ->
                    val sorted = values.sorted()
                    appendLine(
                        "$name samples=${values.size} avg=${values.average().roundToLong()} " +
                            "p50=${percentile(sorted, 50)} p95=${percentile(sorted, 95)} " +
                            "min=${sorted.first()} max=${sorted.last()}",
                    )
                }
            }
            appendLine()
            appendLine("[Labels]")
            if (labels.isEmpty()) {
                appendLine("No diagnostic labels recorded.")
            } else {
                labels.toSortedMap().forEach { (name, counts) ->
                    appendLine("$name ${counts.toSortedMap()}")
                }
            }
            appendLine()
            appendLine("[Recent events]")
            events.forEach(::appendLine)
            if (!metricsCollectorSummary.isNullOrBlank()) {
                appendLine()
                appendLine("[MetricsCollector]")
                appendLine(metricsCollectorSummary)
            }
            appendLine("===========================================================")
        }
    }

    private fun buildLine(
        type: String,
        name: String,
        value: String?,
        attributes: Map<String, Any?>,
    ): String {
        val parts = mutableListOf(type, "name=$name")
        if (value != null) parts += value
        attributes.entries
            .filter { it.value != null }
            .forEach { (key, rawValue) -> parts += "$key=${sanitize(rawValue)}" }
        return parts.joinToString(" ")
    }

    private fun percentile(sortedValues: List<Long>, percentile: Int): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = (percentile / 100.0 * (sortedValues.size - 1)).toInt()
            .coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }

    private fun sanitize(value: Any?): String {
        return value
            ?.toString()
            ?.replace(Regex("\\s+"), "_")
            ?.take(160)
            ?: "null"
    }

    private fun reportFile(context: Context): File {
        return File(context.applicationContext.cacheDir, "flower_performance_diagnostics.txt")
    }

    private fun processStartElapsedRealtimeMs(): Long {
        return runCatching { Process.getStartElapsedRealtime() }
            .getOrElse { elapsedRealtimeMs() }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    }
}
