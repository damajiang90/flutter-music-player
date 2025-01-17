package tech.soit.quiet.player

import android.content.Context
import android.os.SystemClock
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import kotlinx.coroutines.*
import tech.soit.quiet.MusicPlayerServicePlugin
import tech.soit.quiet.MusicPlayerSession
import tech.soit.quiet.MusicResult
import tech.soit.quiet.MusicSessionCallback
import tech.soit.quiet.ext.durationOrZero
import tech.soit.quiet.ext.mapPlaybackState
import tech.soit.quiet.ext.playbackError
import tech.soit.quiet.ext.toMediaSource
import tech.soit.quiet.service.ShimMusicSessionCallback

class MusicPlayerSessionImpl constructor(private val context: Context) : MusicPlayerSession.Stub(),
    CoroutineScope by MainScope() {

    companion object {
        private val audioAttribute = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

    }

    // Wrap a SimpleExoPlayer with a decorator to handle audio focus for us.
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttribute, true)
            .build().apply {
                addListener(ExoPlayerEventListener())
            }
    }

    @Suppress("JoinDeclarationAndAssignment")
    internal val servicePlugin: MusicPlayerServicePlugin

    private val shimSessionCallback = ShimMusicSessionCallback()

    private var playMode: PlayMode = PlayMode.Sequence

    private var playQueue: PlayQueue = PlayQueue.Empty

    private var metadata: MusicMetadata? = null

    @UiThread
    private fun performPlay(metadata: MusicMetadata?, playWhenReady: Boolean = true) {
        this.metadata = metadata
        invalidateMetadata()
        if (metadata == null) {
            player.stop()
            player.clearMediaItems()
            return
        }
        player.setMediaSource(metadata.toMediaSource(context, servicePlugin))
        player.prepare()
        player.playWhenReady = playWhenReady
        invalidatePlaybackState()
    }


    @WorkerThread
    override fun skipToNext() {
        skipTo { getNext(current) }
    }

    @WorkerThread
    override fun skipToPrevious() {
        skipTo { getPrevious(current) }
    }

    private fun skipTo(playWhenReady: Boolean = true, call: suspend (PlayQueue) -> MusicMetadata?) {
        launch {
            player.stop()
            player.clearMediaItems()
            val next = runCatching { call(playQueue) }.getOrNull()
            performPlay(next, playWhenReady)
        }
    }


    @WorkerThread
    override fun play() {
        launch {
            if (player.playerError != null) {
                player.stop()
                performPlay(metadata)
            } else {
                player.playWhenReady = true
            }
        }
    }

    @WorkerThread
    override fun pause() {
        launch {
            player.playWhenReady = false
        }
    }

    @WorkerThread
    override fun getPlayQueue(): PlayQueue {
        return playQueue
    }

    private suspend fun getPrevious(anchor: MusicMetadata?): MusicMetadata? {
        return playQueue.getPrevious(anchor, playMode)
            ?: servicePlugin.onNoMoreMusic(SkipType.Previous, playQueue, playMode)
    }

    private suspend fun getNext(anchor: MusicMetadata?): MusicMetadata? {
        /*val sb = java.lang.StringBuilder()
        val queue = playQueue.getQueue()
        queue.forEach { item ->
            sb.append(item.title)
            sb.append(',')
        }
        val next = playQueue.getNext(anchor, playMode)
        android.util.Log.e("getNext", "anchor:${anchor?.title},\nnext:${next?.title}\nplayMode:${playMode},playQueue:$sb}")
        */
        return playQueue.getNext(anchor, playMode)
            ?: servicePlugin.onNoMoreMusic(SkipType.Next, playQueue, playMode)
    }

    @WorkerThread
    override fun getPrevious(anchor: MusicMetadata?, result: MusicResult) {
        launch { result.onResult(getPrevious(anchor)) }
    }

    @WorkerThread
    override fun getNext(anchor: MusicMetadata?, result: MusicResult) {
        launch { result.onResult(getNext(anchor)) }
    }

    @WorkerThread
    override fun setPlayQueue(queue: PlayQueue) {
        playQueue.onQueueChanged = null
        queue.onQueueChanged = ::invalidatePlayQueue
        var needStop = playQueue.queueId != queue.queueId;
        playQueue = queue

        needStop = needStop or current.let {
            it ?: return@let false
            playQueue.getByMediaId(it.mediaId) == null
        }
        if (needStop) {
            launch { performPlay(null) }
        }
        invalidatePlayQueue()
    }

    @WorkerThread
    override fun seekTo(pos: Long) {
        launch {
            player.seekTo(pos)
        }
    }

    @WorkerThread
    override fun removeCallback(callback: MusicSessionCallback) {
        shimSessionCallback.removeCallback(callback)
    }

    @WorkerThread
    override fun getPlaybackState(): PlaybackState {
        return playbackStateBackup
    }

    @WorkerThread
    override fun stop() {
        player.playWhenReady = false
    }

    @WorkerThread
    override fun addCallback(callback: MusicSessionCallback) {
        shimSessionCallback.addCallback(callback)
    }

    @WorkerThread
    override fun playFromMediaId(mediaId: String) {
        skipTo { it.getByMediaId(mediaId) }
    }

    @WorkerThread
    override fun prepareFromMediaId(mediaId: String) {
        skipTo(playWhenReady = false) { it.getByMediaId(mediaId) }
    }

    @WorkerThread
    override fun getPlayMode(): Int {
        return playMode.rawValue
    }

    @WorkerThread
    override fun getCurrent(): MusicMetadata? {
        return metadata
    }

    @WorkerThread
    override fun setPlayMode(playMode: Int) {
        this.playMode = PlayMode.valueOf(playMode)
        shimSessionCallback.onPlayModeChanged(playMode)
    }

    @WorkerThread
    override fun addMetadata(metadata: MusicMetadata, anchorMediaId: String?) {
        launch {
            playQueue.add(anchorMediaId, metadata)
            invalidatePlayQueue()
        }
    }

    @WorkerThread
    override fun removeMetadata(mediaId: String) {
        launch {
            playQueue.remove(mediaId)
            invalidatePlayQueue()
        }
    }

    @WorkerThread
    override fun setPlaybackSpeed(speed: Double) {
        launch {
            player.playbackParameters = PlaybackParameters(speed.toFloat())
            invalidatePlaybackState()
        }
    }

    /**
     * insert a list to current playing queue
     *
     * TODO: available for ui channel
     */
    fun insertMetadataList(list: List<MusicMetadata>, index: Int) {
        playQueue.insert(index, list)
        invalidatePlayQueue()
    }

    private var playbackStateBackup: PlaybackState = PlaybackState(
        state = State.None,
        position = 0,
        bufferedPosition = 0,
        speed = 1F,
        error = null,
        updateTime = System.currentTimeMillis(),
        duration = player.durationOrZero()
    )

    private fun invalidatePlaybackState() {
        val playerError = player.playbackError()
        val state = playerError?.let { State.Error } ?: player.mapPlaybackState()
        val playbackState = PlaybackState(
            state = state,
            position = player.currentPosition,
            bufferedPosition = player.bufferedPosition,
            speed = player.playbackParameters.speed,
            error = playerError,
            updateTime = SystemClock.uptimeMillis(),
            duration = player.durationOrZero(),
        )
        this.playbackStateBackup = playbackState
        shimSessionCallback.onPlaybackStateChanged(playbackState)
    }

    private fun invalidateMetadata() {
        metadata = metadata?.copyWith(duration = player.durationOrZero())
        shimSessionCallback.onMetadataChanged(metadata)
    }

    private fun invalidatePlayQueue() {
        shimSessionCallback.onPlayQueueChanged(playQueue)
    }

    @WorkerThread
    override fun destroy() {
        cancel()
    }


    private inner class ExoPlayerEventListener : Player.Listener {

        override fun onIsLoadingChanged(isLoading: Boolean) {

        }

        override fun onPlayerError(error: PlaybackException) {
            invalidatePlaybackState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            invalidatePlaybackState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            invalidatePlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            invalidatePlaybackState()
            // auto play next
            if (playbackState == Player.STATE_ENDED) {
                if (playMode == PlayMode.Single) {
                    player.seekTo(0)
                    player.playWhenReady = true
                } else {
                    skipToNext()
                }
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            invalidatePlaybackState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            invalidateMetadata()
        }

    }


    init {
        servicePlugin = MusicPlayerServicePlugin.startServiceIsolate(context, this)
    }

    private enum class SkipType {
        Next, Previous,
    }

    // return the next music for playing
    private suspend fun MusicPlayerServicePlugin.onNoMoreMusic(
        skip: SkipType,
        playQueue: PlayQueue,
        playMode: PlayMode
    ): MusicMetadata? {
        return when (skip) {
            SkipType.Next -> onPlayNextNoMoreMusic(playQueue, playMode)
            SkipType.Previous -> onPlayPreviousNoMoreMusic(playQueue, playMode)
        }
    }

}
