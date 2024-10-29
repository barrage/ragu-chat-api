package net.barrage.llmao.core.models.common

import kotlinx.serialization.Serializable
import org.jooq.DatePart

@Serializable
enum class Period(val datePart: DatePart) {
  WEEK(DatePart.DAY),
  MONTH(DatePart.DAY),
  YEAR(DatePart.MONTH),
}
