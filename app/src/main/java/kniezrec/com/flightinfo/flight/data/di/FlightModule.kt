package kniezrec.com.flightinfo.flight.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.flight.data.AndroidPressureDataSource
import kniezrec.com.flightinfo.flight.data.PressureDataSource

/** The barometer behind `FlightParametersViewModel`. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class FlightModule {
    @Binds
    abstract fun bindPressureDataSource(dataSource: AndroidPressureDataSource): PressureDataSource
}
