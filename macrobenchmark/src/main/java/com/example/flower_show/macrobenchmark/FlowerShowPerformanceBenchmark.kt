package com.example.flower_show.macrobenchmark

import android.os.Build
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
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
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.example.flower_show"
private const val VIDEO_FIRST_FRAME_TRACE = "FlowerVideoFirstFrame"
private const val VIDEO_BUFFERING_TRACE = "FlowerVideoBuffering"
private const val QUALITY_SWITCH_TRACE = "FlowerQualitySwitch"
private const val WAIT_TIMEOUT_MS = 10_000L
private const val STARTUP_ITERATIONS = 3
private const val INTERACTION_ITERATIONS = 2

@OptIn(ExperimentalMacrobenchmarkApi::class)
private val BASELINE_COMPILATION_MODE = CompilationMode.Ignore()

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMacrobenchmarkApi::class)
class FlowerShowPerformanceBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun coldStartup() {
        assumeMacrobenchmarkSupportedDevice()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = STARTUP_ITERATIONS,
            compilationMode = BASELINE_COMPILATION_MODE,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            waitForAppWindow()
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun videoFirstFrame() {
        assumeMacrobenchmarkSupportedDevice()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                TraceSectionMetric(VIDEO_FIRST_FRAME_TRACE, TraceSectionMetric.Mode.Sum),
            ),
            iterations = STARTUP_ITERATIONS,
            compilationMode = BASELINE_COMPILATION_MODE,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            waitForAppWindow()
            Thread.sleep(3_000)
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun feedScrollFramesAndBuffering() {
        assumeMacrobenchmarkSupportedDevice()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric(VIDEO_BUFFERING_TRACE, TraceSectionMetric.Mode.Count),
                TraceSectionMetric(VIDEO_BUFFERING_TRACE, TraceSectionMetric.Mode.Sum),
            ),
            iterations = INTERACTION_ITERATIONS,
            compilationMode = BASELINE_COMPILATION_MODE,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForAppWindow()
                Thread.sleep(1_000)
            },
        ) {
            repeat(6) {
                swipeFeedForward()
                device.waitForIdle()
            }
            Thread.sleep(1_000)
        }
    }

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun qualitySwitchLatency() {
        assumeMacrobenchmarkSupportedDevice()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                TraceSectionMetric(QUALITY_SWITCH_TRACE, TraceSectionMetric.Mode.Sum),
            ),
            iterations = INTERACTION_ITERATIONS,
            compilationMode = BASELINE_COMPILATION_MODE,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForAppWindow()
                Thread.sleep(1_000)
            },
        ) {
            val qualityButton = device.wait(Until.findObject(By.textContains("\u753b\u8d28")), WAIT_TIMEOUT_MS)
            qualityButton?.click()
            val lowerQuality = device.wait(Until.findObject(By.textContains("360p")), WAIT_TIMEOUT_MS)
            lowerQuality?.click()
            device.waitForIdle()
            Thread.sleep(3_000)
        }
    }

    private fun waitForAppWindow() {
        val found = device.wait(
            Until.hasObject(By.pkg(TARGET_PACKAGE)),
            WAIT_TIMEOUT_MS,
        )
        check(found) { "Timed out waiting for $TARGET_PACKAGE window" }
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

    private fun isVivoUserBuild(): Boolean =
        Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
            Build.MODEL.equals("V2359A", ignoreCase = true)

    private fun assumeMacrobenchmarkSupportedDevice() {
        assumeFalse(
            "Macrobenchmark Perfetto/UiAutomation is unstable on this vivo user build; run on Pixel/emulator.",
            isVivoUserBuild(),
        )
    }
}
