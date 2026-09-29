package kniezrec.com.flightinfo.nearby.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.nearby.AndroidNearbyCityRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRepository
import kniezrec.com.flightinfo.nearby.data.AndroidCityDataSource
import kniezrec.com.flightinfo.nearby.data.CityDataSource

/**
 * The platform behind the `@Singleton` `CityRepository` (Nearby city card), and the legacy
 * [NearbyCityRepository] still used by the route controller until TASK-012.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NearbyModule {
    @Binds
    abstract fun bindCityDataSource(dataSource: AndroidCityDataSource): CityDataSource

    @Binds
    abstract fun bindNearbyCityRepository(repository: AndroidNearbyCityRepository): NearbyCityRepository
}
