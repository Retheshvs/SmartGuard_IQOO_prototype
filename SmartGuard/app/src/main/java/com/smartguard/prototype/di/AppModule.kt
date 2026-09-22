package com.smartguard.prototype.di

import android.content.Context
import androidx.room.Room
import com.smartguard.prototype.profile.ProfileDao
import com.smartguard.prototype.profile.ProfileDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the application-level [CoroutineScope] (SupervisorJob + Default dispatcher). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProfileDatabase(@ApplicationContext context: Context): ProfileDatabase {
        return Room.databaseBuilder(
            context,
            ProfileDatabase::class.java,
            ProfileDatabase.DATABASE_NAME
        )
            // Destructive migration is acceptable for this prototype
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(database: ProfileDatabase): ProfileDao = database.profileDao()

    /**
     * Application-scoped coroutine scope using [SupervisorJob] so individual child
     * failures don't cancel the parent. Used for long-running work like
     * [com.smartguard.prototype.screentime.ScreenTimeManager]'s countdown.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
