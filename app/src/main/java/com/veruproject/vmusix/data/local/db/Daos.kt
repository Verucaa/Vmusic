package com.veruproject.vmusix.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LikedDao {

    @Query("SELECT * FROM liked_songs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<LikedSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE id = :id)")
    fun observeIsLiked(id: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM liked_songs")
    suspend fun clear()
}

@Dao
interface HistoryDao {

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: PlayHistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}

@Dao
interface PlaylistDao {

    data class PlaylistWithMeta(
        val id: Long,
        val name: String,
        val createdAt: Long,
        val cnt: Int,
        val thumb: String?
    )

    data class PlaylistSongCount(
        val playlistId: Long,
        val cnt: Int
    )

    @Query(
        """
        SELECT
            p.id,
            p.name,
            p.createdAt,
            (
                SELECT COUNT(*)
                FROM playlist_songs s
                WHERE s.playlistId = p.id
            ) AS cnt,
            (
                SELECT thumbnail
                FROM playlist_songs s
                WHERE s.playlistId = p.id
                ORDER BY position
                LIMIT 1
            ) AS thumb
        FROM playlists p
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylistsWithMeta(): Flow<List<PlaylistWithMeta>>

    @Query(
        """
        SELECT *
        FROM playlists
        ORDER BY createdAt DESC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT
            playlistId,
            COUNT(*) AS cnt
        FROM playlist_songs
        GROUP BY playlistId
        """
    )
    fun observeSongCounts(): Flow<List<PlaylistSongCount>>

    @Query(
        """
        SELECT *
        FROM playlist_songs
        WHERE playlistId = :playlistId
        ORDER BY position ASC
        """
    )
    fun observeSongs(
        playlistId: Long
    ): Flow<List<PlaylistSongEntity>>

    @Query(
        """
        SELECT *
        FROM playlist_songs
        WHERE playlistId = :playlistId
        ORDER BY position ASC
        """
    )
    suspend fun getSongs(
        playlistId: Long
    ): List<PlaylistSongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(
        playlist: PlaylistEntity
    ): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(
        song: PlaylistSongEntity
    )

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(
        playlistId: Long
    )

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongs(
        playlistId: Long
    )

    @Query(
        """
        DELETE FROM playlist_songs
        WHERE playlistId = :playlistId
        AND songId = :songId
        """
    )
    suspend fun deleteSongRow(
        playlistId: Long,
        songId: String
    )

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM playlist_songs
            WHERE playlistId = :playlistId
            AND songId = :songId
        )
        """
    )
    suspend fun containsSong(
        playlistId: Long,
        songId: String
    ): Boolean
}

@Dao
interface DownloadDao {

    @Query(
        """
        SELECT *
        FROM downloads
        ORDER BY createdAt DESC
        """
    )
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query(
        """
        SELECT *
        FROM downloads
        WHERE id = :id
        """
    )
    suspend fun get(
        id: String
    ): DownloadEntity?

    @Query(
        """
        SELECT *
        FROM downloads
        WHERE state = 'done'
        """
    )
    suspend fun getCompleted(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        entity: DownloadEntity
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(
        id: String
    )

    @Query("DELETE FROM downloads")
    suspend fun clear()
}
