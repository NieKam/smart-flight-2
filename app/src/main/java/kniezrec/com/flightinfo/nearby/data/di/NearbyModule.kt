package kniezrec.com.flightinfo.nearby.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.nearby.AndroidNearbyCityRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRepository

/** One [AndroidNearbyCityRepository] (a `@Singleton`) serves both the route and the nearby-city controllers. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NearbyModule {
    @Binds
    abstract fun bindNearbyCityRepository(repository: AndroidNearbyCityRepository): NearbyCityRepository
}
