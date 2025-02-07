package net.barrage.llmao.core.settings

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.tables.references.APPLICATION_SETTINGS
import org.jooq.DSLContext
import org.jooq.impl.DSL.excluded

class SettingsRepository(private val dslContext: DSLContext) {
  suspend fun list(keys: List<String>): List<Setting> {
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
      .map { it.into(APPLICATION_SETTINGS).toModel() }
      .toList()
  }

  suspend fun update(update: SettingsUpdate) {
    dslContext
      .insertInto(APPLICATION_SETTINGS, APPLICATION_SETTINGS.NAME, APPLICATION_SETTINGS.VALUE)
      .apply { update.updates.forEach { setting -> values(setting.key, setting.value) } }
      .onConflict(APPLICATION_SETTINGS.NAME)
      .doUpdate()
      .set(APPLICATION_SETTINGS.VALUE, excluded(APPLICATION_SETTINGS.VALUE))
      .awaitSingle()
  }
}
