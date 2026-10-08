package com.veruproject.vmusix.core.di

import android.content.Context
import androidx.room.Room
import com.veruproject.vmusix.data.local.db.DownloadDao
import com.veruproject.vmusix.data.local.db.HistoryDao
import com.veruproject.vmusix.data.local.db.LikedDao
import com.veruproject.vmusix.data.local.db.PlaylistDao
import com.veruproject.vmusix.data.local.db.VmusixDatabase
import com.veruproject.vmusix.data.remote.youtube.InnerTube
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttp(): okhttp3.OkHttpClient = InnerTube.newClient()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VmusixDatabase =
        Room.databaseBuilder(context, VmusixDatabase::class.java, "vmusix.db").build()

    @Provides fun provideLikedDao(db: VmusixDatabase): LikedDao = db.likedDao()
    @Provides fun provideHistoryDao(db: VmusixDatabase): HistoryDao = db.historyDao()
    @Provides fun providePlaylistDao(db: VmusixDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideDownloadDao(db: VmusixDatabase): DownloadDao = db.downloadDao()
}
