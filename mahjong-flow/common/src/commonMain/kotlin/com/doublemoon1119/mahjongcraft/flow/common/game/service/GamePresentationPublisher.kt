package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

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
     */
    fun publishWallStructure(
        gameId: Uuid,
        structure: Map<Uuid, TileWallPosition>,
        dealerSeatIndex: Int,
        deadWallTileIds: Set<Uuid>,
        diceCount: Int,
    )

    /**
     * 通知平台呈現層本局初始手牌分配。
     *
     * 跟 [publishWallStructure] 的王牌分離同理，手牌落地要等擲骰動畫播完才觸發，不能在骰子還在動畫
     * 時就直接讓手牌出現在玩家面前——這三件事（王牌分離、手牌分配、寶牌指示器翻開）體感上應該同時
     * 發生，才符合「建牌 → 擲骰開門 → 分牌」的真實節奏。[diceCount] 的用途跟 [publishWallStructure]
     * 相同，供平台實作換算延遲時長。
     *
     * @param gameId 對局 Uuid。
     * @param handsBySeatIndex 依 `TableState.players` 固定座位 index 分組的初始手牌，每組依發牌順序
     *        排列，鍵為 [IdentifiedTile.id]；空 map 代表這局結束，只需要清除舊牌。
     * @param diceCount 本次開局擲骰的骰子數量；未搭配擲骰的呼叫可傳 `0`。
     */
    fun publishHandTiles(gameId: Uuid, handsBySeatIndex: Map<Int, List<Uuid>>, diceCount: Int)

    /**
     * 通知平台呈現層本局開局座位傳送。只在開局時呼叫一次，之後連莊/過莊開新局不會再次呼叫——風位
     * 輪轉純粹是規則概念，玩家在平台世界裡的物理位置整場對局固定不變。
     *
     * @param gameId 對局 Uuid。
     * @param seatedPlayerIds 依 `TableState.players` 固定座位順序排列的玩家 Uuid 清單。
     */
    fun publishGameStarted(gameId: Uuid, seatedPlayerIds: List<Uuid>)

    /**
     * 通知平台呈現層某玩家摸到的牌需要移動到手牌旁的摸牌位置。
     *
     * 只在真正摸牌時呼叫；捨牌不會呼叫這個方法清除摸牌位——捨牌時原本佔用摸牌位的那張牌一定會有
     * 新去處（併入立牌列表，或本身就是被丟的那張移去牌河），呼叫端改呼叫 [publishHandTiles]（重新
     * 排列立牌列）與 [publishDiscardPileUpdated]（把牌移去牌河），不會有「entity 留在摸牌位沒人管」
     * 的情況。
     *
     * @param gameId 對局 Uuid。
     * @param seatIndex 摸牌玩家在 `TableState.players` 的固定座位 index。
     * @param standingTileCount 這位玩家目前立牌張數（不含這張剛摸到的牌），供平台實作換算摸牌位相對
     * 立牌列尾端的偏移，不需要重新定位既有立牌。
     * @param drawnTileId 剛摸到那張牌的 Uuid；為 `null` 代表清除既有摸牌位呈現（例如對局／該局結束
     * 時的收尾呼叫）。
     */
    fun publishTileDrawn(gameId: Uuid, seatIndex: Int, standingTileCount: Int, drawnTileId: Uuid?)

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
