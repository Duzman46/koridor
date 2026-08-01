package com.duzman46.gridbound.di

import com.duzman46.gridbound.data.DefaultGameRepository
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.ai.AIActionGenerator
import com.duzman46.gridbound.game.ai.EasyAI
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindGameRepository(implementation: DefaultGameRepository): GameRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default

    @Provides
    fun provideEasyAI(actionGenerator: AIActionGenerator, random: Random): EasyAI =
        EasyAI(actionGenerator, random)
}

