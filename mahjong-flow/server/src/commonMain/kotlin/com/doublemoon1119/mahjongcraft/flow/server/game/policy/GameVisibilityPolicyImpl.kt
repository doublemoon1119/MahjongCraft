package com.doublemoon1119.mahjongcraft.flow.server.game.policy

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.mapTiles
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.ResolvedTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileReadContext
import com.doublemoon1119.mahjongcraft.logic.tile.component.defaultTileRegionComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileComponents
import kotlin.uuid.Uuid

/**
 * [GameVisibilityPolicy] 的預設實作。
 *
 * @property componentRegistry 以明確定義裁切玩家及對局組件。
 * @property moduleRegistry 取得規則模組，用來裁切玩家狀態、額外可見牌面與逐張手牌姿態。
 */
class GameVisibilityPolicyImpl(
    private val moduleRegistry: MahjongModuleRegistry,
    private val componentRegistry: ComponentRegistry,
) : GameVisibilityPolicy {
    override fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot {
        val playerIds = game.tableState.players.mapTo(linkedSetOf()) { it.id }
        val winnerIds = game.tableState.winnerPlayerIds()
        val visibleHandPlayerIds = visibleHandPlayerIds(game, observerId)
        val module = moduleRegistry.getModule(game.tableState.config)
        val snapshot = game.tableState.toSnapshot(visibleHandPlayerIds)
        val authoritativePlayers = game.tableState.players.associateBy { it.id }
        val canObserve = observerId in playerIds || game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED
        val reader = if (observerId in playerIds) ComponentReadContext.Player(observerId) else ComponentReadContext.Spectator(observerId)
        val tableViews = if (canObserve) {
            componentRegistry.project(
                context = ComponentProjectionContext(
                    ruleModuleId = module.id,
                    tableState = game.tableState,
                    ownerType = BuiltInComponentOwners.TABLE,
                    owner = game.tableState,
                    reader = reader,
                ),
                components = game.tableState.components,
            )
        } else {
            ComponentViewSet.EMPTY
        }
        val tiles = if (canObserve) resolveTiles(game.tableState) else emptyList()
        val byId = tiles.associateBy { it.area.tile.id }
        val tileReader = tileReadContext(game, observerId)
        fun projectTile(tile: IdentifiedTileSnapshot): IdentifiedTileSnapshot = tile.copy(
            face = byId[tile.id]?.faceViewFor(tileReader, module.id, game.tableState, componentRegistry),
        )
        val projected = snapshot.mapTiles(::projectTile)
        return projected.copy(
            componentViews = tableViews,
            publicTileIds = tiles.filter { it.canRead(TileReadContext.Public(winnerIds)) }.mapTo(mutableSetOf()) { it.area.tile.id },
            tilePoseStates = tiles.filter {
                it.pose != defaultTileRegionComponents(game.tableState, it.area)[BuiltInTileComponents.POSE]
            }.map { TilePoseState(it.area.tile.id, it.pose) }.sortedBy { it.tileId.toString() },
            players = projected.players.map { playerSnapshot ->
                val player = checkNotNull(authoritativePlayers[playerSnapshot.id])
                val playerViews = if (canObserve) {
                    componentRegistry.project(
                        context = ComponentProjectionContext(
                            ruleModuleId = module.id,
                            tableState = game.tableState,
                            ownerType = BuiltInComponentOwners.PLAYER,
                            owner = player,
                            reader = reader,
                        ),
                        components = player.components,
                    )
                } else {
                    ComponentViewSet.EMPTY
                }
                playerSnapshot.copy(
                    componentViews = playerViews,
                    hand = playerSnapshot.hand.copy(
                        melds = playerSnapshot.hand.melds.map { meldSnapshot ->
                            val meld = player.hand.melds.single { it.id == meldSnapshot.id }
                            val views = if (canObserve) {
                                componentRegistry.project(
                                    context = ComponentProjectionContext(
                                        ruleModuleId = module.id,
                                        tableState = game.tableState,
                                        ownerType = BuiltInComponentOwners.MELD,
                                        owner = meld,
                                        reader = reader,
                                    ),
                                    components = meld.components,
                                )
                            } else {
                                ComponentViewSet.EMPTY
                            }
                            meldSnapshot.copy(componentViews = views)
                        },
                    ),
                )
            },
        )
    }

    override fun tileFacesFor(
        game: Game,
        observerId: Uuid,
        tileIds: Collection<Uuid>,
    ): Map<Uuid, TileFaceView> {
        val canObserve = game.tableState.players.any { it.id == observerId } || game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED
        if (!canObserve) return emptyMap()
        return readableFaces(game.tableState, tileReadContext(game, observerId), tileIds)
    }

    override fun publicTileFaces(tableState: TableState, tileIds: Collection<Uuid>): Map<Uuid, TileFaceView> = readableFaces(tableState, TileReadContext.Public(tableState.winnerPlayerIds()), tileIds)

    /**
     * 以同一份解算結果裁切指定牌張；讀取者看不到的牌不列入結果。
     *
     * @param tableState 目前權威桌況。
     * @param reader 已決定的讀取情境。
     * @param tileIds 要讀取的牌張。
     * @return 讀取者可知的牌面與特徵。
     */
    private fun readableFaces(
        tableState: TableState,
        reader: TileReadContext,
        tileIds: Collection<Uuid>,
    ): Map<Uuid, TileFaceView> {
        val wanted = tileIds.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val moduleId = moduleRegistry.getModule(tableState.config).id
        return resolveTiles(tableState)
            .filter { it.area.tile.id in wanted }
            .mapNotNull { resolved -> resolved.faceViewFor(reader, moduleId, tableState, componentRegistry)?.let { resolved.area.tile.id to it } }
            .toMap()
    }

    /**
     * 解算所有牌區的組件；規則描述無效時依規則契約錯誤處理。
     *
     * @param tableState 目前權威桌況。
     * @return 合併後的逐張組件。
     */
    private fun resolveTiles(tableState: TableState): List<ResolvedTileComponents> = try {
        moduleRegistry.getModule(tableState.config).resolveTileComponents(tableState, componentRegistry)
    } catch (invalid: IllegalArgumentException) {
        violation(invalid.message ?: "Invalid tile components")
    }

    /**
     * 讀取者可看到哪些玩家的手牌：參與者看自己與和牌者，旁觀者依觀戰設定。
     *
     * @param game 權威遊戲。
     * @param observerId 讀取者識別碼。
     * @return 手牌可讀的玩家識別碼。
     */
    private fun visibleHandPlayerIds(game: Game, observerId: Uuid): Set<Uuid> {
        val playerIds = game.tableState.players.mapTo(linkedSetOf()) { it.id }
        val winnerIds = game.tableState.winnerPlayerIds()
        if (observerId in playerIds) return winnerIds + observerId
        val canRevealSpectatorHands =
            game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED &&
                game.flowConfig.spectatorHandVisibility == SpectatorHandVisibility.REVEALED
        return when {
            canRevealSpectatorHands -> playerIds
            game.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED -> winnerIds
            else -> emptySet()
        }
    }

    /**
     * 建立參與者或旁觀者的牌張讀取情境。
     *
     * @param game 權威遊戲。
     * @param observerId 讀取者識別碼。
     * @return 對應的讀取情境。
     */
    private fun tileReadContext(game: Game, observerId: Uuid): TileReadContext = if (game.tableState.players.any { it.id == observerId }) {
        TileReadContext.Player(observerId, game.tableState.winnerPlayerIds())
    } else {
        TileReadContext.Spectator(observerId, visibleHandPlayerIds(game, observerId))
    }

    /** 以規則契約錯誤中止投影，避免輸出錯誤的公開範圍。 */
    private fun violation(diagnostic: String): Nothing = throw RuleContractViolationException(
        reasonId = GameAbortReasonIds.RULE_EXECUTION_FAILED,
        diagnostic = diagnostic,
    )
}
