package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/**
 * 剝除 [Meld] 的牌面，只保留 [MeldPresentation] 需要的資訊。
 *
 * 牌 ID 保留副露成立時的穩定順序；[MeldType.AddedKan] 補上的第四張（[Meld.tiles] 的最後一張）維持在最後。
 * 鳴取的牌由 [MeldPresentation.calledTileId] 指出，不靠位置辨識。
 *
 * @param revealsClosedKanTiles 該規則是否公開暗槓的牌（[MahjongRuleConfig.revealsClosedKanTiles]），
 * 只影響暗槓的逐牌朝向，非暗槓時傳入的值不影響結果。
 * @return 保留已保存牌序及逐牌朝向、不包含牌面的呈現描述。
 */
fun Meld.toPresentation(revealsClosedKanTiles: Boolean): MeldPresentation {
    val orderedIds = tiles.map { it.id }
    val faceDownTileIds = when {
        type == MeldType.ClosedKan && !revealsClosedKanTiles -> orderedIds.toSet()
        else -> faceDownTileIds.intersect(orderedIds.toSet())
    }
    return MeldPresentation(
        type = type,
        tileIds = orderedIds,
        calledTileId = sourceTile?.id,
        sourceDirection = sourceDirection,
        allTilesFaceDown = faceDownTileIds.size == orderedIds.size,
        faceDownTileIds = faceDownTileIds,
    )
}

/**
 * 把對局中發生的事通知平台呈現層的出口：flow 決定通知的時機與語意內容，平台決定如何呈現。
 *
 * 與 [GameEventPublisher] 分工：[GameEventPublisher] 把權威事件通知玩家；此介面提供呈現所需的桌況步驟、牌牆布局、
 * 已提交的擲骰結果與各種結算內容。
 *
 * 實作必須是 best-effort：沒有平台實作、該對局不支援呈現，或呈現本身失敗時都不得拋例外，呼叫端的權威狀態變更不受影響。
 * 新局呈現依序發布牌牆、完整擲骰記錄，最後發布初次發牌；初次發牌的呈現也包含這一局的桌上物件、局況與玩家資訊。
 * 平台在初次發牌收到前可收集同批請求，再依自己的時間線交付，不能以動畫回寫權威結果。
 */
interface GamePresentationPublisher {
    /**
     * 通知平台呈現一次流局結算。預設 no-op。
     *
     * @param gameId 對局識別碼。
     * @param request 流局結算呈現資料。
     */
    fun publishExhaustiveDrawSettlement(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) = Unit

    /**
     * 通知平台在整場對局結束後呈現最終排行，完成後才可返回房間。
     *
     * @param gameId 對局識別碼。
     * @param request 終局排行呈現資料。
     */
    fun publishMatchSettlement(gameId: Uuid, request: MatchSettlementPresentationRequest) = Unit

    /**
     * 通知平台 [playerId] 開始從立牌中選擇多張牌（`tileSelection`／`rule decision` 的 `maxCount > 1`）；平台可據此
     * 提供確認選擇的操作。只需要選一張時不呼叫。
     *
     * 預設 no-op。
     *
     * @param gameId 對局識別碼。
     * @param playerId 開始選牌的玩家識別碼。
     */
    fun publishTileSelectionStarted(gameId: Uuid, playerId: Uuid) = Unit

    /**
     * 通知平台 [playerId] 的多張選牌已送出或已失效；沒有進行中的選牌時應為 no-op。
     *
     * 預設 no-op。
     *
     * @param gameId 對局識別碼。
     * @param playerId 已送出或失去選牌狀態的玩家識別碼。
     */
    fun publishTileSelectionEnded(gameId: Uuid, playerId: Uuid) = Unit

    /**
     * 通知平台依輪次呈現本局全部權威擲骰結果。
     *
     * @param gameId 對局 Uuid。
     * @param rolls 依擲骰順序排列的記錄；每筆指定實際擲骰者與個別點數。
     */
    fun publishOpeningRolls(
        gameId: Uuid,
        rolls: List<OpeningDiceRoll>,
    )

    /**
     * 通知平台本局牌牆結構。
     *
     * [assemblyStructure] 是牌牆剛完成組裝時的基本格位；[layout] 是規則決定的開門後最終布局。平台可用兩者呈現開門
     * 過程，不必從王牌集合反推規則專有的位移。[dealerSeatIndex] 讓平台以莊家座位為基準
     * 換算各面牌牆的位置。
     *
     * @param gameId 對局 Uuid。
     * @param assemblyStructure 本局牌牆完成組裝、尚未開門時的面／墩／層格位；牌張集合必須與 [layout]
     * 一致。空布局呼叫時傳空 map。
     * @param layout 本局牌牆所有牌（含活牌與王牌）的完整抽象位置；空布局代表這局結束，只需清除舊牌。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index。
     * @param deadWallTileIds [layout] 之中屬於王牌區的牌 Uuid 子集合；空布局呼叫時可傳空集合。
     * @param openingDiceCounts 各輪的骰子數量，供平台安排開門前的擲骰時間；沒有擲骰時傳空清單。
     * @param isNewOpening 這次是否剛完成開門；恢復既有桌況時傳 `false`。平台可據此決定是否呈現從 [assemblyStructure] 到
     * [layout] 的開門過程。
     * @param revealedTileIds [deadWallTileIds] 之中，牌牆建立當下就公開的牌 Uuid 子集合（例如日麻開局就翻開的第一張
     * 寶牌指示牌，由呼叫端從組件結果挑出已公開且正面朝上的牌）；不支援此概念的規則傳空集合。動作後才追加
     * 公開的牌由 [publishTableSteps] 的桌況得出。
     */
    fun publishWallStructure(
        gameId: Uuid,
        assemblyStructure: Map<Uuid, TileWallPosition>,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        openingDiceCounts: List<Int>,
        isNewOpening: Boolean = true,
        revealedTileIds: Set<Uuid> = emptySet(),
    )

    /**
     * 通知平台一次權威交易中依序發生的步驟。
     *
     * 每一步與平台上一次呈現的桌況比較：牌在區域之間的移動、同一區內的位置調整、姿態與公開狀態的改變，以及桌上物件、
     * 局況與玩家資訊，都由平台從兩份桌況算出；[TableStep.events] 只說明這一步發生的事。平台依序呈現，前一步完成後才
     * 開始下一步，跨玩家也一樣；同一步的變化同時發生。
     *
     * 開局與換局的初次發牌改用 [publishInitialDeal]。
     *
     * @param gameId 對局識別碼。
     * @param steps 依發生順序排列的步驟；最後一步通常是這次交易提交的權威桌況，等待搶和時是宣告完成的候選桌況。
     */
    fun publishTableSteps(gameId: Uuid, steps: List<TableStep>)

    /**
     * 通知平台本局開局（或換局）的初次發牌。
     *
     * 一次帶齊所有座位的最終手牌，讓平台能讓各座位同一批的發牌同時進行；發牌完成後的變化一律改用
     * [publishTableSteps]，並從 [tableState] 接續比較。平台同時依 [tableState] 呈現這一局的桌上物件、局況與玩家資訊。
     *
     * @param gameId 對局 Uuid。
     * @param tableState 發牌完成後的權威桌況；各座位立牌的順序是發牌完成後的最終牌序，莊家由它決定。
     * @param handTileIdsBySeatIndex 每個座位最終手牌的完整牌 Uuid 列表，依發牌順序排列，鍵為 `TableState.players` 的固定
     * 座位 index；決定每一批發到哪些牌。規則不支援開門流程（沒有牌牆／擲骰）時呼叫端不應呼叫這個方法。
     * @param dealBatchSizes 依序發牌的批次大小列表，由呼叫端依規則設定的 `dealBatchSizes()` 算出；
     * 平台不驗證總和是否等於各座位手牌張數。
     * @param openingDiceCounts 各輪的骰子數量，供平台安排發牌前的擲骰時間；沒有擲骰時傳空清單。
     */
    fun publishInitialDeal(
        gameId: Uuid,
        tableState: TableState,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealBatchSizes: List<Int>,
        openingDiceCounts: List<Int>,
    )

    /**
     * 清除所有玩家的立牌、摸牌位與副露呈現；對局結束、回到房間等情境使用。
     *
     * @param gameId 對局 Uuid。
     */
    fun clearPlayerTiles(gameId: Uuid)

    /**
     * 通知平台本場對局開局的座位安排。只在開局時呼叫一次；之後連莊、過莊開新局不會再次呼叫，風位輪轉是規則概念。
     * 規則要求局間換位時改由 [publishSeatOrderChanged] 通知。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 依 `TableState.players` 座位順序排列的玩家 Uuid 清單。
     */
    fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>)

    /**
     * 通知平台規則在局與局之間讓玩家換了座位。在下一局開始、擺牌與桌上物件之前呼叫，每次換位一次；座位沒變時不呼叫。
     *
     * 平台應讓座位改變的玩家移到新座位，即使他們正坐在這張桌子的舊座位上。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 依新的 `TableState.players` 座位順序排列的玩家 Uuid 清單。
     */
    fun publishSeatOrderChanged(gameId: Uuid, seatedPlayerIds: List<Uuid>)

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

    /**
     * 通知平台在胡牌演出之後呈現各贏家詳情與共用分數排行。
     *
     * @param gameId 對局識別碼。
     * @param request 胡牌結算呈現資料。
     */
    fun publishWinSettlement(gameId: Uuid, request: WinSettlementPresentationRequest) = Unit

    /**
     * 通知平台一次完整的胡牌呈現：胡牌演出與結算**依序**呈現。
     *
     * 跟分別呼叫 [publishWinCelebration] 與 [publishWinSettlement] 的關鍵差異是**順序是 API 契約**：
     * 實作必須保證結算排在胡牌演出結束之後才開始，不得依賴兩次獨立非同步呼叫的先後順序。
     *
     * 分別呼叫的那兩個方法保留給不成對的呼叫點（例如流局特殊結果只需要結算）。
     *
     * @param gameId 對局識別碼。
     * @param request 胡牌演出與結算的完整呈現資料。
     */
    fun publishWinPresentation(gameId: Uuid, request: WinPresentationRequest) = Unit
}
