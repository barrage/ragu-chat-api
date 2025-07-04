package net.barrage.llmao.core.repository

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.settings.ApplicationSetting
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.core.settings.toModel
import net.barrage.llmao.core.tables.references.APPLICATION_SETTINGS
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded

class SettingsRepository(private val dslContext: DSLContext) {
  private val log = KtorSimpleLogger("n.b.l.c.settings.SettingsRepository")

  suspend fun getValue(key: String): String? {
    return dslContext
      .select(APPLICATION_SETTINGS.VALUE)
      .from(APPLICATION_SETTINGS)
      .where(APPLICATION_SETTINGS.NAME.eq(key))
      .awaitFirstOrNull()
      ?.into(APPLICATION_SETTINGS)
      ?.value
  }

  suspend fun listAll(): List<ApplicationSetting> {
    return dslContext
      .select(
        APPLICATION_SETTINGS.NAME,
        APPLICATION_SETTINGS.VALUE,
        APPLICATION_SETTINGS.CREATED_AT,
        APPLICATION_SETTINGS.UPDATED_AT,
      )
      .from(APPLICATION_SETTINGS)
      .asFlow()
      .map {
        try {
          it.into(APPLICATION_SETTINGS).toModel()
        } catch (e: AppError) {
          log.warn("Failed to parse setting", e)
          null
        }
      }
      .toList()
      .filterNotNull()
  }

  suspend fun list(keys: List<String>): List<ApplicationSetting> {
    return dslContext
      .select(
        APPLICATION_SETTINGS.NAME,
        APPLICATION_SETTINGS.VALUE,
        APPLICATION_SETTINGS.CREATED_AT,
        APPLICATION_SETTINGS.UPDATED_AT,
      )
      .from(APPLICATION_SETTINGS)
      .where(APPLICATION_SETTINGS.NAME.`in`(keys))
      .asFlow()
      .map {
        try {
          it.into(APPLICATION_SETTINGS).toModel()
        } catch (e: AppError) {
          log.warn("Failed to parse setting", e)
          null
        }
      }
      .toList()
      .filterNotNull()
  }

  suspend fun update(update: SettingsUpdate) {
    update.removals?.forEach { key ->
      dslContext
        .deleteFrom(APPLICATION_SETTINGS)
        .where(APPLICATION_SETTINGS.NAME.eq(key))
        .awaitFirstOrNull()
    }

    update.updates?.let { updates ->
      dslContext
        .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
        .apply { updates.forEach { setting -> values(setting.key, setting.value) } }
        .onConflict(APPLICATION_SETTINGS.NAME)
        .doUpdate()
        .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
        .awaitFirstOrNull()
    }
  }
}
