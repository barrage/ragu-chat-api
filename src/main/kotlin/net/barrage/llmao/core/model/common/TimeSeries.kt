package net.barrage.llmao.core.model.common

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Holds arbitrary time series. This type should never be used directly. Instead, type aliases
 * should be made for whatever data is intended to be displayed as a time series.
 *
 * The identifier is used both as a key in the `series` and `legend` and represents the primary
 * entity ID for whatever the time series is for.
 *
 * @param V The type of the series data, i.e. what we are mapping to dates. See [TimeSeriesData].
 * @param L The type of the legend, i.e. what we are mapping to identifiers. This is useful for
 *   display purposes.
 * @param series The time series data whose keys are the entity identifiers.
 * @param legend The legend data whose keys are the entity identifiers.
 */
@Serializable
class TimeSeries<V, L>(val series: Map<String, TimeSeriesData<V>>, val legend: Map<String, L>) {
  companion object {
    /**
     * Return a list of dates for the given period, starting from the current date.
     *
     * When the period is [Period.WEEK] or [Period.MONTH], the dates returned will be the last 7 or
     * 30 days.
     *
     * When the period is [Period.YEAR], the dates returned will be the first day of the month for
     * the last 12 months.
     */
    internal fun datesForPeriod(period: Period): List<String> {
      return when (period) {
          Period.WEEK -> (0..6) // last 7 days
          Period.MONTH -> (0..29) // last 30 days
          Period.YEAR -> (0..11) // last 12 months
        }
        .map {
          if (period == Period.YEAR) {
            LocalDate.now().withDayOfMonth(1).minusMonths(it.toLong()).toString()
          } else {
            LocalDate.now().minusDays(it.toLong()).toString()
          }
        }
        .reversed()
    }

    /**
     * Construct a time series builder.
     *
     * @param period The period to use for the time series.
     * @param default The default value to use for a time series entry that is missing a date in the
     *   period.
     */
    fun <V, L> builder(period: Period, default: V) = TimeSeriesBuilder<V, L>(period, default)
  }
}

class TimeSeriesBuilder<V, L>(private val period: Period, private val default: V) {
  private val series = mutableMapOf<String, TimeSeriesData<V>>()
  private val legend = mutableMapOf<String, L>()

  /**
   * Add a data point to the time series under the given identifier.
   *
   * If a time series bucket under the identifier does not exist, it will be created.
   *
   * If the time key is `null`, the value will not be added, however the identifier bucket will be
   * created. This is useful for adding identifiers that have no data for consistency purposes when
   * displaying time series data.
   *
   * @param identifier The identifier for the time series.
   * @param timeKey The time key, i.e. the date, timestamp, et.
   * @param value The value to add to the time series.
   */
  fun addDataPoint(identifier: String, timeKey: String?, value: V): TimeSeriesBuilder<V, L> {
    val timeSeries = series.getOrPut(identifier) { TimeSeriesData(data = mutableMapOf()) }
    timeKey?.let { timeSeries.data[it] = value }
    return this
  }

  /**
   * Add a legend for the given identifier.
   *
   * Legends are used to give more explanation on what the identifiers are representing.
   */
  fun addLegend(identifier: String, label: L): TimeSeriesBuilder<V, L> {
    legend[identifier] = label
    return this
  }

  fun build(): TimeSeries<V, L> {
    val dates = TimeSeries.datesForPeriod(period)

    for (date in dates) {
      for (identifier in series.keys) {
        series[identifier]!!.data.getOrPut(date) { default }
      }
    }

    return TimeSeries(series, legend)
  }
}

/**
 * Wrapper around time series data.
 *
 * The time series data consists of arbitrary values. The data should always be indexed via strings,
 * converting any local date times or timestamps.
 *
 * @param V The type of the series data, i.e. what we are mapping to dates.
 */
@Serializable class TimeSeriesData<V>(val data: MutableMap<String, V>)
