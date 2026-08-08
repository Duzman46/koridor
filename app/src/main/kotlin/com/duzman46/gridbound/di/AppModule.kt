package com.duzman46.gridbound.di

import com.duzman46.gridbound.auth.data.FirebaseAuthRepository
import com.duzman46.gridbound.auth.domain.AuthRepository
import com.duzman46.gridbound.data.DefaultGameRepository
import com.duzman46.gridbound.domain.repository.GameRepository
import com.duzman46.gridbound.game.ai.AIActionGenerator
import com.duzman46.gridbound.game.ai.EasyAI
import com.duzman46.gridbound.game.ai.SearchAI
import com.duzman46.gridbound.game.ai.search.SearchClock
import com.duzman46.gridbound.game.ai.search.SearchConfig
import com.duzman46.gridbound.game.engine.GameEngine
import com.duzman46.gridbound.game.pathfinding.AStarPathFinder
import com.duzman46.gridbound.leaderboard.data.RtdbLeaderboardRepository
import com.duzman46.gridbound.leaderboard.domain.LeaderboardRepository
import com.duzman46.gridbound.match.data.RtdbMatchRepository
import com.duzman46.gridbound.match.domain.MatchRepository
import com.duzman46.gridbound.online.data.FirebaseOnlineGameRepository
import com.duzman46.gridbound.online.domain.OnlineGameRepository
import com.duzman46.gridbound.profile.data.RtdbUserProfileRepository
import com.duzman46.gridbound.profile.domain.UserProfileRepository
import com.duzman46.gridbound.social.data.RtdbSocialRepository
import com.duzman46.gridbound.social.domain.SocialRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** A scope that lives as long as the process, for work that outlives any single screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The two tiers that share `SearchAI`. Dagger ignores Kotlin default parameter values and cannot
 * tell two `SearchConfig`s apart, so the distinction has to be carried by a qualifier.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExpertEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HardEngine

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindGameRepository(implementation: DefaultGameRepository): GameRepository

    @Binds
    @Singleton
    abstract fun bindOnlineGameRepository(implementation: FirebaseOnlineGameRepository): OnlineGameRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(implementation: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserProfileRepository(implementation: RtdbUserProfileRepository): UserProfileRepository

    @Binds
    @Singleton
    abstract fun bindMatchRepository(implementation: RtdbMatchRepository): MatchRepository

    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(implementation: RtdbLeaderboardRepository): LeaderboardRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(implementation: RtdbSocialRepository): SocialRepository
}

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    @Provides
    fun provideSearchClock(): SearchClock = SearchClock.SYSTEM

    /**
     * Deliberately unscoped. `AIEngineFactory` is constructor-injected into `GameViewModel`, so
     * this already yields one engine per screen and a fresh one per restart — and the searcher's
     * tables are allocated on its first call, so a player who never selects the tier never pays
     * for them.
     */
    @Provides
    @ExpertEngine
    fun provideExpertAI(
        actionGenerator: AIActionGenerator,
        gameEngine: GameEngine,
        pathFinder: AStarPathFinder,
        clock: SearchClock,
    ): SearchAI = SearchAI(actionGenerator, gameEngine, pathFinder, SearchConfig.EXPERT, clock)

    @Provides
    @HardEngine
    fun provideHardAI(
        actionGenerator: AIActionGenerator,
        gameEngine: GameEngine,
        pathFinder: AStarPathFinder,
        clock: SearchClock,
    ): SearchAI = SearchAI(actionGenerator, gameEngine, pathFinder, SearchConfig.HARD, clock)
}
