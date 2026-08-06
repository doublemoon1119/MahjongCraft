package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 用於單元測試的 [TableState] 工廠。
 */
object FakeTableStateFactory {

    /**
     * 建立一個測試用的 [TableState]。
     */
    fun create(
        id: Uuid = Uuid.random(),
        players: List<MahjongPlayer> = emptyList(),
        config: MahjongRuleConfig = FakeMahjongRuleConfig(),
        tileWall: TileWall = TileWall(emptyList()),
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
        currentPlayerIndex: Int = 0,
        dynamicRuleState: DynamicRuleState? = null,
        pendingReaction: PendingReaction? = null,
    ): TableState = TableState(
        id = id,
        players = players,
        config = config,
        tileWall = tileWall,
        prevalentWind = prevalentWind,
        roundNumber = roundNumber,
        comboCount = comboCount,
        currentPlayerIndex = currentPlayerIndex,
        dynamicRuleState = dynamicRuleState,
        pendingReaction = pendingReaction,
    )
}
