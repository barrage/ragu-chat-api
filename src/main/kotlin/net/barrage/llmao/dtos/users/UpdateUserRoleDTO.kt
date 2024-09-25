package net.barrage.llmao.dtos.users

import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.Roles

@Serializable class UpdateUserRoleDTO(val role: Roles)
