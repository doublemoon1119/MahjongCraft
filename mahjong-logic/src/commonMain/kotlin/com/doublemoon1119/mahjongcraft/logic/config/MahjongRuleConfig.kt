package com.doublemoon1119.mahjongcraft.logic.config

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 定義麻將遊戲最基礎的物理配置介面。
 *
 * 此介面僅包含所有麻將規則通用的物理參數，如手牌張數與牌組構成。
 */
interface MahjongRuleConfig {
    /** 初始手牌張數（不含摸牌）。例如日本麻將為 13，台灣麻將為 16。 */
    val initialHandSize: Int

    /** 牌山結束時需保留在場上不被使用的「王牌」張數。 */
    val deadTileCount: Int

    /** 該規則對應的積分配置。 */
    val scoreConfig: ScoreConfig

    /** 該規則對應的對局長度配置。 */
    val gameLength: GameLength

    /**
     * 最小胡牌番數或台數限制（翻縛）。
     * 日本麻將通常為 1，台灣麻將通常為 0。
     */
    val minimumWinConstraint: Int

    /**
     * 該規則要求的最小玩家人數。
     * */
    val minPlayers: Int

    /**
     * 該規則允許的最大玩家人數。
     * */
    val maxPlayers: Int

    /**
     * 一炮多響（同一張捨牌同時被多位玩家榮和）時的結算方式。
     */
    val multiRonPolicy: MultiRonPolicy

    /**
     * 是否公開暗槓（[MeldType.CLOSED_KAN]）的牌面給本人以外的觀察者。
     *
     * 日本麻將等規則暗槓身份仍算公開（僅實際擺放時把兩端牌翻蓋，不影響身份判定）；台灣麻將等規則
     * 暗槓完全不公開，直到自摸或流局才揭露。其他副露種類（吃／碰／明槓／加槓）本身就是公開宣告的
     * 動作，不受此欄位影響，恆為可見。
     */
    val revealsClosedKanTiles: Boolean
}

/**
 * 驗證規則配置的基本不變量。
 *
 * 供各規則配置的實作類別於建構時（`init` 區塊）呼叫，確保配置數值落在合理範圍內，
 * 避免非法數值（例如來自反序列化的網路封包或存檔）在建構當下就未被攔截，
 * 進而在後續的房間人數判斷（如 `com.doublemoon1119.mahjongcraft.flow.common.room.model.Room`）中產生不可預期的行為。
 *
 * @throws IllegalArgumentException 當任一數值不符合基本不變量時拋出。
 */
fun MahjongRuleConfig.validate() {
    require(minPlayers >= 1) { "minPlayers($minPlayers) must be at least 1" }
    require(maxPlayers >= minPlayers) { "maxPlayers($maxPlayers) must not be less than minPlayers($minPlayers)" }
    require(initialHandSize > 0) { "initialHandSize($initialHandSize) must be a positive integer" }
    require(deadTileCount >= 0) { "deadTileCount($deadTileCount) must not be negative" }
    require(minimumWinConstraint >= 0) { "minimumWinConstraint($minimumWinConstraint) must not be negative" }
}

/**
 * 配牌時每批最多同時抓取幾張牌——真實麻將不分玩法，配牌動作普遍是一次抓兩墩（4 張），重複抓取
 * 直到湊滿 [MahjongRuleConfig.initialHandSize]，最後一批不足 4 張時只抓剩下的張數（例如 13 張是
 * `[4, 4, 4, 1]`，16 張是 `[4, 4, 4, 4]`）。各批總和恆等於 [MahjongRuleConfig.initialHandSize]。
 *
 * 純函式而非 [MahjongRuleModule] 的
 * 可覆寫方法：這是通行慣例，沒有任何規則需要客製化抓取節奏，做成 virtual method 只是不必要的間接層。
 *
 * @return 依序播放的批次大小列表。
 */
fun MahjongRuleConfig.dealBatchSizes(): List<Int> {
    val batches = mutableListOf<Int>()
    var remaining = initialHandSize
    while (remaining > 0) {
        val take = minOf(DEAL_BATCH_MAX_SIZE, remaining)
        batches += take
        remaining -= take
    }
    return batches
}

/** 配牌時每批最多同時抓取的張數（兩墩），見 [dealBatchSizes] KDoc。 */
private const val DEAL_BATCH_MAX_SIZE = 4
