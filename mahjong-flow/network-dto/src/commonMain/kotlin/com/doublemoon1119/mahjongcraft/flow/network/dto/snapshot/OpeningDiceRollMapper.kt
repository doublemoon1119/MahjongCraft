package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/** 將領域擲骰結果轉成網路 DTO。 */
fun OpeningDiceRoll.toDto(): OpeningDiceRollDto = OpeningDiceRollDto(
    rollIndex = rollIndex,
    playerId = playerId.toString(),
    dice = dice.values,
)

/** 將網路 DTO 還原成領域擲骰結果。 */
fun OpeningDiceRollDto.toDomain(): OpeningDiceRoll = OpeningDiceRoll(
    rollIndex = rollIndex,
    playerId = Uuid.parse(playerId),
    dice = DiceRollResult.of(dice),
)
