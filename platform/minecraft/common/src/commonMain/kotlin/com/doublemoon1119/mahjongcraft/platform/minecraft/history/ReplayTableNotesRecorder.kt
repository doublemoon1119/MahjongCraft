package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayPresentationNotes
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.RoundConclusion
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileReadContext
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileComponents
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTileFaces
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.cornerWidthsBySeat
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ScatteredDiscardLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableDiscardArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistry
import kotlinx.serialization.json.JsonObject

/**
 * 牌譜保存時，依每份桌況寫下 [ReplayTableNotes]，算法與對局中擺牌、畫出本局資訊、玩家資訊與桌上物件時相同。
 *
 * @property moduleRegistry 依規則設定取得規則模組。
 * @property tileLayoutProfiles 規則的桌面牌位配置。
 * @property tablePropDescribers 規則的桌上物件描述。
 * @property roundInfoLines 規則的本局資訊內容。
 * @property componentRegistry 解析桌上物件公開牌面所用的牌張組件。
 * @property networkRegistries 寫出桌上物件牌面所用的網路資料登記。
 */
class ReplayTableNotesRecorder(
    private val moduleRegistry: MahjongModuleRegistry,
    private val tileLayoutProfiles: TableTileLayoutProfileRegistry,
    private val tablePropDescribers: TablePropDescriberRegistry,
    private val roundInfoLines: RoundInfoLineDisplayRegistry,
    private val componentRegistry: ComponentRegistry,
    private val networkRegistries: NetworkDtoRegistries,
) : ReplayPresentationNotes {
    override fun notesFor(state: TableState): JsonObject? = notes(state).encode(networkRegistries)

    /**
     * 算出一份桌況的擺放附註。
     *
     * @param state 交易完成後的權威桌況。
     * @return 擺放附註。
     */
    fun notes(state: TableState): ReplayTableNotes {
        val module = moduleRegistry.getModule(state.config)
        val current = state.physicalWallLayout?.placements?.values?.toSet().orEmpty()
        val reach = module.createPhysicalWallLayoutPolicy()?.roundReach(state).orEmpty()
        val scattered = tileLayoutProfiles.profileFor(module.id).discardArea == TableDiscardArea.SCATTERED
        val describer = tablePropDescribers.find(module.id)
        return ReplayTableNotes(
            extraWallReach = reach - current,
            scatterPatternSeed = if (scattered) ScatteredDiscardLayout.roundPatternSeed(state.id, state.roundSerial) else null,
            cornerWidthBySeat = describer.cornerWidthsBySeat(state),
            roundInfo = roundInfoLines.buildLines(module.id, state),
            playerIndicators = state.players.withIndex().associate { (seat, player) -> seat to module.getPublicPlayerIndicators(state, player) }.filterValues { it.isNotEmpty() },
            tableProps = describer?.describe(state, publicTileFaces(state, module)).orEmpty(),
            drawHandReveals = drawHandReveals(state),
        )
    }

    /**
     * 流局結束時各座位的手牌是否公開，與對局中的流局演出相同。本局先胡牌退場的座位不列出。
     *
     * @param state 權威桌況。
     * @return 依座位的公開與否。不是流局結束的桌況為空。
     */
    private fun drawHandReveals(state: TableState): Map<Int, Boolean> {
        if ((state.turnPhase as? TurnPhase.RoundOver)?.conclusion !is RoundConclusion.ExhaustiveDraw) return emptyMap()
        val revealed = ExhaustiveDrawSettlementPresentationRequestFactory.revealedHandPlayerIds(state, state.revealedHandSettlements)
        return state.players.withIndex()
            .filter { (_, player) -> player.id !in state.finishedPlayerIds }
            .associate { (seat, player) -> seat to (player.id in revealed) }
    }

    /**
     * 桌上物件可以顯示的公開牌面，與對局中呈現桌上物件時相同。
     *
     * @param state 權威桌況。
     * @param module 這場對局的規則模組。
     * @return 依牌 UUID 查詢公開牌面。還沒公開的牌為 null。
     */
    private fun publicTileFaces(state: TableState, module: MahjongRuleModule<*>): TablePropTileFaces {
        val resolved by lazy { module.resolveTileComponents(state, componentRegistry).associateBy { it.area.tile.id } }
        return TablePropTileFaces { tileId -> resolved[tileId]?.faceViewFor(TileReadContext.Public(), module.id, state, componentRegistry) }
    }
}
