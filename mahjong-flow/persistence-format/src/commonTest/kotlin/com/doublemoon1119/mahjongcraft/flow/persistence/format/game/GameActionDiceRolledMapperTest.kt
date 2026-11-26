package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證擲骰動作保存資料保留輪次、實際玩家與逐顆點數。 */
class GameActionDiceRolledMapperTest {
    @Test
    fun `dice rolled action keeps opening roll`() {
        val roll = OpeningDiceRoll(
            rollIndex = 1,
            playerId = Uuid.parse("00000000-0000-0000-0000-000000000002"),
            dice = DiceRollResult.of(listOf(3, 4)),
        )
        val action = GameAction.DiceRolled(roll)
        val registries = emptyPersistenceRegistries()

        val dto = action.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions)
        val restored = dto.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions)

        assertEquals(GameActionPersistenceDto.DiceRolled(1, roll.playerId.toString(), listOf(3, 4)), dto)
        assertEquals(action, restored)
    }
}
