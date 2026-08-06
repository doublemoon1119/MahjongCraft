package com.doublemoon1119.mahjongcraft.logic.config

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
     * 是否允許旁觀
     *
     * 在遊戲外的玩家能否看到遊戲內玩家的手牌，在牌河或者副露的牌則不在此限
     * */
    val isSpectateAllowed: Boolean

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
}

/**
 * 驗證規則配置的基本不變量。
 *
 * 供各規則配置的實作類別於建構時（`init` 區塊）呼叫，確保配置數值落在合理範圍內，
 * 避免非法數值（例如來自反序列化的網路封包或存檔）在建構當下就未被攔截，
 * 進而在後續的房間人數判斷（如 [com.doublemoon1119.mahjongcraft.flow.common.room.model.Room]）中產生不可預期的行為。
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
