package net.barrage.llmao.app.administration.settings

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.administration.settings.ApplicationSetting
import net.barrage.llmao.core.administration.settings.SettingKey
import net.barrage.llmao.core.administration.settings.SettingsRepository
import net.barrage.llmao.core.administration.settings.SettingsUpdate
import net.barrage.llmao.core.administration.settings.toModel
import net.barrage.llmao.tables.references.APPLICATION_SETTINGS
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded

class SettingsRepositoryPostgres(private val dslContext: DSLContext) : SettingsRepository {
  private val log = KtorSimpleLogger("n.b.l.c.settings.SettingsRepository")

  override suspend fun getValue(key: SettingKey): String? {
    return dslContext
      .select(APPLICATION_SETTINGS.VALUE)
      .from(APPLICATION_SETTINGS)
      .where(APPLICATION_SETTINGS.NAME.eq(key.name))
      .awaitFirstOrNull()
      ?.into(APPLICATION_SETTINGS)
      ?.value
  }

  override suspend fun listAll(): List<ApplicationSetting> {
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

  override suspend fun list(keys: List<String>): List<ApplicationSetting> {
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

  override suspend fun update(update: SettingsUpdate) {
    update.removals?.forEach { key ->
      dslContext
        .deleteFrom(APPLICATION_SETTINGS)
        .where(APPLICATION_SETTINGS.NAME.eq(key.name))
        .awaitFirstOrNull()
    }

    update.updates?.let { updates ->
      dslContext
        .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
        .apply { updates.forEach { setting -> values(setting.key.name, setting.value) } }
        .onConflict(APPLICATION_SETTINGS.NAME)
        .doUpdate()
        .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
        .awaitFirstOrNull()
    }
  }
}
