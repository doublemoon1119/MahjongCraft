package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 供 [GamePresentationPublisher.publishPlayerAreaUpdated] 使用的單一副露呈現資料。
 *
 * 刻意只帶位置呈現需要的最小資訊（種類、牌 Uuid 列表、鳴取來源牌 Uuid、鳴取方位），不像 [Meld] 那樣
 * 攜帶實際 `Tile` 牌面——管理中牌張的牌面完全交給 client 端依可見性快照另外呈現，平台呈現層只需要
 * 知道怎麼擺位置。
 *
 * @property type 副露種類，決定橫放張數與位置排列。
 * @property tileIds 組成這組副露的所有牌 Uuid，依 [Meld.tiles] 原始順序排列。
 * @property calledTileId 鳴取自他家的那張牌 Uuid；暗槓沒有鳴牌來源時為 `null`。
 * @property sourceDirection 鳴取來源的相對方位，決定 [calledTileId] 在組內橫放的位置（左／中／右）；
 * 暗槓沒有鳴牌來源時為 [RelativeDirection.Self]。
 * @property allTilesFaceDown 只在 [type] 為 [MeldType.CLOSED_KAN] 時有意義：該規則是否連暗槓身份都
 * 不公開（例如台灣麻將），此時四張牌全部蓋牌呈現；日本麻將等身份公開的規則此欄位為 `false`，維持
 * 兩端蓋牌、中間兩張攤牌的傳統呈現方式。其餘副露種類固定為 `false`（一律牌面朝上，不受此欄位影響）。
 */
data class MeldPresentation(
    val type: MeldType,
    val tileIds: List<Uuid>,
    val calledTileId: Uuid?,
    val sourceDirection: RelativeDirection,
    val allTilesFaceDown: Boolean,
)

/**
 * 剝除 [Meld] 的實際牌面，只保留 [MeldPresentation] 需要的位置呈現資訊。
 *
 * @param revealsClosedKanTiles 該規則是否公開暗槓身份（[MahjongRuleConfig.revealsClosedKanTiles]），
 * 只影響 [MeldPresentation.allTilesFaceDown] 的計算，非暗槓時傳入的值不影響結果。
 */
fun Meld.toPresentation(revealsClosedKanTiles: Boolean): MeldPresentation = MeldPresentation(
    type = type,
    tileIds = tiles.map { it.id },
    calledTileId = sourceTile?.id,
    sourceDirection = sourceDirection,
    allTilesFaceDown = type == MeldType.CLOSED_KAN && !revealsClosedKanTiles,
)

/**
 * 對局 in-process 呈現觸發器。
 *
 * `:mahjong-flow` 對外傳遞「只有平台呈現層需要、不該進入 `TableState`／persistence／network DTO」
 * 一次性資料的出口——目前是開局／連莊重新擲骰開門時的權威骰子結果與牌牆結構座標。這些資料只在牌局
 * 剛初始化的那個當下存在，呼叫端用完即可丟棄，不需要另外保存。
 *
 * 與 [GameEventPublisher] 分工明確：[GameEventPublisher] 負責通知玩家（跨網路、需要序列化）；此介面
 * 負責觸發 server 端本地呈現邏輯（不跨網路、不需要序列化）。實作方必須是 best-effort——沒有平台
 * 實作、該桌不是對應平台的桌子、或呈現觸發本身失敗時，都不能拋例外，呼叫端的權威狀態變更不因此
 * 受影響。
 */
interface GamePresentationPublisher {
    /**
     * 通知平台呈現層本局權威擲骰結果。
     *
     * [dealerSeatIndex]／[roundNumber]／[comboCount] 是呼叫端已經持有的通用桌況資料，一併帶過去讓
     * 平台呈現層自行決定怎麼用（例如換算成畫面呈現用的「這是第幾次擲骰」序號、決定擲骰者的座位）——
     * 不在這裡先算好任何 Minecraft 專屬概念，維持這個介面本身跟平台無關。
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
     * 通知平台呈現層本局牌牆結構座標。
     *
     * [dealerSeatIndex] 跟 [publishDiceRoll] 同理，是呼叫端已經持有的通用桌況資料，一併帶過去讓平台
     * 呈現層自行決定怎麼把牌牆面／墩／層結構換算成以莊家座位為基準的世界座標。
     *
     * [deadWallTileIds]／[diceCount] 讓平台呈現層知道「哪些牌是王牌」與「這次擲骰動畫要播多久」——
     * 王牌區要跟活牌保持一點視覺距離，但這個分離要等骰子動畫播完才觸發（比照真實麻將牌桌開門後才把
     * 王牌移出的節奏），不能在牌牆剛生成的當下就直接呈現，否則會少了「開門」的過程，缺少沉浸感。
     * 呼叫端只負責提供這兩項資料，何時、如何觸發王牌分離的呈現細節仍完全交給平台實作決定。
     *
     * @param gameId 對局 Uuid。
     * @param structure 本局牌牆所有牌（含活牌與王牌）的面／墩／層結構座標，鍵為 [IdentifiedTile.id]；空 map 代表這局結束，只需要清除
     * 舊牌。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index。
     * @param deadWallTileIds [structure] 之中屬於王牌區的牌 Uuid 子集合；空 map 呼叫時可傳空集合。
     * @param diceCount 本次開門擲骰的骰子數量，供平台實作換算擲骰動畫總長度；未搭配擲骰的呼叫可傳 `0`。
     * @param revealedTileIds [deadWallTileIds] 之中，牌牆建立當下就該立即公開翻面的牌 Uuid 子集合
     * （例如日麻開局就翻開的第一張寶牌指示牌，由呼叫端用 `TileWallRevealable.getVisibleTileIds`
     * 算出）；平台實作會在王牌移出開門位置的同一個時機點翻開這些牌，不支援此概念的規則傳空集合即可。
     * 槓牌後才追加公開的牌屬於 [publishDeadWallRevealUpdated] 的職責，不是這裡。
     */
    fun publishWallStructure(
        gameId: Uuid,
        structure: Map<Uuid, TileWallPosition>,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
        revealedTileIds: Set<Uuid> = emptySet(),
    )

    /**
     * 通知平台呈現層本局王牌區裡，目前完整應該公開翻面的牌集合有異動——用於牌牆建立**之後**才追加
     * 公開的牌，例如日麻槓牌成立後翻開的新寶牌指示牌；開局當下就該公開的第一張（不需要等任何事件）
     * 屬於 [publishWallStructure] 的 `revealedTileIds`，不是這裡，兩者是完全獨立的呈現時機。
     *
     * 刻意用泛用的「應該公開翻面」措辭而非「寶牌」，讓這個介面本身維持規則無關——呼叫端一律用
     * `TileWallRevealable.getVisibleTileIds` 算出目前完整該公開的集合，不支援此概念的規則永遠不會
     * 呼叫這個方法。
     *
     * @param gameId 對局 Uuid。
     * @param revealedTileIds 目前完整應該公開翻面的王牌 Uuid 集合（不是只有新增的那幾張），平台實作
     * 逐張翻面、冪等，呼叫端不需要自行比對差異。
     */
    fun publishDeadWallRevealUpdated(gameId: Uuid, revealedTileIds: Set<Uuid>)

    /**
     * 通知平台呈現層本局莊家角落的積棒（連莊棒）數量。
     *
     * 跟牌牆同一個時機點觸發（呼叫端緊接在 [publishWallStructure] 之後呼叫），不是每次打牌/摸牌/
     * 鳴牌都觸發——積棒數量整局固定（等於 `TableState.comboCount`），只有連莊/換局時才變，生命週期
     * 綁在牌牆生成，不是綁在 [publishPlayerAreaUpdated]。
     *
     * @param gameId 對局 Uuid。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index——積棒只在莊家角落顯示。
     * @param stickCount 該顯示的積棒支數，恆等於 `TableState.comboCount`；`0` 代表這局還沒連莊過，
     * 等同只清除舊積棒。
     */
    fun publishScoringSticksUpdated(gameId: Uuid, dealerSeatIndex: Int, stickCount: Int)

    /**
     * 通知平台呈現層某玩家目前的手牌（含摸牌位）與副露需要更新為目前狀態。
     *
     * 原本是 `publishHandTiles`／`publishTileDrawn`／`publishMeldsUpdated` 三個獨立方法，合併成這一個
     * 的理由：手牌（含摸牌位）要能對副露＋積棒讓開空間，前提是同一次呼叫必須同時知道「立牌、摸牌、
     * 副露、積棒支數」四種狀態——平台實作才能一次算出正確的讓開偏移，不能分開觸發、各自為政。
     *
     * 開局/換局的初次發牌不走這個方法——那有專屬的分批動畫節奏，見 [publishInitialDealAnimation]；
     * 這個方法固定同步呈現，適用一般回合動作（捨牌、摸牌、鳴牌）。
     *
     * @param gameId 對局 Uuid。
     * @param seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
     * @param standingTileIds 這位玩家目前立牌，依發牌／捨牌後的順序排列（`Hand.tiles`，不含
     * [drawnTileId]），鍵為 [IdentifiedTile.id]；空清單代表這局結束，只需要清除舊牌。
     * @param drawnTileId 這位玩家目前摸到、尚未併入立牌或打出的那張牌 Uuid（`Hand.lastDrawn`）；
     * `null` 代表目前沒有摸牌位要呈現。
     * @param melds 這位玩家目前所有副露，依宣告順序排列——第一組（最早宣告）位於副露區固定的桌角
     * 錨點外緣（積棒外緣），後續每組依序往玩家自己手牌方向排開，呼叫端不需要另外傳遞位置索引。
     * @param comboStickCount 這位玩家目前該顯示的積棒支數——只有莊家非零，等於 `TableState.comboCount`；
     * 只用來讓手牌／副露正確讓開積棒佔用的空間，不會觸發積棒 entity 本身的生成／清除（那是
     * [publishScoringSticksUpdated] 的職責）。
     * @param animateDrawnTile [drawnTileId] 非 `null` 時，是否要播放「牌從牌山原位面朝下起飛、隱形
     * 傳送到摸牌位、傳送同一瞬間切換成面向玩家、解除隱形後落下」的動畫——只有真正的摸牌事件
     * （`DrawTileUseCase`）該傳 `true`；其餘呼叫端（捨牌、鳴牌、副露相關回應）即使當下摸牌位仍有牌，
     * 也維持預設 `false` 直接定格顯示，不重複播放動畫。跟 [publishInitialDealAnimation] 的差別是翻面
     * 發生在隱形期間、玩家看不到旋轉過程，不需要落地後再另外播放一段看得見的翻牌動畫——摸牌是高頻的
     * 單張動作，不需要像開局那樣等所有座位到齊才一起揭曉。
     */
    fun publishPlayerAreaUpdated(
        gameId: Uuid,
        seatIndex: Int,
        standingTileIds: List<Uuid>,
        drawnTileId: Uuid?,
        melds: List<MeldPresentation>,
        comboStickCount: Int,
        animateDrawnTile: Boolean = false,
    )

    /**
     * 通知平台呈現層本局開局（或換局）的初次發牌動畫。
     *
     * 跟原本逐座位呼叫 [publishPlayerAreaUpdated] 不同，這個方法一次帶齊所有座位的最終手牌，讓平台
     * 實作能把每個座位「同一批」的牌同時排入動畫時間軸——四位玩家同時摸牌、同時落地，不是各自獨立的
     * 時間軸。呼叫端固定在開局/換局流程裡取代原本逐座位呼叫 [publishPlayerAreaUpdated] 的那一段，只
     * 用於初次發牌；發牌完成後的立牌張數異動（捨牌、鳴牌等）一律回到 [publishPlayerAreaUpdated]。
     *
     * 摸牌位／副露在開局當下必定為空／`null`——沒有人已經摸牌、也沒有任何宣告，因此不像
     * [publishPlayerAreaUpdated] 需要收這兩項參數。
     *
     * @param gameId 對局 Uuid。
     * @param handTileIdsBySeatIndex 每個座位最終手牌的完整牌 Uuid 列表，鍵為 `TableState.players` 的
     * 固定座位 index；規則不支援開門流程（沒有牌牆／擲骰）時呼叫端不應呼叫這個方法。
     * @param dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index，只用來換算積棒佔用
     * 寬度（[comboStickCount] 只有莊家非零）。
     * @param comboStickCount 開局當下該顯示的積棒支數，等於 `TableState.comboCount`；理由同
     * [publishPlayerAreaUpdated] 的同名參數。
     * @param dealBatchSizes 依序播放的批次大小列表，由呼叫端依規則模組的
     * `MahjongRuleModule.dealBatchSizes` 算出；平台實作依序播放，不驗證總和是否等於各座位手牌張數。
     * @param diceCount 本次開局擲骰的骰子數量，供平台實作換算「發牌動畫該等擲骰動畫播完才開始」的
     * 延遲時長；規則不支援開門流程時傳 `0`。
     */
    fun publishInitialDealAnimation(
        gameId: Uuid,
        handTileIdsBySeatIndex: Map<Int, List<Uuid>>,
        dealerSeatIndex: Int,
        comboStickCount: Int,
        dealBatchSizes: List<Int>,
        diceCount: Int,
    )

    /**
     * 清除整桌所有玩家的手牌/摸牌位/副露/積棒呈現——對局結束、回房間等清空情境使用，沒有座位分組
     * 資料可傳時呼叫這個方法，取代原本 `publishHandTiles(gameId, emptyMap(), 0)` 的空 map 清空語意。
     *
     * @param gameId 對局 Uuid。
     */
    fun clearPlayerAreas(gameId: Uuid)

    /**
     * 通知平台呈現層本局開局座位傳送。只在開局時呼叫一次，之後連莊/過莊開新局不會再次呼叫——風位
     * 輪轉純粹是規則概念，玩家在平台世界裡的物理位置整場對局固定不變。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 依 `TableState.players` 固定座位順序排列的玩家 Uuid 清單。
     */
    fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>)

    /**
     * 通知平台呈現層某玩家的牌河需要更新為目前狀態。
     *
     * 呼叫時機：該玩家捨牌後，或該玩家先前的捨牌被吃/碰/槓走、使牌河紀錄的 `isTaken` 狀態改變時
     * （即使沒有新增捨牌，側身標記也可能因此位移，需要重新呈現）。
     *
     * @param gameId 對局 Uuid。
     * @param seatIndex 牌河所屬玩家在 `TableState.players` 的固定座位 index。
     * @param discardTileIds 這位玩家目前牌河所有紀錄的牌 Uuid，依捨牌順序排列——順序本身決定牌河
     * 排列位置，呼叫端不需要另外傳遞位置索引。
     * @param sidewaysMarkedTileId 這位玩家牌河中應側身呈現的牌 Uuid；`null` 代表沒有任何一張需要
     * 側身（例如非立直規則、或立直牌已被鳴走且尚無下一張捨牌）。刻意用泛用的「側身標記」措辭而非
     * 「立直」，讓這個介面本身維持規則無關。
     */
    fun publishDiscardPileUpdated(gameId: Uuid, seatIndex: Int, discardTileIds: List<Uuid>, sidewaysMarkedTileId: Uuid?)
}
