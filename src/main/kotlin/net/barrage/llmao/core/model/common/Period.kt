package net.barrage.llmao.core.model.common

import kotlinx.serialization.Serializable
import org.jooq.DatePart

@Serializable
enum class Period(val datePart: DatePart) {
  WEEK(DatePart.DAY),
  MONTH(DatePart.DAY),
  YEAR(DatePart.MONTH),
}
