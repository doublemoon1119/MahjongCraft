package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 供 [GamePresentationPublisher.publishPlayerTilesUpdated] 使用的單一副露資料。
 *
 * 只帶擺放副露需要的資訊（種類、牌 ID、鳴取的牌與來源方位），不帶 [Meld] 的牌面；牌面由各玩家的可見性快照決定。
 *
 * @property type 副露種類。
 * @property tileIds 組成這組副露的所有牌 ID，依規則牌序排列；加槓補上的第四張固定排在最後。鳴取的牌由 [calledTileId]
 * 指出，不靠位置辨識。
 * @property calledTileId 鳴取自他家的牌 ID；暗槓沒有鳴牌來源時為 `null`。
 * @property sourceDirection 鳴取來源的相對方位；暗槓沒有鳴牌來源時為 [RelativeDirection.Self]。
 * @property allTilesFaceDown 只在 [type] 為 [MeldType.CLOSED_KAN] 時有意義：規則是否連暗槓的牌都不公開；公開暗槓牌的規則
 * （例如日麻）為 `false`。其餘副露種類固定為 `false`。
 */
data class MeldPresentation(
    val type: MeldType,
    val tileIds: List<Uuid>,
    val calledTileId: Uuid?,
    val sourceDirection: RelativeDirection,
    val allTilesFaceDown: Boolean,
)

/**
 * 剝除 [Meld] 的牌面，只保留 [MeldPresentation] 需要的資訊。
 *
 * 牌 ID 依 [tileOrder] 排列，讓玩家讀得懂；[MeldType.ADDED_KAN] 補上的第四張（[Meld.tiles] 的最後一張）維持在最後。
 * 鳴取的牌由 [MeldPresentation.calledTileId] 指出，不靠位置辨識。
 *
 * @param revealsClosedKanTiles 該規則是否公開暗槓的牌（[MahjongRuleConfig.revealsClosedKanTiles]），
 * 只影響 [MeldPresentation.allTilesFaceDown]，非暗槓時傳入的值不影響結果。
 * @param tileOrder 該規則的牌序。
 */
fun Meld.toPresentation(revealsClosedKanTiles: Boolean, tileOrder: TileOrder): MeldPresentation = MeldPresentation(
    type = type,
    tileIds = if (type == MeldType.ADDED_KAN) {
        tiles.dropLast(1).sortedWith(compareBy(tileOrder) { it.tile }).map { it.id } + tiles.last().id
    } else {
        tiles.sortedWith(compareBy(tileOrder) { it.tile }).map { it.id }
    },
    calledTileId = sourceTile?.id,
    sourceDirection = sourceDirection,
    allTilesFaceDown = type == MeldType.CLOSED_KAN && !revealsClosedKanTiles,
)

/**
 * 把對局中發生的事通知平台呈現層的出口：flow 決定通知的時機與語意內容，平台決定如何呈現。
 *
 * 與 [GameEventPublisher] 分工：[GameEventPublisher] 把權威事件通知玩家；此介面提供呈現所需、但不屬於權威狀態的資料
 * （例如擲骰結果、牌牆布局、哪一張是剛摸到的牌）與各種結算內容。
 *
 * 實作必須是 best-effort：沒有平台實作、該對局不支援呈現，或呈現本身失敗時都不得拋例外，呼叫端的權威狀態變更不受影響。
 */
interface GamePresentationPublisher {
    /** 權威遊戲動作成立後通知平台；平台可據此呈現該動作，例如播放宣告語音。 */
    fun publishGameActionDeclared(gameId: Uuid, actorId: Uuid, action: GameAction) = Unit

    /** 通知平台呈現一次流局結算。預設 no-op。 */
    fun publishExhaustiveDrawSettlement(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) = Unit

    /** 通知平台在整場對局結束後呈現最終排行，完成後才可返回房間。 */
    fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) = Unit

    /**
     * 通知平台 [playerId] 開始從立牌中選擇多張牌（`tileSelection`／`preparation` 的 `maxCount > 1`）；平台可據此
     * 提供確認選擇的操作。只需要選一張時不呼叫。
     *
     * 預設 no-op。
     */
    fun publishTileSelectionStarted(gameId: Uuid, playerId: Uuid) = Unit

    /**
     * 通知平台 [playerId] 的多張選牌已送出或已失效；沒有進行中的選牌時應為 no-op。
     *
     * 預設 no-op。
     */
    fun publishTileSelectionEnded(gameId: Uuid, playerId: Uuid) = Unit

    /**
     * 通知平台本局權威擲骰結果。
     *
     * [dealerSeatIndex]／[roundNumber]／[comboCount] 是呼叫端已經持有的通用桌況資料，一併提供讓平台自行決定怎麼使用
     * （例如決定擲骰者的座位）。
     *
     * @param gameId 對局 Uuid。
     * @param dice 本次開門使用的權威擲骰個別點數。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index（自風輪轉不會改變
     *   index，只改變該 index 玩家的風位）。
     * @param roundNumber 本次擲骰發生當下的局數。
     * @param comboCount 本次擲骰發生當下的本場數（連莊次數）。
     */
    fun publishDiceRoll(gameId: Uuid, dice: DiceRollResult, dealerSeatIndex: Int, roundNumber: Int, comboCount: Int)

    /**
     * 通知平台本局牌牆結構。
     *
     * [assemblyStructure] 是牌牆剛完成組裝時的基本格位；[layout] 是規則決定的開門後最終布局。平台可用兩者呈現開門
     * 過程，不必從王牌集合反推規則專有的位移。[dealerSeatIndex] 與 [publishDiceRoll] 相同，讓平台以莊家座位為基準
     * 換算各面牌牆的位置。
     *
     * @param gameId 對局 Uuid。
     * @param assemblyStructure 本局牌牆完成組裝、尚未開門時的面／墩／層格位；牌張集合必須與 [layout]
     * 一致。空布局呼叫時傳空 map。
     * @param layout 本局牌牆所有牌（含活牌與王牌）的完整抽象位置；空布局代表這局結束，只需清除舊牌。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index。
     * @param deadWallTileIds [layout] 之中屬於王牌區的牌 Uuid 子集合；空布局呼叫時可傳空集合。
     * @param diceCount 本次開門擲骰的骰子數量，供平台安排開門前的擲骰時間；未搭配擲骰的呼叫可傳 `0`。
     * @param isNewOpening 這次是否剛完成開門；恢復既有桌況時傳 `false`。平台可據此決定是否呈現從 [assemblyStructure] 到
     * [layout] 的開門過程。
     * @param revealedTileIds [deadWallTileIds] 之中，牌牆建立當下就公開的牌 Uuid 子集合（例如日麻開局就翻開的第一張
     * 寶牌指示牌，由呼叫端以 `TileWallRevealable.getVisibleTileIds` 算出）；不支援此概念的規則傳空集合。動作後才追加
     * 公開的牌屬於 [publishWallTilesRevealed]。
     */
    fun publishWallStructure(
        gameId: Uuid,
        assemblyStructure: Map<Uuid, TileWallPosition>,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
        isNewOpening: Boolean = diceCount > 0,
        revealedTileIds: Set<Uuid> = emptySet(),
    )

    /**
     * 通知平台依序呈現一次已由規則驗證的牌牆布局移動。
     *
     * 呼叫端只可在包含最終布局的權威桌況成功保存後發布；平台不得藉此回寫或重新判定桌況。預設 no-op。
     *
     * @param gameId 對局 Uuid。
     * @param phases 依規則決定順序排列的移動階段。
     */
    fun publishWallLayoutTransition(gameId: Uuid, phases: List<PhysicalWallLayoutTransitionPhase>) = Unit

    /**
     * 通知平台本局牌牆中本次新增公開的牌，例如日麻開槓後翻開的新寶牌指示牌；牌牆建立當下就公開的牌屬於
     * [publishWallStructure] 的 `revealedTileIds`。
     *
     * 規則的 `WallRevealPolicy` 提供新增集合，flow 驗證其符合權威可見差集後才會呼叫；不支援此概念的規則不會發布。
     *
     * @param gameId 對局 Uuid。
     * @param revealedTileIds 本次新增公開的牌 Uuid 集合，不含先前已公開的牌。
     */
    fun publishWallTilesRevealed(gameId: Uuid, revealedTileIds: Set<Uuid>)

    /**
     * 通知平台：規則狀態可能已改變（例如宣告成立），與規則相關的呈現需要依目前桌況更新。
     *
     * 不帶任何規則專屬資料；要呈現什麼完全由平台上該規則登記的描述決定，沒有登記描述的規則不會多出任何呈現。
     * 呼叫端在固定時間點呼叫：開局與換局緊接在 [publishWallStructure] 之後，以及規則狀態改變之後。
     *
     * @param gameId 對局 Uuid。
     */
    fun publishRuleStateUpdated(gameId: Uuid)

    /**
     * 通知平台局況資訊需要更新為目前的權威桌況。
     *
     * Flow 只傳遞遊戲狀態，不決定顯示哪些欄位、翻譯 key 或排列順序；這些屬於平台上的規則 provider。呼叫端應在局況改變
     * 後傳入剛保存的完整 [tableState]。
     *
     * 呼叫時機：開局／換局（與 [publishWallStructure] 同一批呼叫）、每次摸牌（牌山剩餘張數可能改變），以及任何會改變
     * 局況內容的事件（例如立直宣告後供託支數改變）。
     *
     * @param gameId 對局 Uuid。
     * @param tableState 呼叫端當下已保存的權威桌況快照。
     */
    fun publishRoundInfoUpdated(gameId: Uuid, tableState: TableState)

    /**
     * 通知平台某玩家目前的立牌、摸牌位與副露。
     *
     * 三者一起提供，讓平台能一併決定擺放（例如立牌要為副露讓出空間）。開局／換局的初次發牌改用
     * [publishInitialDeal]；這個方法用於一般回合動作（捨牌、摸牌、鳴牌）。
     *
     * @param gameId 對局 Uuid。
     * @param seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
     * @param standingTileIds 這位玩家目前立牌，依手牌順序排列（`Hand.tiles`，不含 [drawnTileId]），鍵為
     * [IdentifiedTile.id]；空清單代表這局結束，只需要清除舊牌。
     * @param drawnTileId 這位玩家目前摸到、尚未併入立牌或打出的那張牌 Uuid（`Hand.lastDrawn`）；
     * `null` 代表目前沒有摸牌位。
     * @param melds 這位玩家目前所有副露，依宣告順序排列。
     * @param isNewlyDrawn [drawnTileId] 是否為這次剛摸到的牌：只有真正的摸牌（`DrawTileUseCase`）傳 `true`，平台可據此
     * 呈現摸牌的過程；其餘呼叫即使摸牌位仍有牌也維持 `false`。
     * @param newlyClaimedMeldTileIds 這次新成立副露、從原本位置移入副露的牌 Uuid：吃／碰／明槓／暗槓為整組，加槓只有
     * 新加入的那一張。空集合（預設值）代表沒有。
     */
    fun publishPlayerTilesUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        isNewlyDrawn: Boolean = false,
        newlyClaimedMeldTileIds: Set<Uuid> = emptySet(),
    )

    /**
     * 通知平台本局開局（或換局）的初次發牌。
     *
     * 一次帶齊所有座位的最終手牌，讓平台能讓各座位同一批的發牌同時進行；發牌完成後的手牌變化一律改用
     * [publishPlayerTilesUpdated]。開局當下沒有摸牌位與副露，因此不帶這兩項。
     *
     * @param gameId 對局 Uuid。
     * @param handTileIdsBySeatIndex 每個座位最終手牌的完整牌 Uuid 列表，依發牌順序排列，鍵為 `TableState.players` 的固定
     * 座位 index；決定每一批發到哪些牌。規則不支援開門流程（沒有牌牆／擲骰）時呼叫端不應呼叫這個方法。
     * @param postFlipHandTileIdsBySeatIndex 每個座位發牌完成後的最終牌序，鍵同上；沒有啟用自動整理手牌的座位與
     * [handTileIdsBySeatIndex] 內容相同，有啟用的座位為整理後的順序。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index，發牌從這個座位開始輪。
     * @param dealBatchSizes 依序發牌的批次大小列表，由呼叫端依規則設定的 `dealBatchSizes()` 算出；
     * 平台不驗證總和是否等於各座位手牌張數。
     * @param diceCount 本次開局擲骰的骰子數量，供平台安排發牌前的擲骰時間；規則不支援開門流程時傳 `0`。
     */
    fun publishInitialDeal(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        dealBatchSizes: List<Int>,
        diceCount: Int,
    )

    /**
     * 清除所有玩家的立牌、摸牌位與副露呈現；對局結束、回到房間等情境使用。
     *
     * @param gameId 對局 Uuid。
     */
    fun clearPlayerTiles(gameId: Uuid)

    /**
     * 通知平台本局開局的座位安排。只在開局時呼叫一次，之後連莊／過莊開新局不會再次呼叫——風位輪轉是規則概念，玩家的
     * 座位整場對局固定不變。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 依 `TableState.players` 固定座位順序排列的玩家 Uuid 清單。
     */
    fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>)

    /**
     * 通知平台某玩家的牌河需要更新為目前狀態。
     *
     * 呼叫時機：該玩家捨牌後，或該玩家先前的捨牌被吃／碰／槓走、使牌河紀錄的 `isTaken` 狀態改變時（即使沒有新增捨牌，
     * 側身標記的牌也可能因此改變）。
     *
     * @param gameId 對局 Uuid。
     * @param seatIndex 牌河所屬玩家在 `TableState.players` 的固定座位 index。
     * @param discardTileIds 這位玩家目前牌河所有紀錄的牌 Uuid，依捨牌順序排列。
     * @param sidewaysMarkedTileId 這位玩家牌河中應標記為側身的牌 Uuid；`null` 代表沒有（例如非立直規則，或立直牌已被
     * 鳴走且尚無下一張捨牌）。用泛用的「側身標記」措辭而非「立直」，讓這個介面維持規則無關。
     * @param newlyDiscardedTileId [discardTileIds] 之中這次呼叫真正新增的那張牌 Uuid，平台可據此呈現捨牌的過程；
     * `null`（預設值）代表這次只是既有牌河重新整理。只有捨牌（`DiscardTileUseCase`）那次會傳入實際 Uuid。
     */
    fun publishDiscardPileUpdated(
        gameId: Uuid,
        seatIndex: Int,
        discardTileIds: List<Uuid>,
        sidewaysMarkedTileId: Uuid?,
        newlyDiscardedTileId: Uuid? = null,
    )

    /**
     * 通知平台這次胡牌成立的贏家，平台據此呈現胡牌演出。
     *
     * 呼叫時機：贏家結算完成、既有事件廣播之後——自摸（`DeclareTsumoUseCase`）緊接在廣播 [GameAction.Tsumo] 之後；
     * 榮和／搶槓（`RespondToDiscardUseCase`／`RespondToRobbingUseCase`）在既有事件廣播之後，一次包含這次所有贏家。
     *
     * 不攜帶贏家手牌的完整內容，手牌如何排列由平台依規則模組的牌序決定。
     *
     * @param gameId 對局 Uuid。
     * @param request 胡牌張、是否自摸與各贏家的展示理由；榮和／搶槓時胡牌張位在放銃者的牌河或副露。
     */
    fun publishWinCelebration(gameId: Uuid, request: WinCelebrationRequest)

    /** 通知平台在胡牌演出之後呈現各贏家詳情與共用分數排行。 */
    fun publishWinSettlement(gameId: Uuid, request: WinSettlementPresentationRequest) = Unit

    /**
     * 通知平台一次完整的胡牌呈現：胡牌演出與結算**依序**呈現。
     *
     * 跟分別呼叫 [publishWinCelebration] 與 [publishWinSettlement] 的關鍵差異是**順序是 API 契約**：
     * 實作必須保證結算排在胡牌演出結束之後才開始，不得依賴兩次獨立非同步呼叫的先後順序。
     *
     * 分別呼叫的那兩個方法保留給不成對的呼叫點（例如流局特殊結果只需要結算）。
     */
    fun publishWinPresentation(gameId: Uuid, request: WinPresentationRequest) = Unit
}

/**
 * 一次胡牌的完整呈現請求。
 *
 * 「提供哪些內容」與「是否暫停其他玩家」是兩件獨立的事：
 * - 內容：[celebration] 與 [settlement]；中途胡牌時結算的資訊範圍見 [ContinuingWinSettlementDetail]。
 * - 是否暫停：[roundContinues] 為 `false` 時一律暫停；為 `true` 時由平台決定，見下方說明。
 *
 * @property winnerPlayerIds 這次一起成立的所有贏家；[roundContinues] 時平台須依此收尾這些玩家的手牌呈現。
 * @property celebration 胡牌演出請求；一律呈現、不可省略，這是其他玩家得知「這位玩家胡了」的依據。
 * @property settlement 結算請求。
 * @property roundContinues 本局是否在這次胡牌之後仍然繼續（已完成玩家退出、其他人續打）。
 *
 * `false`（本局就此結束）時，整段呈現期間其他玩家都在等待。
 *
 * `true` 時只有平台認為需要所有人觀看的段落才暫停遊戲，例如依展示理由播放、且要求所有人看完的展示；暫停期間停止
 * 玩家輸入、AI、強制自動操作與決策計時器。其餘段落（包括整個結算）不得阻擋仍在本局中的玩家。不論是否暫停，換局都會
 * 等整段呈現結束；整段結束後，平台須把贏家的手牌呈現收尾（見 `GamePresentationBusyGate.isPresentingContinuingWin`）。
 */
data class WinPresentationRequest(
    val winnerPlayerIds: Set<Uuid>,
    val celebration: WinCelebrationRequest,
    val settlement: WinSettlementPresentationRequest,
    val roundContinues: Boolean,
) {
    init {
        require(winnerPlayerIds.isNotEmpty()) { "A win presentation must have at least one winner" }
    }
}
