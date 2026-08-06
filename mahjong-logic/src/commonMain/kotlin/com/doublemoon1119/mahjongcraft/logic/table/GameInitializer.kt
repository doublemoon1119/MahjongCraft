package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import kotlin.uuid.Uuid

/**
 * 建立一場新對局的初始桌況。
 *
 * 洗座位、建立牌山、輪流發初始手牌屬於規則無關的機械過程，交由此處統一實作，
 * 避免每個 [MahjongRuleModule] 各自重複實作一次同樣的流程。
 */
object GameInitializer {
    /**
     * 依據 [module] 建立一場新對局的初始 [TableState]。
     *
     * @param id 對局的唯一識別碼（沿用房間的 Uuid）。
     * @param playerIds 參與對局的玩家 Uuid 列表（尚未分配座位，內部會隨機排序）。
     * @param module 該對局採用的規則模組，提供牌山工廠、牌河實作與規則配置。
     * @return 已完成洗牌、發牌、分數初始化的新 [TableState]。
     * @throws IllegalArgumentException 當玩家人數不在該規則允許的範圍內時拋出。
     */
    fun initialize(id: Uuid, playerIds: List<Uuid>, module: MahjongRuleModule<*>): TableState {
        require(playerIds.size in module.config.minPlayers..module.config.maxPlayers) {
            "Player count ${playerIds.size} out of range for this rule config " +
                "(${module.config.minPlayers}..${module.config.maxPlayers})"
        }

        val seats = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).take(playerIds.size)
        val shuffledPlayerIds = playerIds.shuffled()

        var wall = module.createWallFactory().create()
        val players = shuffledPlayerIds.mapIndexed { index, playerId ->
            val (tiles, remainingWall) = wall.drawN(module.config.initialHandSize)
            wall = remainingWall
            MahjongPlayer(
                id = playerId,
                initialSeat = seats[index],
                hand = Hand(tiles = tiles),
                discardPile = module.createDiscardPile()
            )
        }

        return TableState(id = id, players = players, config = module.config, tileWall = wall).init()
    }

    /**
     * 連續從牌山最前方摸取 [count] 張牌。
     *
     * @return 摸到的牌列表（依摸牌順序排列）與摸牌後的新 [TileWall] 實例。
     * @throws IllegalStateException 當牌山在發牌途中就已摸盡時拋出。
     */
    private fun TileWall.drawN(count: Int): Pair<List<IdentifiedTile>, TileWall> {
        var wall = this
        val tiles = mutableListOf<IdentifiedTile>()
        repeat(count) {
            val (tile, newWall) = wall.draw()
            checkNotNull(tile) { "Tile wall ran out while dealing initial hands" }
            tiles += tile
            wall = newWall
        }
        return tiles to wall
    }
}
