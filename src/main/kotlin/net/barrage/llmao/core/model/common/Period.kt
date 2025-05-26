package net.barrage.llmao.core.model.common

import kotlinx.serialization.Serializable
import net.barrage.llmao.types.KLocalDate
import org.jooq.DatePart

@Serializable
enum class Period(val datePart: DatePart) {
  WEEK(DatePart.DAY),
  MONTH(DatePart.DAY),
  YEAR(DatePart.MONTH);

  fun toDateBeforeNow(): KLocalDate {
    return when (this) {
      WEEK -> KLocalDate.now().minusDays(6)
      MONTH -> KLocalDate.now().minusMonths(1)
      YEAR -> KLocalDate.now().minusYears(1)
    }
  }
}
