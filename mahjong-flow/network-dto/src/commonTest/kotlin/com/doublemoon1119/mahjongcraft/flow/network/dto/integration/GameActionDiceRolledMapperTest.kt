package com.doublemoon1119.mahjongcraft.flow.network.dto.integration

import com.doublemoon1119.mahjongcraft.flow.network.dto.command.GameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證擲骰動作 DTO 保留輪次、實際玩家與逐顆點數。 */
class GameActionDiceRolledMapperTest {
    @Test
    fun `dice rolled action keeps opening roll`() {
        val roll = OpeningDiceRoll(
            rollIndex = 2,
            playerId = Uuid.parse("00000000-0000-0000-0000-000000000001"),
            dice = DiceRollResult.of(listOf(2, 6, 1)),
        )
        val action = GameAction.DiceRolled(roll)
        val registries = DefaultNetworkDtoRegistries()

        val dto = action.toDto(registries)
        val restored = dto.toDomain(registries)

        assertEquals(GameActionDto.DiceRolled(2, roll.playerId.toString(), listOf(2, 6, 1)), dto)
        assertEquals(action, restored)
    }
}
