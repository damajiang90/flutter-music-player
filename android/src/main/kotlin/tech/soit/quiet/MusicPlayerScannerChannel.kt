import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Size
import androidx.annotation.RequiresApi
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import tech.soit.quiet.MusicPlayerScanner
import java.io.ByteArrayOutputStream
import java.io.File


class MusicPlayerScannerChannel: MethodCallHandler, RequestPermissionsResultListener {
    private var context: Context
    private lateinit var activity : Activity
    private var scanPendingResult: MethodChannel.Result? = null
    private val STORAGE_PERMISSION_REQUEST_CODE = 4999

    constructor(channel: MethodChannel, context: Context){
        this.context = context
        channel.setMethodCallHandler(this)
    }

    fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
        this.activity = activityPluginBinding.activity
        activityPluginBinding.removeRequestPermissionsResultListener(this)
        activityPluginBinding.addRequestPermissionsResultListener(this)
    }

    fun onDetachedFromActivityForConfigChanges() {
    }

    fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
    }

    fun onDetachedFromActivity() {
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when(call.method) {
            "scanLocalSongs" -> {
                scanPendingResult = result
                checkPermission(true)
            }
            "loadContentThumbnail" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val map = call.arguments as Map<*, *>
                    result.success(loadContentThumbnail(map["uri"] as String))
                }
                else {
                    result.success(null)
                }
            }
            else -> result.notImplemented()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadContentThumbnail(uriStr: String): ByteArray {
        var thumbnail: Bitmap? = null
        val byteArrayStream = ByteArrayOutputStream()
        try {
            val uri = Uri.parse(uriStr)
            thumbnail = context.contentResolver.loadThumbnail(uri, Size(256, 256), null)
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayStream)
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
        finally {
            thumbnail?.recycle()
        }
        return byteArrayStream.toByteArray()
    }

    private fun checkPermission(handlePermission: Boolean) {
        if (checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // TODO: user should be explained why the app needs the permission
                if (handlePermission) {
                    requestPermissions()
                } else {
                    doNoPermissionsError()
                }
            } else {
                if (handlePermission) {
                    requestPermissions()
                } else {
                    doNoPermissionsError()
                }
            }
        } else {
            doScanLocalMusic()
        }
    }

    /*private fun scanMusicFiles(files: Array<File>) {
        for (file in files) {
            if (file.isDirectory) {
                scanMusicFiles(file.listFiles())
            } else {
                activity.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.parse("file://" + file.absolutePath)
                    )
                )
            }
        }
    }*/
    private fun scanAllSongs(): java.util.ArrayList<java.util.HashMap<*, *>>? {
        val scanner = MusicPlayerScanner(activity)
        // Scan all files under Music folder in external storage directory
        //scanMusicFiles(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).listFiles())

        val allSongs: List<MusicPlayerScanner.Song>? = scanner.scanAllSongs()
        val songsMapList = ArrayList<HashMap<*, *>>()
        if(allSongs != null) {
            for (song in allSongs) {
                songsMapList.add(song.toMap())
            }
        }
        return songsMapList
    }

    private fun doScanLocalMusic() {
        scanPendingResult?.success(scanAllSongs())
        scanPendingResult = null
    }

    private fun doNoPermissionsError() {
        scanPendingResult?.error("permission", "you don't have the user permission to access the storage", null)
        scanPendingResult = null
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermissions() {
        activity.requestPermissions(arrayOf<String>(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_REQUEST_CODE)
    }

    private fun shouldShowRequestPermissionRationale(
        activity: Activity,
        permission: String
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            activity.shouldShowRequestPermissionRationale(permission)
        } else false
    }

    private fun checkSelfPermission(context: Context, permission: String?): Int {
        requireNotNull(permission) { "permission is null" }
        return context.checkPermission(permission, Process.myPid(), Process.myUid())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                val permission = permissions[i]
                val grantResult = grantResults[i]
                if (permission == Manifest.permission.READ_EXTERNAL_STORAGE) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        doScanLocalMusic()
                    } else {
                        doNoPermissionsError()
                    }
                }
            }
        }
        return false
    }
}