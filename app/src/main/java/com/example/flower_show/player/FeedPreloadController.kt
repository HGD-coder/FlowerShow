package com.example.flower_show.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RendererCapabilitiesList
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.example.flower_show.util.MetricsCollector
import com.example.flower_show.util.PerformanceDiagnostics
import kotlin.math.abs

class FeedPreloadController(
    mediaSourceFactory: MediaSource.Factory,
    trackSelector: TrackSelector,
    bandwidthMeter: BandwidthMeter,
    renderersFactory: DefaultRenderersFactory,
    allocator: Allocator,
    preloadLooper: Looper,
) {
    private val targetControl = FeedTargetPreloadStatusControl()
    private val preloadManager = DefaultPreloadManager(
        targetControl,
        mediaSourceFactory,
        trackSelector,
        bandwidthMeter,
        FeedRendererCapabilitiesListFactory(renderersFactory),
        allocator,
        preloadLooper,
    )
    private val trackedMediaItemsByUrl = linkedMapOf<String, MediaItem>()
    private var currentPlayingUrl: String? = null

    fun getMediaSource(url: String): MediaSource? {
        return preloadManager.getMediaSource(MediaItem.fromUri(url))
    }

    fun onPlaybackStarted(index: Int, url: String) {
        currentPlayingUrl = url
        targetControl.currentPlayingIndex = index
        preloadManager.setCurrentPlayingIndex(index)
        preloadManager.invalidate()
    }

    fun updateWindow(currentIndex: Int, requests: List<VideoPreloadRequest>) {
        targetControl.currentPlayingIndex = currentIndex
        preloadManager.setCurrentPlayingIndex(currentIndex)

        val desiredRequests = requests
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
        val desiredUrls = desiredRequests.mapTo(mutableSetOf()) { it.url }
        val currentUrl = currentPlayingUrl
        var removedCount = 0
        var addedCount = 0

        val iterator = trackedMediaItemsByUrl.iterator()
        while (iterator.hasNext()) {
            val (url, mediaItem) = iterator.next()
            if (url !in desiredUrls && url != currentUrl) {
                preloadManager.remove(mediaItem)
                iterator.remove()
                removedCount += 1
            }
        }

        desiredRequests.forEach { request ->
            if (!trackedMediaItemsByUrl.containsKey(request.url)) {
                val mediaItem = MediaItem.fromUri(request.url)
                preloadManager.add(mediaItem, request.index)
                trackedMediaItemsByUrl[request.url] = mediaItem
                addedCount += 1
            }
        }

        preloadManager.invalidate()
        MetricsCollector.record("preload_window_size", desiredRequests.size.toLong())
        PerformanceDiagnostics.event(
            "preload_window",
            mapOf(
                "currentIndex" to currentIndex,
                "desiredSize" to desiredRequests.size,
                "trackedSize" to trackedMediaItemsByUrl.size,
                "added" to addedCount,
                "removed" to removedCount,
            ),
        )
        Log.d(TAG, "Preload window current=$currentIndex size=${desiredRequests.size}")
    }

    fun release() {
        trackedMediaItemsByUrl.clear()
        preloadManager.release()
    }

    private class FeedTargetPreloadStatusControl : TargetPreloadStatusControl<Int> {
        var currentPlayingIndex: Int = C.INDEX_UNSET

        override fun getTargetPreloadStatus(rankingData: Int): TargetPreloadStatusControl.PreloadStatus? {
            if (currentPlayingIndex == C.INDEX_UNSET) return null

            val distance = rankingData - currentPlayingIndex
            return when {
                distance == 0 -> DefaultPreloadManager.Status(
                    DefaultPreloadManager.Status.STAGE_SOURCE_PREPARED,
                )
                distance in 1..NEXT_VIDEO_COUNT -> DefaultPreloadManager.Status(
                    DefaultPreloadManager.Status.STAGE_LOADED_TO_POSITION_MS,
                    NEXT_VIDEO_PRELOAD_MS,
                )
                distance == -1 -> DefaultPreloadManager.Status(
                    DefaultPreloadManager.Status.STAGE_LOADED_TO_POSITION_MS,
                    PREVIOUS_VIDEO_PRELOAD_MS,
                )
                abs(distance) <= SOURCE_ONLY_DISTANCE -> DefaultPreloadManager.Status(
                    DefaultPreloadManager.Status.STAGE_SOURCE_PREPARED,
                )
                else -> null
            }
        }
    }

    private class FeedRendererCapabilitiesListFactory(
        private val renderersFactory: DefaultRenderersFactory,
    ) : RendererCapabilitiesList.Factory {
        override fun createRendererCapabilitiesList(): RendererCapabilitiesList {
            val handler = Handler(Looper.getMainLooper())
            val renderers = renderersFactory.createRenderers(
                handler,
                object : VideoRendererEventListener {},
                object : AudioRendererEventListener {},
                object : TextOutput {
                    override fun onCues(cueGroup: CueGroup) = Unit
                },
                object : MetadataOutput {
                    override fun onMetadata(metadata: Metadata) = Unit
                },
            )
            return FeedRendererCapabilitiesList(renderers)
        }
    }

    private class FeedRendererCapabilitiesList(
        private val renderers: Array<Renderer>,
    ) : RendererCapabilitiesList {
        private val rendererCapabilities: Array<RendererCapabilities> =
            renderers.map { it.capabilities }.toTypedArray()

        override fun getRendererCapabilities(): Array<RendererCapabilities> = rendererCapabilities

        override fun size(): Int = rendererCapabilities.size

        override fun release() {
            renderers.forEach { it.release() }
        }
    }

    private companion object {
        private const val TAG = "FeedPreloadController"
        private const val NEXT_VIDEO_COUNT = 1
        private const val SOURCE_ONLY_DISTANCE = 3
        private const val NEXT_VIDEO_PRELOAD_MS = 2_500L
        private const val PREVIOUS_VIDEO_PRELOAD_MS = 750L
    }
}
