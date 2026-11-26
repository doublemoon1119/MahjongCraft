package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/** 將領域擲骰結果轉成保存資料。 */
fun OpeningDiceRoll.toPersistenceDto(): OpeningDiceRollPersistenceDto = OpeningDiceRollPersistenceDto(
    rollIndex = rollIndex,
    playerId = playerId.toString(),
    dice = dice.values,
)

/** 將保存資料還原成領域擲骰結果。 */
fun OpeningDiceRollPersistenceDto.toDomain(): OpeningDiceRoll = OpeningDiceRoll(
    rollIndex = rollIndex,
    playerId = Uuid.parse(playerId),
    dice = DiceRollResult.of(dice),
)
