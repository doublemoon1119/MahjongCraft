package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializationResult
import kotlin.uuid.Uuid

/**
 * 本次開局已計算的正式排位與桌況；提交時必須仍是同一個房間。
 *
 * @property room 產生本次資料的完整房間。
 * @property initializationResult 只計算一次的開局桌況。
 * @property dealOrderHandTileIdsBySeatIndex 各正式席位的發牌順序。
 * @property matchSeed 產生本次開局並延續到整場對局的不公開種子。
 */
class PreparedGameStart internal constructor(
    val room: Room,
    val initializationResult: GameInitializationResult,
    val dealOrderHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
    val matchSeed: MatchSeed,
) {
    /** 本次規則已決定的正式成員順序，接入端不可另行重排。 */
    val playerIds: List<Uuid> = initializationResult.tableState.players.map { it.id }
}
