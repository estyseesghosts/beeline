package me.foxtails.palustris.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.foxtails.palustris.data.media.DraftThreadImagePreparer
import me.foxtails.palustris.domain.ThreadImagePreparer

/** Binds the draft-backed thread image preparer to its domain contract. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThreadModule {
    @Binds
    abstract fun bindThreadImagePreparer(impl: DraftThreadImagePreparer): ThreadImagePreparer
}
