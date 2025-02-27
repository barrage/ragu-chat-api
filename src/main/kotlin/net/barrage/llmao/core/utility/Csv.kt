package net.barrage.llmao.core.utility

import io.ktor.utils.io.*
import net.barrage.llmao.core.models.CreateUser
import net.barrage.llmao.core.models.CsvImportError
import net.barrage.llmao.core.models.CsvImportErrorType
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.core.validateEmail

suspend fun parseUsers(csv: ByteReadChannel): Pair<List<CreateUser>, List<CsvImportError>> {
  val header = csv.readUTF8Line() ?: throw AppError.api(ErrorReason.InvalidParameter, "Empty CSV")

  if (header.lowercase() != "FullName,FirstName,LastName,Email,Role".lowercase()) {
    throw AppError.api(ErrorReason.InvalidParameter, "Invalid CSV header")
  }

  val users = mutableListOf<CreateUser>()
  val failed = mutableListOf<CsvImportError>()

  var index = 2
  var line = csv.readUTF8Line()
  while (line != null) {
    try {
      if (line.isBlank()) {
        continue
      }

      // Split the line into 5 fields, if less than 5 fields are returned, an exception is thrown
      var (fullName, firstName, lastName, email, roleString) =
        line.split(",", limit = 5).map { it.trim() }

      val errors = mutableListOf<String>()

      if (fullName.isBlank()) {
        errors.add("FullName")
      }

      if (!validateEmail(email)) {
        errors.add("Email")
      }

      val role =
        try {
          Role.valueOf(roleString.uppercase())
        } catch (_: IllegalArgumentException) {
          errors.add("Role")
          null
        }

      if (errors.isNotEmpty()) {
        failed.add(CsvImportError(index, CsvImportErrorType.VALIDATION, errors))
        continue
      }

      users.add(CreateUser(email, fullName, firstName, lastName, role!!))
    } catch (_: IndexOutOfBoundsException) {
      // Missing fields, when splitting the line less than 5 fields are returned
      failed.add(CsvImportError(index, CsvImportErrorType.MISSING_FIELDS, listOf("Missing fields")))
    } finally {
      index++
      line = csv.readUTF8Line()
    }
  }

  return Pair(users, failed)
}
