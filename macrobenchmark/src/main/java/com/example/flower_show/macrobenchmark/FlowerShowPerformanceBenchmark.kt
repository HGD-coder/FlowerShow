package com.example.flower_show.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.example.flower_show"
private const val VIDEO_FIRST_FRAME_TRACE = "FlowerVideoFirstFrame"
private const val VIDEO_BUFFERING_TRACE = "FlowerVideoBuffering"
private const val QUALITY_SWITCH_TRACE = "FlowerQualitySwitch"
private const val WAIT_TIMEOUT_MS = 10_000L

@RunWith(AndroidJUnit4::class)
class FlowerShowPerformanceBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForResource("video_screen")
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun videoFirstFrame() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(VIDEO_FIRST_FRAME_TRACE, TraceSectionMetric.Mode.Sum),
        ),
        iterations = 5,
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
        waitForResource("player_surface")
        Thread.sleep(3_000)
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun feedScrollFramesAndBuffering() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric(VIDEO_BUFFERING_TRACE, TraceSectionMetric.Mode.Count),
            TraceSectionMetric(VIDEO_BUFFERING_TRACE, TraceSectionMetric.Mode.Sum),
        ),
        iterations = 5,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForResource("feed_pager")
            Thread.sleep(1_000)
        },
    ) {
        repeat(6) {
            swipeFeedForward()
            device.waitForIdle()
        }
        Thread.sleep(1_000)
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun qualitySwitchLatency() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(QUALITY_SWITCH_TRACE, TraceSectionMetric.Mode.Sum),
        ),
        iterations = 5,
        compilationMode = CompilationMode.Partial(),
        setupBlock = {
            pressHome()
            startActivityAndWait()
            waitForResource("quality_button")
            Thread.sleep(1_000)
        },
    ) {
        val qualityButton = device.findObject(By.res(TARGET_PACKAGE, "quality_button"))
        qualityButton?.click()
        val lowerQuality = device.wait(Until.findObject(By.textContains("360p")), WAIT_TIMEOUT_MS)
        lowerQuality?.click()
        device.waitForIdle()
        Thread.sleep(3_000)
    }

    private fun MacrobenchmarkScope.waitForResource(resourceId: String) {
        val found = device.wait(
            Until.hasObject(By.res(TARGET_PACKAGE, resourceId)),
            WAIT_TIMEOUT_MS,
        )
        check(found) { "Timed out waiting for $TARGET_PACKAGE:id/$resourceId" }
    }

    private fun swipeFeedForward() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(
            width / 2,
            (height * 0.78f).toInt(),
            width / 2,
            (height * 0.22f).toInt(),
            28,
        )
    }
}
