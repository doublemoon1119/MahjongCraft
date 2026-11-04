package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPendingFlowerFollowUp
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 尚待處理花牌的持久化資料。 */
@Serializable
data class TaiwanPendingFlowerFollowUpPersistenceDto(
    val playerId: String,
    val tileId: String,
)

/** 將尚待處理的花牌轉成持久化資料。 */
fun TaiwanPendingFlowerFollowUp.toPersistenceDto(): TaiwanPendingFlowerFollowUpPersistenceDto = TaiwanPendingFlowerFollowUpPersistenceDto(playerId.toString(), tileId.toString())

/** 將持久化資料還原成尚待處理的花牌。 */
fun TaiwanPendingFlowerFollowUpPersistenceDto.toDomain(): TaiwanPendingFlowerFollowUp = TaiwanPendingFlowerFollowUp(Uuid.parse(playerId), Uuid.parse(tileId))
