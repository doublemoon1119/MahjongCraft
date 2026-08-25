package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 套用暗槓/加槓副露並從死牌區補摸嶺上牌。
 *
 * 從 [DeclareKanUseCase] 抽出的共用邏輯：無人可搶槓時直接套用、以及有人可搶槓但全員選擇放過
 * （[RespondToKanUseCase]）時「補做」原本被暫緩的套用，兩處需要完全相同的一段邏輯，抽成
 * 共用物件避免重複。依序 `recordAction(kanAction)` → 補摸嶺上牌 → `recordAction(GameAction.Draw)`，
 * 讓 [RiichiHandValueContextCalculator] 既有的嶺上開花偵測邏輯（依賴 `actionHistory` 最後兩筆是否恰為 `[Kan, Draw]`）能真正被觸發，
 * 並呼叫 `module.onMeldClaimed` 清除全場一發。
 */
internal object KanDeclarationApplier {
    /**
     * @property tableState 套用副露與嶺上摸牌後的新桌況；牌山恰好摸盡時（[rinshanTile] 為 null）
     *           與呼叫前的 [apply] 參數 `state` 內容相同，未套用任何變化。
     * @property rinshanTile 補摸到的嶺上牌；牌山恰好摸盡時為 null，呼叫端此時應視為 [GameError.WallExhausted]。
     */
    data class Result(val tableState: TableState, val rinshanTile: IdentifiedTile?)

    /**
     * @param state 目前的桌況（尚未套用本次槓牌）。
     * @param declarerId 宣告暗槓/加槓的玩家 Uuid。
     * @param kanAction 本次宣告的具體動作（僅支援 [GameAction.KanType.CLOSED_KAN]／
     *        [GameAction.KanType.ADDED_KAN]，[GameAction.KanType.OPEN_KAN] 走 [RespondToDiscardUseCase]）。
     * @param incomingTile 觸發槓牌的那張牌。
     * @param module 該對局採用的規則模組。
     */
    fun apply(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): Result {
        val declarer = state.players.first { it.id == declarerId }
        val handAfterMeld = when (kanAction.type) {
            GameAction.KanType.CLOSED_KAN -> {
                val kanTiles = kanAction.withTiles.mapNotNull { id ->
                    declarer.hand.standingTiles.find { it.id == id }
                } + incomingTile
                declarer.hand.call(MeldType.CLOSED_KAN, kanTiles, source = null, direction = RelativeDirection.Self)
            }

            GameAction.KanType.ADDED_KAN -> {
                val tileInterpretation = module.createTileInterpretationPolicy()
                val targetMeldIndex = declarer.hand.exposedMelds.indexOfFirst {
                    it.type == MeldType.PON &&
                        tileInterpretation.canonicalize(it.tiles.first().tile) ==
                        tileInterpretation.canonicalize(incomingTile.tile)
                }
                declarer.hand.upgradeToAddedKan(incomingTile, targetMeldIndex)
            }

            GameAction.KanType.OPEN_KAN -> error("Unreachable: OPEN_KAN never reaches KanDeclarationApplier")
        }

        val rinshanTile = drawRinshanTile(state) ?: return Result(state, null)

        val declarerAfterMeld = declarer.copy(hand = handAfterMeld).recordAction(kanAction)
        val declarerAfterDraw = declarerAfterMeld
            .copy(hand = declarerAfterMeld.hand.copy(lastDrawn = rinshanTile))
            .clearPassedTiles()
            .recordAction(GameAction.Draw)
        val playersAfterMeld = state.players.map { if (it.id == declarerId) declarerAfterDraw else it }
        val playersAfterMeldClaimed = module.onMeldClaimed(playersAfterMeld)

        return Result(state.copy(players = playersAfterMeldClaimed), rinshanTile)
    }

    /**
     * 從王牌區（[TableState.initialDeadWall]）摸嶺上牌，取代過去誤用 `TileWall.drawLast()`
     * 從活牌堆尾端摸牌的既有錯誤行為——真實麻將的嶺上牌本來就該來自死牌區，不是活牌堆。
     *
     * [RiichiDynamicState.getDoraIndicators] 的既有慣例是王牌區前 2 墩（索引 0～3，
     * `FIRST_INDICATOR_OFFSET = 4`）保留給嶺上摸牌、之後才是寶牌指示牌區——這裡沿用同一份保留區，
     * 依「目前桌上已經成立幾次槓」當索引依序取用（第一次槓用索引 0，第二次用索引 1，以此類推），
     * 跟寶牌指示牌區的索引範圍互不重疊，不會有同一張牌被兩邊重複使用的疑慮。索引超出保留區範圍
     * （理論上不會發生：第 5 次槓之前四槓散了流局應該已經先成立）或這個規則不支援王牌區
     * （`initialDeadWall` 為空）時回傳 `null`，呼叫端視同牌山摸盡。
     *
     * 王牌區本身固定不變、不需要每次槓後另外從活牌堆補牌維持張數——理由同
     * [RiichiDynamicState.getDoraIndicators] KDoc「王牌集合從開局到終局都完全固定不變」的既有簡化，
     * 這裡只是把嶺上牌的實際來源改成跟那份既有簡化一致，不是新增一種王牌會變動的機制。
     *
     * `internal` 而非 `private`：[RespondToDiscardUseCase] 的明槓（[GameAction.KanType.OPEN_KAN]）
     * 分支需要完全相同的邏輯，避免重複實作一次。
     */
    internal fun drawRinshanTile(state: TableState): IdentifiedTile? {
        val kanCountSoFar = state.players.sumOf { player ->
            player.hand.exposedMelds.count {
                it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN
            }
        }
        // 保守起見即使呼叫端沒擋住（RiichiLegalActionValidator 已經在合法動作層擋下第 5 次槓），這裡
        // 也不讓索引跑出保留區、誤把寶牌指示牌區的牌當成嶺上牌摸走。
        if (kanCountSoFar >= RINSHAN_RESERVE_SIZE) return null
        return state.initialDeadWall.getOrNull(kanCountSoFar)
    }

    /** 王牌區前段保留給嶺上摸牌的張數，見 [drawRinshanTile] KDoc。 */
    private const val RINSHAN_RESERVE_SIZE = 4
}
