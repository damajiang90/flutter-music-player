package tech.soit.quiet.ext

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSource
import kotlinx.coroutines.runBlocking
import tech.soit.quiet.MusicPlayerServicePlugin
import tech.soit.quiet.player.MusicMetadata
import java.io.File

internal fun MusicMetadata.toMediaSource(
    context: Context,
    servicePlugin: MusicPlayerServicePlugin
): ProgressiveMediaSource {
    var factory: DataSource.Factory = DefaultDataSource.Factory(context)
    factory = UrlUpdatingDataSource.Factory(factory, servicePlugin)
    val mediaItem = MediaItem.Builder()
        .setMediaId(this.mediaId)
        .setUri(buildMediaUri(this))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artists)
            .setDisplayTitle(this.title)
            .setSubtitle(this.subtitle)
            .build()).build()
    return ProgressiveMediaSource.Factory(factory)
        .createMediaSource(mediaItem)
}


private const val SCHEME = "quiet"

/**
 * Build a Decorated Uri for this Media.
 * this uri will handle in [UrlUpdatingDataSource]
 */
private fun buildMediaUri(metadata: MusicMetadata): Uri {
    return if(metadata.isLocal || metadata.isMatchingToLocal) {
        Uri.fromFile(File(metadata.mediaUri))
    }
    else {
        Uri.Builder()
            .scheme(SCHEME)
            .path("player")
            .appendQueryParameter("id", metadata.mediaId)
            .appendQueryParameter("uri", metadata.mediaUri ?: "")
            .build()
    }

}

/**
 * auto update url when we read data from dataSource
 */
private class UrlUpdatingDataSource(
    private val dataSource: DataSource,
    private val backgroundChannel: MusicPlayerServicePlugin
) : DataSource by dataSource {

    class Factory(
        private val factory: DataSource.Factory,
        private val backgroundChannel: MusicPlayerServicePlugin
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return UrlUpdatingDataSource(
                factory.createDataSource(),
                backgroundChannel
            )
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        val former = dataSpec.uri
        return if (former.scheme == SCHEME) {
            val id = former.getQueryParameter("id")!!
            val fallback = former.getQueryParameter("uri")
            val uri = runBlocking {
                val playUrl = backgroundChannel.getPlayUrl(id, fallback)
                if ("asset".equals(playUrl.scheme, true)) {
                    return@runBlocking Uri.parse("asset:///flutter_assets${playUrl.path}")
                } else {
                    playUrl
                }
            }
            val newSpec = dataSpec.withUri(uri)
            dataSource.open(newSpec)
        } else {
            dataSource.open(dataSpec)
        }
    }


    override fun getUri(): Uri? {
        return dataSource.uri
    }


    override fun close() {
        dataSource.close()
    }

}
