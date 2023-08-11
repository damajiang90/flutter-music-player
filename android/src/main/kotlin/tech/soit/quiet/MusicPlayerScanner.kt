package tech.soit.quiet

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import java.lang.Exception
import java.util.Random

class MusicPlayerScanner(activity: Activity) {
    companion object {
        const val TAG = "MusicPlayerScanner"
    }
    private var activity: Activity = activity
    private var mContentResolver: ContentResolver? = activity.contentResolver
    private val mSongs: MutableList<Song> = ArrayList()
    private val mRandom = Random()
    private val mAlbumMap = HashMap<Long, String>()
    /*  private val mAudioPath = HashMap<Long, String>()*/

    fun scanAllSongs(): List<Song>? {
        mSongs.clear()
        mAlbumMap.clear()
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // load all album art
            loadAlbumArt()
        }

        // query all music audio
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cur = mContentResolver!!.query(uri, null, MediaStore.Audio.Media.IS_MUSIC, null, MediaStore.Audio.Media.IS_MUSIC)
            ?: return mSongs
        if (!cur.moveToFirst()) {
            return mSongs
        }
        val idColumn = cur.getColumnIndex(MediaStore.Audio.Media._ID)
        val artistColumn = cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val titleColumn = cur.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val albumColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumIdColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val durationColumn = cur.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val dateColumn = cur.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val dataColumn = cur.getColumnIndex(MediaStore.Audio.Media.DATA);
        val needContentAlbum = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        do {
            try {
                val id = cur.getLong(idColumn)
                val albumId = cur.getLong(albumIdColumn)
                val albumUrl: String? = if(needContentAlbum) {
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                } else {
                    mAlbumMap[albumId]
                }
                val song = Song(
                    id,
                    cur.getString(artistColumn),
                    cur.getString(titleColumn),
                    cur.getString(albumColumn),
                    albumId,
                    cur.getLong(durationColumn),
                    cur.getString(dataColumn),
                    albumUrl,
                    cur.getLong(dateColumn)
                )
                mSongs.add(song)
            }
            catch (e: Exception) {
                Log.e(TAG, "scan cursor error:${e.stackTrace}")
            }
        } while (cur.moveToNext())
        return mSongs
    }

    private fun loadAlbumArt() {
        val cursor = mContentResolver!!.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART),
            null,
            null,
            null
        )
        if (cursor!!.moveToFirst()) {
            val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Albums._ID)
            val albumArtColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
            do {
                val id = cursor.getLong(albumIdColumn)
                val path = cursor.getString(albumArtColumn)
                if(!TextUtils.isEmpty(path)) {
                    mAlbumMap[id] = path
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    fun getContentResolver(): ContentResolver? {
        return mContentResolver
    }

    fun getRandomSong(): Song? {
        return if (mSongs.size <= 0) null else mSongs[mRandom.nextInt(mSongs.size)]
    }

    fun getAllSongs(): List<Song>? {
        return mSongs
    }

    inner class Song {
        private var id: Long
        private var artist: String
        private var title: String
        private var album: String
        private var albumId: Long = 0
        private var duration: Long
        private var uri: String?
        private var albumArt: String?
        private var dateAdded: Long

        constructor(
            id: Long,
            artist: String,
            title: String,
            album: String,
            albumId: Long,
            duration: Long,
            uri: String?,
            albumArt: String?,
            dateAdded: Long
        ) {
            this.id = id
            this.artist = artist
            this.title = title
            this.album = album
            this.albumId = albumId
            this.duration = duration
            this.uri = uri
            this.albumArt = albumArt
            this.dateAdded = dateAdded
        }

        fun getUri(): String? {
            return uri
        }

        fun toMap(): HashMap<String, Any?> {
            val songsMap = HashMap<String, Any?>()
            songsMap["id"] = id
            songsMap["artists"] = artist
            songsMap["name"] = title
            songsMap["album"] = album
            songsMap["albumId"] = albumId
            songsMap["duration"] = duration
            songsMap["uri"] = uri
            songsMap["imageUrl"] = albumArt
            songsMap["dateAdded"] = dateAdded
            return songsMap
        }
    }
}