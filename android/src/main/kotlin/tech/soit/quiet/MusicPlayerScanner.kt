package tech.soit.quiet

import android.content.ContentResolver
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import java.util.Random

class MusicPlayerScanner(cr: ContentResolver?) {
    private var mContentResolver: ContentResolver? = cr
    private val mSongs: MutableList<Song> = ArrayList()
    private val mRandom = Random()
    private val mAlbumMap = HashMap<Long, String>()
    private val mAudioPath = HashMap<Long, String>()

    fun prepare() {
        // load all album art
        loadAlbumArt()

        // query all audio path
        loadAudioPath()

        // query all music audio
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cur = mContentResolver!!.query(
            uri, null,
            MediaStore.Audio.Media.IS_MUSIC + " = 1", null, null
        ) ?: return
        if (!cur.moveToFirst()) {
            return
        }
        val artistColumn = cur.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val titleColumn = cur.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val albumColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumArtColumn = cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val durationColumn = cur.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val idColumn = cur.getColumnIndex(MediaStore.Audio.Media._ID)
        val trackIdColumn = cur.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val musicDirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
        do {
            val trackIdStr = cur.getString(trackIdColumn)
            var trackId = 0
            if (!TextUtils.isEmpty(trackIdStr)) {
                trackId = trackIdStr.toInt()
            }
            val song = Song(
                cur.getLong(idColumn),
                cur.getString(artistColumn),
                cur.getString(titleColumn),
                cur.getString(albumColumn),
                cur.getLong(durationColumn),
                mAudioPath[cur.getLong(idColumn)],
                mAlbumMap[cur.getLong(albumArtColumn)],
                trackId.toLong()
            )
            if (song.getUri()!!.startsWith(musicDirPath)) {
                mSongs.add(song)
            }
        } while (cur.moveToNext())
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
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA),
            null,
            null,
            null
        )
        if (cursor!!.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                if(!TextUtils.isEmpty(path)) {
                    mAudioPath[id] = path
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
        private var trackId: Long

        constructor(
            id: Long,
            artist: String,
            title: String,
            album: String,
            duration: Long,
            albumId: Long,
            trackId: Long
        ) {
            this.id = id
            this.artist = artist
            this.title = title
            this.album = album
            this.duration = duration
            this.albumId = albumId
            uri = genRI
            albumArt = getAlbum()
            this.trackId = trackId
        }

        constructor(
            id: Long,
            artist: String,
            title: String,
            album: String,
            duration: Long,
            uri: String?,
            albumArt: String?,
            trackId: Long
        ) {
            this.id = id
            this.artist = artist
            this.title = title
            this.album = album
            this.duration = duration
            this.uri = uri
            this.albumArt = albumArt
            this.trackId = trackId
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

        private fun getAlbum(): String {
            var path = ""
            //            try {
//                Uri genericArtUri = Uri.parse("content://media/external/audio/albumart");
//                Uri actualArtUri = ContentUris.withAppendedId(genericArtUri, albumId);
//                return actualArtUri.toString();
//            } catch(Exception e) {
//                return null;
//            }
            val cursor: Cursor? = getContentResolver()?.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf<String>(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART),
                MediaStore.Audio.Albums._ID + "=?",
                arrayOf<String>(albumId.toString()),
                null
            )
            if(cursor != null) {
                if (cursor.moveToFirst()) {
                    path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART))
                    // do whatever you need to do
                }
                cursor.close()
            }
            return path
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
            songsMap["trackId"] = trackId      
            return songsMap
        }
    }
}