package com.linroid.viewit.ioc.module

import android.content.Context
import com.linroid.viewit.data.ImageRepoManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * @author linroid <linroid@gmail.com>
 * @since 08/01/2017
 */
@Module
class RepoModule {
    @Singleton @Provides
    fun provideImageRepoManager(context: Context): ImageRepoManager {
        return ImageRepoManager(context)
    }
}
