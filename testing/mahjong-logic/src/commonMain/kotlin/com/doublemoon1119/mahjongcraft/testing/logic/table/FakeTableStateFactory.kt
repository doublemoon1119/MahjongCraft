package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiLegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import kotlin.uuid.Uuid

/**
 * 用於單元測試的 [TableState] 工廠。
 */
object FakeTableStateFactory {

    /**
     * 建立一個測試用的 [TableState]。
     *
     * [tileWall] 預設包含牌張，避免測試意外觸發依賴牌山剩餘張數的邊界判斷；這也符合
     * [RiichiLegalActionValidator] 對河底／海底鳴牌與立直摸牌機會的檢查。需要測試牌山摸盡時，請傳入
     * `TileWall(emptyList())`。
     *
     * @param id 桌況識別碼，預設隨機產生。
     * @param players 玩家清單；每位玩家的起家順位會依清單索引正規化，若自風重複則改依索引指定自風。
     * @param dealerPlayerId 莊家玩家識別碼，預設使用首位玩家的識別碼；清單為空時隨機產生。
     * @param config 此桌況使用的規則設定。
     * @param tileWall 牌山，預設包含 70 張測試牌；摸盡情境請明確傳入空牌山。
     * @param prevalentWind 場風，預設為東風。
     * @param roundNumber 局數，預設為 1。
     * @param comboCount 連莊／本場數，預設為 0。
     * @param currentPlayerIndex 目前行動玩家在清單中的索引，預設為 0。
     * @param pendingReaction 尚待處理的鳴牌反應；不為 null 時階段為等捨牌反應。
     * @param pendingRobbingReaction 尚待處理的搶槓反應；不為 null 時階段為等搶和反應。
     * @param turnPhase 明確指定的回合階段；為 null 時依反應參數決定，沒有反應時目前玩家有剛摸到的牌為等行動，否則為等摸牌。
     * @param reservedWallTiles 從牌山預留的牌，預設為空清單。
     * @param physicalWallLayout 實體牌牆配置，預設為 null。
     * @param finishedPlayerIds 已完成本局的玩家識別碼，預設為空集合。
     * @param components 初始桌況組件；日麻設定預設建立零供託與本局初始進度，其他設定為空集合。
     */
    fun create(
        id: Uuid = Uuid.random(),
        players: List<MahjongPlayer> = listOf(
            FakeMahjongPlayerFactory.create(Wind.EAST),
            FakeMahjongPlayerFactory.create(Wind.SOUTH),
        ),
        dealerPlayerId: Uuid = players.firstOrNull()?.id ?: Uuid.random(),
        config: MahjongRuleConfig = FakeMahjongRuleConfig(),
        tileWall: TileWall = TileWall(List(DEFAULT_WALL_SIZE) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)) }),
        prevalentWind: Wind = Wind.EAST,
        roundNumber: Int = 1,
        comboCount: Int = 0,
        currentPlayerIndex: Int = 0,
        pendingReaction: PendingReaction? = null,
        pendingRobbingReaction: PendingRobbingReaction? = null,
        turnPhase: TurnPhase? = null,
        reservedWallTiles: List<IdentifiedTile> = emptyList(),
        physicalWallLayout: TileWallPhysicalLayout? = null,
        finishedPlayerIds: Set<Uuid> = emptySet(),
        components: ComponentSet = if (config is RiichiFamilyRuleConfig) riichiTableComponents() else ComponentSet.EMPTY,
    ): TableState {
        val hasDuplicateWinds = players.map { it.seatWind }.distinct().size != players.size
        val normalizedPlayers = players.mapIndexed { index, player ->
            player.copy(
                initialSeatIndex = index,
                seatWind = if (hasDuplicateWinds) Wind.entries[index] else player.seatWind,
            )
        }
        return TableState(
            id = id,
            players = normalizedPlayers,
            config = config,
            tileWall = tileWall,
            dealerPlayerId = dealerPlayerId,
            prevalentWind = prevalentWind,
            roundNumber = roundNumber,
            comboCount = comboCount,
            currentPlayerIndex = currentPlayerIndex,
            turnPhase = turnPhase ?: when {
                pendingReaction != null -> TurnPhase.AwaitingDiscardReaction(pendingReaction)
                pendingRobbingReaction != null -> TurnPhase.AwaitingRobbingReaction(pendingRobbingReaction)
                normalizedPlayers.getOrNull(currentPlayerIndex)?.hand?.lastDrawn != null -> TurnPhase.AwaitingAction
                else -> TurnPhase.AwaitingDraw
            },
            reservedWallTiles = reservedWallTiles,
            physicalWallLayout = physicalWallLayout,
            finishedPlayerIds = finishedPlayerIds,
            components = components,
        )
    }

    /** [TileWall] 預設牌張數，供一般測試避免意外觸發牌山摸盡判斷。 */
    private const val DEFAULT_WALL_SIZE = 70
}
