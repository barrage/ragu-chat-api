package net.barrage.llmao.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.Role

@Serializable
data class CsvImportUsersResult(
  val successful: List<CsvImportedUser>,
  var failed: List<CsvImportError>,
)

@Serializable
data class CsvImportedUser(
  val email: String,
  val fullName: String,
  val role: Role,
  val skipped: Boolean,
)

@Serializable
data class CsvImportError(val line: Int, val type: CsvImportErrorType, val message: List<String>)

@Serializable
enum class CsvImportErrorType {
  @SerialName("missing_fields") MISSING_FIELDS,
  @SerialName("validation") VALIDATION,
}
