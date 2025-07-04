/*
 * This file is generated by jOOQ.
 */
package net.barrage.llmao.core


import kotlin.collections.List

import net.barrage.llmao.core.tables.ApplicationSettings
import net.barrage.llmao.core.tables.MessageAttachments
import net.barrage.llmao.core.tables.MessageGroupEvaluations
import net.barrage.llmao.core.tables.MessageGroups
import net.barrage.llmao.core.tables.Messages
import net.barrage.llmao.core.tables.TokenUsage

import org.jooq.Catalog
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.SchemaImpl


/**
 * standard public schema
 */
@Suppress("warnings")
open class Public : SchemaImpl(DSL.name("public"), DefaultCatalog.DEFAULT_CATALOG, DSL.comment("standard public schema")) {
    companion object {

        /**
         * The reference instance of <code>public</code>
         */
        val PUBLIC: Public = Public()
    }

    /**
     * The table <code>public.application_settings</code>.
     */
    val APPLICATION_SETTINGS: ApplicationSettings get() = ApplicationSettings.APPLICATION_SETTINGS

    /**
     * The table <code>public.message_attachments</code>.
     */
    val MESSAGE_ATTACHMENTS: MessageAttachments get() = MessageAttachments.MESSAGE_ATTACHMENTS

    /**
     * The table <code>public.message_group_evaluations</code>.
     */
    val MESSAGE_GROUP_EVALUATIONS: MessageGroupEvaluations get() = MessageGroupEvaluations.MESSAGE_GROUP_EVALUATIONS

    /**
     * The table <code>public.message_groups</code>.
     */
    val MESSAGE_GROUPS: MessageGroups get() = MessageGroups.MESSAGE_GROUPS

    /**
     * The table <code>public.messages</code>.
     */
    val MESSAGES: Messages get() = Messages.MESSAGES

    /**
     * The table <code>public.token_usage</code>.
     */
    val TOKEN_USAGE: TokenUsage get() = TokenUsage.TOKEN_USAGE

    override fun getCatalog(): Catalog = DefaultCatalog.DEFAULT_CATALOG

    override fun getTables(): List<Table<*>> = listOf(
        ApplicationSettings.APPLICATION_SETTINGS,
        MessageAttachments.MESSAGE_ATTACHMENTS,
        MessageGroupEvaluations.MESSAGE_GROUP_EVALUATIONS,
        MessageGroups.MESSAGE_GROUPS,
        Messages.MESSAGES,
        TokenUsage.TOKEN_USAGE
    )
}
