package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPendingFlowerFollowUp
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 尚待處理花牌的持久化資料。
 *
 * @property playerId 公開花牌的玩家。
 * @property tileId 已移到公開花區的牌張識別碼。
 * @property replacementsDue 這位玩家已公開、尚未補的張數。
 */
@Serializable
data class TaiwanPendingFlowerFollowUpPersistenceDto(
    val playerId: String,
    val tileId: String,
    val replacementsDue: Int,
)

/** 將尚待處理的花牌轉成持久化資料。 */
fun TaiwanPendingFlowerFollowUp.toPersistenceDto(): TaiwanPendingFlowerFollowUpPersistenceDto = TaiwanPendingFlowerFollowUpPersistenceDto(playerId.toString(), tileId.toString(), replacementsDue)

/** 將持久化資料還原成尚待處理的花牌。 */
fun TaiwanPendingFlowerFollowUpPersistenceDto.toDomain(): TaiwanPendingFlowerFollowUp = TaiwanPendingFlowerFollowUp(Uuid.parse(playerId), Uuid.parse(tileId), replacementsDue)
