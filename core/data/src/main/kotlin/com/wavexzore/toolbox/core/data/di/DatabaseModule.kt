package com.wavexzore.toolbox.core.data.di

import android.content.Context
import androidx.room.Room
import com.wavexzore.toolbox.core.data.rules.ToolboxDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): ToolboxDatabase =
        Room.databaseBuilder(context, ToolboxDatabase::class.java, ToolboxDatabase.NAME).build()
}
