package kniezrec.com.flightinfo.location.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.location.data.AndroidLocationDataSource
import kniezrec.com.flightinfo.location.data.LocationDataSource

/** The platform behind the `@Singleton` `LocationRepository`. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocationModule {
    @Binds
    abstract fun bindLocationDataSource(dataSource: AndroidLocationDataSource): LocationDataSource
}
