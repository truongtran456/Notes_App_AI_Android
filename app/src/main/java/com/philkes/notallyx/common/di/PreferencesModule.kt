package com.philkes.notallyx.common.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.philkes.notallyx.common.datasource.AppSharePrefs
import com.philkes.notallyx.common.datasource.AppSharePrefsImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(AppSharePrefsImpl.Keys.SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideAppSharePrefs(
        sharedPreferences: SharedPreferences,
        gson: Gson,
    ): AppSharePrefs = AppSharePrefsImpl(sharedPreferences, gson)
}

