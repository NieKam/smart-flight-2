package kniezrec.com.flightinfo.nearby.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.nearby.data.AndroidCityDataSource
import kniezrec.com.flightinfo.nearby.data.CityDataSource

/** The platform behind the `@Singleton` `CityRepository` (Nearby city card and route). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NearbyModule {
    @Binds
    abstract fun bindCityDataSource(dataSource: AndroidCityDataSource): CityDataSource
}
