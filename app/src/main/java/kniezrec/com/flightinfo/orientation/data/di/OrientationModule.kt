package kniezrec.com.flightinfo.orientation.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.orientation.data.AndroidDisplayRotationProvider
import kniezrec.com.flightinfo.orientation.data.AndroidOrientationDataSource
import kniezrec.com.flightinfo.orientation.data.DisplayRotationProvider
import kniezrec.com.flightinfo.orientation.data.OrientationDataSource

/** The shared orientation sensor behind the Course and Horizon ViewModels. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrientationModule {
    @Binds
    abstract fun bindOrientationDataSource(dataSource: AndroidOrientationDataSource): OrientationDataSource

    @Binds
    abstract fun bindDisplayRotationProvider(provider: AndroidDisplayRotationProvider): DisplayRotationProvider
}
