package tech.soit.quiet

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.util.Size
import java.io.FileNotFoundException
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
    private val mAudioPath = HashMap<Long, String>()

    fun scanAllSongs(): List<Song>? {
        mSongs.clear()
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // load all album art
            loadAlbumArt()
        }

        // query all audio path
        loadAudioPath()

        // query all music audio
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        /*val cur = mContentResolver!!.query(
            uri, null,
            MediaStore.Audio.Media.IS_MUSIC + " = 1", null, null
        )*/
        val cur = mContentResolver!!.query(uri, null, MediaStore.Audio.Media.IS_MUSIC, null, MediaStore.Audio.Media.IS_MUSIC)
            ?: return mSongs
        if (!cur.moveToFirst()) {
            return mSongs
        }
        val artistColumn = cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val titleColumn = cur.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val albumColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumArtColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val durationColumn = cur.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val idColumn = cur.getColumnIndex(MediaStore.Audio.Media._ID)
        val dateColumn = cur.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        do {
            try {
                val song = Song(
                    cur.getLong(idColumn),
                    cur.getString(artistColumn),
                    cur.getString(titleColumn),
                    cur.getString(albumColumn),
                    cur.getLong(albumArtColumn),
                    cur.getLong(durationColumn),
                    mAudioPath[cur.getLong(idColumn)],
                    mAlbumMap[cur.getLong(albumArtColumn)],
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
        val cursor = getContentResolver()!!.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART),
            null,
            null,
            null
        )
        if (cursor!!.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART))
                if(!TextUtils.isEmpty(path)) {
                    mAlbumMap[id] = path
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private fun loadAudioPath() {
        val cursor = getContentResolver()!!.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.ALBUM_ID),
            null,
            null,
            null
        )
        if (cursor!!.moveToFirst()) {
            val mNeedCacheAlbum = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            /*val localAlbumDir = activity.getExternalFilesDir("localAlbum")
            val albumSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                Size(256, 256) else null
            if(mNeedCacheAlbum) {
                if (localAlbumDir != null) {
                    if(localAlbumDir.exists()) {
                        localAlbumDir.delete()
                    }
                    localAlbumDir.mkdir()
                }
            }*/
            do {
                //var album: Bitmap? = null;
                try {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    if(!TextUtils.isEmpty(path)) {
                        mAudioPath[id] = path
                        if (mNeedCacheAlbum) {
                            val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                            val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            mAlbumMap[albumId] = contentUri.toString()
                            /*album = getContentResolver()!!.loadThumbnail(contentUri, albumSize!!, null)
                            if(album != null) {
                                val byteArrayStream = ByteArrayOutputStream()
                                album.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayStream)
                                val file = File(localAlbumDir, "album_${albumId}.jpg")
                                file.writeBytes(byteArrayStream.toByteArray())
                                mAlbumMap[albumId] = file.path
                                Log.i("saveCacheAlbum:", "albumId:$albumId,len:${file.length()},uri:${file.path}")
                            }*/
                        }
                    }
                }
                catch (_: FileNotFoundException) {
                }
                catch (exception: Exception) {
                    exception.printStackTrace()
                }
                finally {
                    //album?.recycle()
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
            duration: Long,
            albumId: Long,
            dateAdded: Long
        ) {
            this.id = id
            this.artist = artist
            this.title = title
            this.album = album
            this.duration = duration
            this.albumId = albumId
            uri = genRI
            albumArt = getAlbum(albumId)
            this.dateAdded = dateAdded
        }

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
        }//                String title = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Media.TITLE));

        //                String album = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
//                String artist = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
//                long duration = mediaCursor.getLong(mediaCursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
        //Do something with the data
        //This is the id you are looking for
        private val genRI: String?
            get() {
                val mediaContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
                )
                val selection = MediaStore.Audio.Media._ID + "=?"
                val selectionArgs = arrayOf("" + id) //This is the id you are looking for
                val mediaCursor: Cursor? = this@MusicPlayerScanner.getContentResolver()?.query(
                    mediaContentUri,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                if(mediaCursor != null) {
                    if (mediaCursor.count >= 0) {
                        mediaCursor.moveToPosition(0)
                        //                String title = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                        //                String album = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                        //                String artist = mediaCursor.getString(mediaCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                        //                long duration = mediaCursor.getLong(mediaCursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
                        uri = mediaCursor.getString(mediaCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                        //Do something with the data
                    }
                    mediaCursor.close()
                }
                return uri
            }

        fun getUri(): String? {
            return uri
        }

        private fun getAlbum(albumId: Long): String {
            return mAlbumMap[albumId] ?: ""
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