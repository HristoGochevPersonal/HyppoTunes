package com.example.hyppotunes.core.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.getStringOrNull
import com.example.hyppotunes.core.entity.Song
import com.example.hyppotunes.core.entity.SongInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


class LocalSongsRepository(val context: Context) :
    SQLiteOpenHelper(context, "songs_database.sqlite", null, 1) {

    companion object {
        val lock = Mutex()
    }


    private val songsTableName = "Songs"
    private val songInfosViewName = "SongInfos"

    override fun onCreate(db: SQLiteDatabase?) {
        val database = db ?: return
        database.execSQL(
            "create table if not exists $songsTableName\n" +
                    "(\n" +
                    "    Name       text not null,\n" +
                    "    Artist     text not null,\n" +
                    "    Image_path text,\n" +
                    "    File_path  text not null,\n" +
                    "    primary key (Name, Artist)\n" +
                    ");"
        )
        database.execSQL(
            "CREATE VIEW if not exists $songInfosViewName as\n" +
                    "select Artist || Name as Keyword, Name, Artist, Image_path\n" +
                    "from Songs;"
        )
        println("Created")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        val database = db ?: return
        database.execSQL("drop view if exists $songInfosViewName;")
        database.execSQL("drop table if exists $songsTableName;")
        onCreate(database)
    }

    suspend fun songExists(name: String, artist: String): Boolean {
        lock.withLock {
            val readableDb = try {
                readableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return false
            }
            val params = arrayOf(name, artist)
            val cursor = readableDb.rawQuery(
                "select exists(select 1 from $songsTableName where Name like ? and Artist like ? limit 1)",
                params
            )

            val output = cursor.moveToFirst()

            cursor.close()
            readableDb.close()

            return output
        }
    }

    suspend fun songInsert(song: Song): Long {
        lock.withLock {
            val writeableDb = try {
                writableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return -1
            }
            val input = ContentValues()
            input.put("Name", song.name)
            input.put("Artist", song.artist)
            input.put("Image_path", song.imagePath)
            input.put("File_path", song.filePath)
            val inserted = writeableDb.insert(
                songsTableName,
                null,
                input
            )
            writeableDb.close()
            return inserted
        }
    }

    suspend fun songUpdate(song: Song): Int {
        lock.withLock {
            val writeableDb = try {
                writableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return 0
            }

            val input = ContentValues()
            input.put("Name", song.name)
            input.put("Artist", song.artist)
            input.put("Image_path", song.imagePath)
            input.put("File_path", song.filePath)
            val whereClause = "Name like ? and Artist like ?"
            val params = arrayOf(song.name, song.artist)

            val updated = writeableDb.update(songsTableName, input, whereClause, params)

            writeableDb.close()
            return updated
        }
    }

    suspend fun songDelete(song: Song): Int {
        lock.withLock {
            val writeableDb = try {
                writableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return 0
            }
            val whereClause = "Name like ? and Artist like ?"
            val params = arrayOf(song.name, song.artist)
            val deleted = writeableDb.delete(songsTableName, whereClause, params)
            writeableDb.close()
            return deleted
        }
    }

    suspend fun songFetch(name: String, artist: String): Song? {
        lock.withLock {
            val readableDb = try {
                readableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return null
            }
            val params = arrayOf(name, artist)
            val cursor = readableDb.rawQuery(
                "Select * from $songsTableName where Name like ? and Artist like ? LIMIT 1",
                params
            )

            var output: Song? = null

            if (cursor.moveToFirst()) {
                val songName = cursor.getString(0)
                val songArtist = cursor.getString(1)
                val songImagePath = cursor.getStringOrNull(2)
                val songFilePath = cursor.getString(3)
                output = Song(songName, songArtist, songImagePath, songFilePath)
            }

            cursor.close()
            readableDb.close()

            return output
        }
    }

    suspend fun songInfosFetch(keyword: String): List<SongInfo>? {
        lock.withLock {
            val readableDb = try {
                readableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return null
            }

            val params = arrayOf("%$keyword%")
            val cursor =
                readableDb.rawQuery("Select * from $songInfosViewName where Keyword like ?", params)

            val output = ArrayList<SongInfo>(cursor.count)

            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(1)
                    val artist = cursor.getString(2)
                    val imagePath = cursor.getStringOrNull(3)
                    val songInfo = SongInfo(name, artist, imagePath)
                    output.add(songInfo)
                } while (cursor.moveToNext())
            }

            cursor.close()
            readableDb.close()
            return output
        }
    }

    suspend fun autoUpdate(): Boolean {

        for (filePath in context.fileList()) {
            val file = context.getFileStreamPath(filePath)
            if (file.extension != "mp3") {
                context.deleteFile(filePath)
                continue
            }
            val nameArtistFromFile = file.nameWithoutExtension.split(" - ")
            if (nameArtistFromFile.size != 2) {
                context.deleteFile(filePath)
                continue
            }

            val song = Song(nameArtistFromFile[1], nameArtistFromFile[0], null, filePath)

            val fetchedSong = songFetch(nameArtistFromFile[1], nameArtistFromFile[0])

            if (fetchedSong == null) {
                songInsert(song)
                continue
            }

            if (fetchedSong != song) {
                songUpdate(song)
            }
        }
        return true
    }

    suspend fun validate(): Boolean {
        lock.withLock {
            val readableDb = try {
                readableDatabase
            } catch (e: SQLiteException) {
                e.printStackTrace()
                return false
            }

            val cursor =
                readableDb.rawQuery("Select * from $songsTableName", null)

            if (cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(0)
                    val artist = cursor.getString(1)
                    val filePath = cursor.getString(3)
                    val song = Song(name, artist, null, filePath)
                    val file = context.getFileStreamPath(song.filePath)
                    val nameArtistFromFile = file.nameWithoutExtension.split(" - ")
                    if (!file.exists() || nameArtistFromFile[0] != artist || nameArtistFromFile[1] != name) {
                        songDelete(song)
                    }
                } while (cursor.moveToNext())
            }

            cursor.close()
            readableDb.close()

            return true
        }
    }
}