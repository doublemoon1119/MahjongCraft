package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 測試用欄位變更；外層不存在表示未變動，欄位值本身可為 null。
 *
 * @property value 變更後的欄位值。
 */
internal data class TestChanged<T>(val value: T)

/**
 * 測試用有序列表差異，保留共同前綴與後綴，只替換中間片段。
 *
 * @property prefixCount 保留的前綴元素數量。
 * @property removedCount 自舊列表中移除的中間元素數量。
 * @property inserted 插入至中間位置的新元素。
 * @property suffixCount 保留的後綴元素數量。
 */
internal data class TestListSplice<T>(
    val prefixCount: Int,
    val removedCount: Int,
    val inserted: List<T>,
    val suffixCount: Int,
) {
    /** 將中間片段差異套用至舊列表並驗證原長度。 */
    fun applyTo(before: List<T>): List<T> {
        require(prefixCount + removedCount + suffixCount == before.size)
        return before.take(prefixCount) + inserted + before.takeLast(suffixCount)
    }

    /** 建立兩份有序列表之間的最短共同前後綴差異。 */
    companion object {
        /** 列表內容相同時回傳 null，否則回傳可還原的新片段。 */
        fun <T> between(before: List<T>, after: List<T>): TestListSplice<T>? {
            if (before == after) return null
            val prefix = before.zip(after).takeWhile { (old, new) -> old == new }.size
            val commonLimit = minOf(before.size, after.size) - prefix
            val suffix = (0 until commonLimit).takeWhile { offset ->
                before[before.lastIndex - offset] == after[after.lastIndex - offset]
            }.count()
            return TestListSplice(
                prefixCount = prefix,
                removedCount = before.size - prefix - suffix,
                inserted = after.subList(prefix, after.size - suffix),
                suffixCount = suffix,
            )
        }
    }
}

/** 牌河優先以日麻紀錄片段表示；未支援的規則仍明確保留完整替換值。 */
internal sealed interface TestDiscardDelta {
    /** 將牌河差異套用至前一牌河。 */
    fun applyTo(before: DiscardPile<*>): DiscardPile<*>

    /**
     * 日麻牌河的有序紀錄差異。
     *
     * @property entries 已變更的日麻牌河紀錄片段。
     */
    data class Riichi(val entries: TestListSplice<RiichiDiscardEntry>) : TestDiscardDelta {
        /** 驗證前一牌河類型並套用紀錄片段。 */
        override fun applyTo(before: DiscardPile<*>): DiscardPile<*> {
            require(before is RiichiDiscardPile)
            return RiichiDiscardPile(entries.applyTo(before.entries))
        }
    }

    /**
     * 無專用差異編碼時使用的完整牌河替換。
     *
     * @property pile 變更後的完整牌河。
     */
    data class Replace(val pile: DiscardPile<*>) : TestDiscardDelta {
        /** 以保存的完整牌河取代前一牌河。 */
        override fun applyTo(before: DiscardPile<*>): DiscardPile<*> = pile
    }
}

/**
 * 僅用於驗證玩家狀態可由前一狀態增量還原；此模型尚未是持久化 DTO。
 *
 * @property standing 手牌列表的中間片段變化。
 * @property melds 副露列表的中間片段變化。
 * @property lastDrawn 最後摸入牌的變化。
 * @property discards 牌河變化。
 * @property score 分數變化。
 * @property ruleState 規則專屬玩家狀態變化。
 * @property passedTiles 同巡放過的牌種變化。
 * @property actions 動作紀錄的中間片段變化。
 * @property seatWind 自風變化。
 * @property aiStrategyKey AI 策略標記變化。
 */
internal data class TestPlayerDelta(
    val standing: TestListSplice<IdentifiedTile>? = null,
    val melds: TestListSplice<Meld>? = null,
    val lastDrawn: TestChanged<IdentifiedTile?>? = null,
    val discards: TestDiscardDelta? = null,
    val score: Int? = null,
    val ruleState: TestChanged<PlayerRuleState?>? = null,
    val passedTiles: Set<Tile>? = null,
    val actions: TestListSplice<GameAction>? = null,
    val seatWind: Wind? = null,
    val aiStrategyKey: TestChanged<String?>? = null,
) {
    /** 將所有有變更的玩家欄位套用至前一權威狀態。 */
    fun applyTo(before: MahjongPlayer): MahjongPlayer = before.copy(
        hand = before.hand.copy(
            tiles = standing?.applyTo(before.hand.tiles) ?: before.hand.tiles,
            melds = melds?.applyTo(before.hand.melds) ?: before.hand.melds,
            lastDrawn = if (lastDrawn != null) lastDrawn.value else before.hand.lastDrawn,
        ),
        discardPile = discards?.applyTo(before.discardPile) ?: before.discardPile,
        score = score ?: before.score,
        playerRuleState = if (ruleState != null) ruleState.value else before.playerRuleState,
        passedTilesInRound = passedTiles ?: before.passedTilesInRound,
        actionHistory = actions?.applyTo(before.actionHistory) ?: before.actionHistory,
        seatWind = seatWind ?: before.seatWind,
        aiStrategyKey = if (aiStrategyKey != null) aiStrategyKey.value else before.aiStrategyKey,
    )

    /** 建立同一玩家的狀態差異；玩家身分及初始座位不得改變。 */
    companion object {
        /** 玩家狀態相同時回傳 null，否則回傳可套用的差異。 */
        fun between(before: MahjongPlayer, after: MahjongPlayer): TestPlayerDelta? {
            require(before.id == after.id && before.initialSeatIndex == after.initialSeatIndex)
            if (before == after) return null
            val discards = when {
                before.discardPile == after.discardPile -> null
                before.discardPile is RiichiDiscardPile && after.discardPile is RiichiDiscardPile -> {
                    val oldEntries = (before.discardPile as RiichiDiscardPile).entries
                    val newEntries = (after.discardPile as RiichiDiscardPile).entries
                    val splice = TestListSplice.between(oldEntries, newEntries)
                    checkNotNull(splice)
                    TestDiscardDelta.Riichi(splice)
                }
                else -> TestDiscardDelta.Replace(after.discardPile)
            }
            return TestPlayerDelta(
                standing = TestListSplice.between(before.hand.tiles, after.hand.tiles),
                melds = TestListSplice.between(before.hand.melds, after.hand.melds),
                lastDrawn = if (after.hand.lastDrawn != before.hand.lastDrawn) TestChanged(after.hand.lastDrawn) else null,
                discards = discards,
                score = after.score.takeIf { it != before.score },
                ruleState = if (after.playerRuleState != before.playerRuleState) TestChanged(after.playerRuleState) else null,
                passedTiles = after.passedTilesInRound.takeIf { it != before.passedTilesInRound },
                actions = TestListSplice.between(before.actionHistory, after.actionHistory),
                seatWind = after.seatWind.takeIf { it != before.seatWind },
                aiStrategyKey = if (after.aiStrategyKey != before.aiStrategyKey) TestChanged(after.aiStrategyKey) else null,
            )
        }
    }
}
