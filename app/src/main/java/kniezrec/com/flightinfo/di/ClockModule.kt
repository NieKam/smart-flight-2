package kniezrec.com.flightinfo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** Wall clock for controllers that take `() -> Instant`; `Clock.systemUTC()` is what `Instant.now()` uses. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
