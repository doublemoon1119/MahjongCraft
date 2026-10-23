package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.AppliedActionScoring
import com.doublemoon1119.mahjongcraft.flow.common.game.model.DrawFollowUpPresentationStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.MatchSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.OpeningDiceRoll
import kotlin.uuid.Uuid

/**
 * 剝除 [Meld] 的牌面，只保留 [MeldPresentation] 需要的資訊。
 *
 * 牌 ID 保留副露成立時的穩定順序；[MeldType.ADDED_KAN] 補上的第四張（[Meld.tiles] 的最後一張）維持在最後。
 * 鳴取的牌由 [MeldPresentation.calledTileId] 指出，不靠位置辨識。
 *
 * @param revealsClosedKanTiles 該規則是否公開暗槓的牌（[MahjongRuleConfig.revealsClosedKanTiles]），
 * 只影響暗槓的逐牌朝向，非暗槓時傳入的值不影響結果。
 * @return 保留已保存牌序及逐牌朝向、不包含牌面的呈現描述。
 */
fun Meld.toPresentation(revealsClosedKanTiles: Boolean): MeldPresentation {
    val orderedIds = tiles.map { it.id }
    val faceDownTileIds = when {
        type == MeldType.CLOSED_KAN && !revealsClosedKanTiles -> orderedIds.toSet()
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
 * 與 [GameEventPublisher] 分工：[GameEventPublisher] 把權威事件通知玩家；此介面提供呈現所需、但不屬於權威狀態的資料
 * （例如牌牆布局與哪一張是剛摸到的牌）與已提交的擲骰結果、各種結算內容。
 *
 * 實作必須是 best-effort：沒有平台實作、該對局不支援呈現，或呈現本身失敗時都不得拋例外，呼叫端的權威狀態變更不受影響。
 * 新局呈現依序發布牌牆、完整擲骰記錄、規則與局況資訊，最後發布初次發牌。
 * 平台在初次發牌收到前可收集同批請求，再依自己的時間線交付，不能以動畫回寫權威結果。
 */
interface GamePresentationPublisher {
    /**
     * 權威遊戲動作成立後通知平台；平台可據此呈現該動作，例如播放宣告語音。
     *
     * @param gameId 對局識別碼。
     * @param actorId 執行該動作的玩家識別碼。
     * @param action 已成立的權威遊戲動作。
     */
    fun publishGameActionDeclared(gameId: Uuid, actorId: Uuid, action: GameAction) = Unit

    /**
     * 動作成立當下的點數收付寫入權威狀態後通知平台；平台可據此呈現各玩家的增減。分數本身照常隨快照更新。
     *
     * 摸牌後自動處理每一步的收付不經這個方法，改隨 [publishPlayerTilesUpdated] 的 `followUpSteps` 交付，讓平台在該步呈現。
     *
     * @param gameId 對局識別碼。
     * @param scoring 已套用的收付。
     */
    fun publishActionScored(gameId: Uuid, scoring: AppliedActionScoring) = Unit

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
     * 通知平台 [playerId] 開始從立牌中選擇多張牌（`tileSelection`／`preparation` 的 `maxCount > 1`）；平台可據此
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
     * 寶牌指示牌，由呼叫端以 `TileWallRevealable.getVisibleTileIds` 算出）；不支援此概念的規則傳空集合。動作後才追加
     * 公開的牌屬於 [publishWallTilesRevealed]。
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
     * 通知平台依序呈現一次已由規則驗證的牌牆布局移動。
     *
     * 呼叫端只可在包含最終布局的權威桌況成功保存後發布；平台不得藉此回寫或重新判定桌況。預設 no-op。
     *
     * @param gameId 對局 Uuid。
     * @param phases 依規則決定順序排列的移動階段。
     */
    fun publishWallLayoutTransition(gameId: Uuid, phases: List<PhysicalWallLayoutTransitionPhase>) = Unit

    /**
     * 以已提交的布局更新既有牌張，不重建牌牆或重播開門。
     *
     * 用於準備步驟將牌移回牌牆或重新分配牌牆位置；移出牌牆的牌由其目的牌區接手呈現。
     * 不支援呈現的平台可維持預設 no-op。
     *
     * @param gameId 對局識別碼。
     * @param layout 更新後仍在牌牆中的完整布局。
     * @param dealerSeatIndex 本局莊家在固定座位中的索引。
     * @param revealedTileIds 布局中應公開正面的牌張識別碼。
     */
    fun publishWallLayoutUpdated(
        gameId: Uuid,
        layout: TileWallPhysicalLayout,
        dealerSeatIndex: Int,
        revealedTileIds: Set<Uuid>,
    ) = Unit

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
     * [publishInitialDeal]；這個方法用於一般回合動作與開局準備步驟完成後的牌區更新。
     *
     * @param gameId 對局 Uuid。
     * @param seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
     * @param standingTileIds 這位玩家目前立牌，依手牌順序排列（`Hand.tiles`，不含 [drawnTileId]），鍵為
     * [IdentifiedTile.id]；空清單代表這局結束，只需要清除舊牌。
     * @param drawnTileId 這位玩家目前摸到、尚未併入立牌或打出的那張牌 Uuid（`Hand.lastDrawn`）；
     * `null` 代表目前沒有摸牌位。
     * @param melds 這位玩家目前所有副露，依宣告順序排列。
     * @param setAsideTileIds 這位玩家移出手牌、公開擺在桌上的牌 Uuid（見 `MahjongRuleModule.setAsideTiles`），依移出
     * 順序排列；沒有時為空清單。
     * @param isNewlyDrawn [drawnTileId] 是否為這次剛從牌牆摸到的牌：一般摸牌與槓、移出手牌後的補牌都傳 `true`，平台可據此
     * 呈現摸牌的過程；其餘呼叫即使摸牌位仍有牌也維持 `false`。
     * @param newlyClaimedMeldTileIds 這次新成立副露、從原本位置移入副露的牌 Uuid：吃／碰／明槓／暗槓為整組，加槓只有
     * 新加入的那一張。空集合（預設值）代表沒有。
     * @param newlySetAsideTileIds 這次剛移出手牌、公開擺到桌上的牌 Uuid（見 `MahjongRuleModule.setAsideTiles`），例如
     * 三人日麻拔出的北。空集合（預設值）代表沒有。不包含 [followUpSteps] 移出的牌。
     * @param followUpSteps 這次摸牌或補牌之後依序完成的自動移出與補牌；空清單（預設值）代表沒有。不為空時，第一步移出的牌是
     * 這次摸到的牌之一，每一步移出的牌在那一步開始時位於摸牌位；[drawnTileId] 是處理結束後留在摸牌位的牌，最後一步因搶和
     * 視窗暫停時則是暫停時摸牌位上的牌。各步移出的牌也包含在 [setAsideTileIds]。平台應等前一步的牌落地後再呈現下一步，
     * 並在該步呈現它的實體牌牆移動與點數收付。
     */
    fun publishPlayerTilesUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        setAsideTileIds: List<Uuid>,
        isNewlyDrawn: Boolean = false,
        newlyClaimedMeldTileIds: Set<Uuid> = emptySet(),
        newlySetAsideTileIds: Set<Uuid> = emptySet(),
        followUpSteps: List<DrawFollowUpPresentationStep> = emptyList(),
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
     * @param openingDiceCounts 各輪的骰子數量，供平台安排發牌前的擲骰時間；沒有擲骰時傳空清單。
     */
    fun publishInitialDeal(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        postFlipHandTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
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
