package com.skydoves.pokedex.compose.feature.home.di

import android.content.Context
import androidx.room.Room
import com.skydoves.pokedex.compose.feature.home.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSessionDatabase(@ApplicationContext context: Context): SessionDatabase {
        return Room.databaseBuilder(
            context,
            SessionDatabase::class.java,
            "session_database"
        ).build()
    }

    @Provides
    fun provideSessionDao(database: SessionDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideRecognitionMessageDao(database: SessionDatabase): RecognitionMessageDao {
        return database.recognitionMessageDao()
    }

    @Provides
    fun provideAiChatMessageDao(database: SessionDatabase): AiChatMessageDao {
        return database.aiChatMessageDao()
    }
} 