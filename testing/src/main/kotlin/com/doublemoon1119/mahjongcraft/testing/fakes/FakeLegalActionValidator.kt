package com.doublemoon1119.mahjongcraft.testing.fakes

import com.doublemoon1119.mahjongcraft.domain.base.GameAction
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState

/**
 * [LegalActionValidator] 的測試用 Fake 實作。
 *
 * @param actionsToReturn 每次呼叫 `getLegalActions` 時要回傳的固定動作列表。預設為空列表。
 */
class FakeLegalActionValidator(
    private val actionsToReturn: List<GameAction> = emptyList()
) : LegalActionValidator {
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        sourceAction: GameAction,
        sourceDirection: RelativeDirection,
        incomingTile: IdentifiedTile?
    ): List<GameAction> {
        return actionsToReturn
    }
}
