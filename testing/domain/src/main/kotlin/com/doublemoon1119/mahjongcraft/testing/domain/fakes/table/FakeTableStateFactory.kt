package com.doublemoon1119.mahjongcraft.testing.domain.fakes.table

import com.doublemoon1119.mahjongcraft.domain.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import java.util.*

/**
 * 用於單元測試的 [TableState] 工廠。
 */
object FakeTableStateFactory {

    /**
     * 建立一個測試用的 [TableState]。
     */
    fun create(
        id: UUID = UUID.randomUUID(),
        players: List<MahjongPlayer> = emptyList(),
        config: MahjongRuleConfig = FakeMahjongRuleConfig(),
        tileWall: TileWall = TileWall(emptyList()),
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
        currentPlayerIndex: Int = 0,
        dynamicRuleState: DynamicRuleState? = null
    ): TableState {
        return TableState(
            id = id,
            players = players,
            config = config,
            tileWall = tileWall,
            prevalentWind = prevalentWind,
            roundNumber = roundNumber,
            comboCount = comboCount,
            currentPlayerIndex = currentPlayerIndex,
            dynamicRuleState = dynamicRuleState
        )
    }
}