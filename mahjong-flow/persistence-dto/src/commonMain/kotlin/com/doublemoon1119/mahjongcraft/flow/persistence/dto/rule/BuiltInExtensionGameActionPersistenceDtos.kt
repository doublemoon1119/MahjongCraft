package com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlinx.serialization.Serializable

/** 日麻立直動作的 persistence payload。 */
@Serializable
data object RiichiGameActionPersistenceDto

/** 建立已登記內建立直動作的擴充動作 persistence registry。 */
fun buildExtensionGameActionPersistenceRegistry(): PersistenceDtoRegistry<ExtensionGameAction> = PersistenceDtoRegistry<ExtensionGameAction>().apply {
    registerRiichiGameActionPersistenceDto()
}

/** 登記日麻立直 extension action 的 persistence mapper。 */
fun PersistenceDtoRegistry<ExtensionGameAction>.registerRiichiGameActionPersistenceDto() {
    register(
        typeKey = BuiltInGameActionIds.RIICHI,
        domainClass = RiichiGameAction.Riichi::class,
        serializer = RiichiGameActionPersistenceDto.serializer(),
        toDto = { RiichiGameActionPersistenceDto },
        toDomain = { RiichiGameAction.Riichi },
    )
}
