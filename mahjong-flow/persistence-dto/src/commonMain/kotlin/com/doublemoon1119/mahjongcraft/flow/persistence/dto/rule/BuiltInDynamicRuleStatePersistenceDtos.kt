package com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import kotlinx.serialization.Serializable

/** [RiichiDynamicState] 的完整 persistence DTO。 */
@Serializable
data class RiichiDynamicStatePersistenceDto(val riichiStickCount: Int)

/** 建立已註冊內建日麻動態牌桌狀態的 persistence registry。 */
fun buildDynamicRuleStatePersistenceRegistry(): PersistenceDtoRegistry<DynamicRuleState> = PersistenceDtoRegistry<DynamicRuleState>()
    .apply {
        register(
            typeKey = "builtin:riichi_dynamic_state",
            domainClass = RiichiDynamicState::class,
            serializer = RiichiDynamicStatePersistenceDto.serializer(),
            toDto = { RiichiDynamicStatePersistenceDto(it.riichiStickCount) },
            toDomain = { RiichiDynamicState(it.riichiStickCount) },
        )
    }
