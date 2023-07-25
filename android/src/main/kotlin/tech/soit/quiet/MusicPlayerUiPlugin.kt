package tech.soit.quiet

import MusicPlayerScannerChannel
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import tech.soit.quiet.player.MusicMetadata
import tech.soit.quiet.player.PlayMode
import tech.soit.quiet.player.PlayQueue
import tech.soit.quiet.service.MusicPlayerService
import tech.soit.quiet.utils.getNext
import tech.soit.quiet.utils.getPrevious


private const val UI_PLUGIN_NAME = "tech.soit.quiet/player.ui"
private const val SCAN_PLUGIN_NAME = "tech.soit.quiet/player.scan"

class MusicPlayerUiPlugin : FlutterPlugin, ActivityAware {

    private var playerUiChannel: MusicPlayerUiChannel? = null
    private var playerScannerChannel: MusicPlayerScannerChannel? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        playerUiChannel = MusicPlayerUiChannel(MethodChannel(binding.binaryMessenger, UI_PLUGIN_NAME), binding.applicationContext)
        playerScannerChannel = MusicPlayerScannerChannel(MethodChannel(binding.binaryMessenger, SCAN_PLUGIN_NAME), binding.applicationContext)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        playerUiChannel?.destroy()
    }

    override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        playerScannerChannel?.onAttachedToActivity(activityPluginBinding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        playerScannerChannel?.onDetachedFromActivityForConfigChanges()
    }

    override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
        playerScannerChannel?.onReattachedToActivityForConfigChanges(activityPluginBinding)
    }

    override fun onDetachedFromActivity() {
        playerScannerChannel?.onDetachedFromActivity()
    }

}


private class MusicPlayerUiChannel : MethodChannel.MethodCallHandler {

    private val remotePlayer: RemotePlayer

    private val uiPlaybackPlugin: MusicPlayerCallbackPlugin

    private var destroyed = false

    constructor(channel: MethodChannel, context: Context) {
        remotePlayer = context.startMusicService()
        uiPlaybackPlugin = MusicPlayerCallbackPlugin(channel)
        channel.setMethodCallHandler(this)
        remotePlayer.doWhenSessionReady {
            if (!destroyed) {
                it.addCallback(uiPlaybackPlugin)
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) =
        remotePlayer.doWhenSessionReady { session ->
            val r: Any? = when (call.method) {
                "init" -> {
                    uiPlaybackPlugin.onMetadataChanged(session.current)
                    uiPlaybackPlugin.onPlayModeChanged(session.playMode)
                    uiPlaybackPlugin.onPlayQueueChanged(session.playQueue)
                    uiPlaybackPlugin.onPlaybackStateChanged(session.playbackState)
                    session.current != null
                }
                "play" -> session.play()
                "pause" -> session.pause()
                "playFromMediaId" -> session.playFromMediaId(call.arguments())
                "prepareFromMediaId" -> session.prepareFromMediaId(call.arguments())
                "skipToNext" -> session.skipToNext()
                "skipToPrevious" -> session.skipToPrevious()
                "seekTo" -> session.seekTo(call.arguments<Number>()!!.toLong())
                "setPlayMode" -> session.playMode = call.arguments() ?: PlayMode.Sequence.rawValue
                "setPlayQueue" -> session.playQueue =
                    PlayQueue(call.arguments<Map<String, Any>>()!!)
                "getNext" -> session.getNext(MusicMetadata.fromMap(call.arguments()!!))?.obj
                "getPrevious" -> session.getPrevious(MusicMetadata.fromMap(call.arguments()!!))?.obj
                "insertToNext" -> session.addMetadata(
                    MusicMetadata.fromMap(call.arguments()!!),
                    session.current?.mediaId
                )
                "setPlaybackSpeed" -> session.setPlaybackSpeed(call.arguments<Double>()!!)
                else -> {
                    result.notImplemented()
                    return@doWhenSessionReady
                }
            }

            when (r) {
                Unit -> result.success(null)
                else -> result.success(r)
            }
        }

    fun destroy() {
        destroyed = true
        remotePlayer.playerSession?.removeCallback(uiPlaybackPlugin)
    }

}


private fun Context.startMusicService(): RemotePlayer {
    val intent = Intent(this, MusicPlayerService::class.java)
    intent.action = MusicPlayerService.ACTION_MUSIC_PLAYER_SERVICE
    startService(intent)
    val player = RemotePlayer()
    if (!bindService(intent, player, Context.BIND_AUTO_CREATE)) {
        if (BuildConfig.DEBUG) {
            throw IllegalStateException("can not connect to MusicService")
        }
    }
    return player
}


private class RemotePlayer : ServiceConnection, CoroutineScope by MainScope() {

    var playerSession: MusicPlayerSession? = null
        private set

    private val pendingExecution = mutableListOf<suspend (MusicPlayerSession) -> Unit>()

    override fun onServiceDisconnected(name: ComponentName?) {
        playerSession = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        playerSession = MusicPlayerSession.Stub.asInterface(service)
        ArrayList(pendingExecution).forEach(::doWhenSessionReady)
        pendingExecution.clear()
    }

    fun doWhenSessionReady(call: suspend (MusicPlayerSession) -> Unit) {
        val session = playerSession
        if (session == null) {
            pendingExecution.add(call)
        } else {
            launch { call(session) }
        }
    }
}
