package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.uuid.Uuid

/**
 * 用於單元測試的 [TableState] 工廠。
 */
object FakeTableStateFactory {

    /**
     * 建立一個測試用的 [TableState]。
     *
     * [tileWall] 預設不是空牌山——一場真實對局開局時牌山本來就有大量剩餘，空牌山只是牌山摸盡這個
     * 特定情境的測試才需要的邊界值，不該是每個測試都要背的預設狀態（[RiichiLegalActionValidator]
     * 的河底/海底鳴牌限制、立直摸牌機會限制都依賴 `remainingCount`，空牌山預設會讓幾乎所有跟鳴牌／
     * 立直相關的既有測試意外被擋下，這是實際遇到的問題）；需要測試牌山摸盡情境的測試應自行覆寫成
     * `TileWall(emptyList())`。
     */
    fun create(
        id: Uuid = Uuid.random(),
        players: List<MahjongPlayer> = emptyList(),
        config: MahjongRuleConfig = FakeMahjongRuleConfig(),
        tileWall: TileWall = TileWall(List(DEFAULT_WALL_SIZE) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)) }),
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
        currentPlayerIndex: Int = 0,
        dynamicRuleState: DynamicRuleState? = null,
        pendingReaction: PendingReaction? = null,
        pendingKanReaction: PendingKanReaction? = null,
        initialDeadWall: List<IdentifiedTile> = emptyList(),
        finishedPlayerIds: Set<Uuid> = emptySet(),
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
        pendingKanReaction = pendingKanReaction,
        initialDeadWall = initialDeadWall,
        finishedPlayerIds = finishedPlayerIds,
    )

    /** [tileWall] 預設張數——遠高於一般測試的玩家人數，避免不小心撞到摸牌相關的邊界判斷。 */
    private const val DEFAULT_WALL_SIZE = 70
}
