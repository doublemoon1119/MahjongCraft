package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.math.min

/**
 * 標準型（若干面子加一組雀頭）手牌的面子、搭子拆法搜尋，雀頭由呼叫端決定。
 *
 * 計數陣列使用 34 格的固定排列：萬子、筒子、條子的 1～9 依序為 0～8、9～17、18～26，字牌為 27～33。
 * 面子與搭子不會跨花色，因此四組牌各自列舉所有可達的（面子數, 搭子數）組合再合併；結果與對整副牌窮舉相同。
 * 數牌可組成刻子、順子、對子、兩面或邊張搭子與嵌張搭子；字牌只有刻子與對子。
 *
 * 每組牌的結果依該組牌型記憶，記憶只存在於這個實例；每個實例只供單次計算使用，不在執行緒間共用。
 *
 * @property targetMelds 和牌所需的面子數（含副露）。
 */
class StandardMeldSearch(private val targetMelds: Int) {
    init {
        require(targetMelds in 1..MAX_TARGET_MELDS) { "Target melds must be in 1..$MAX_TARGET_MELDS" }
    }

    /** 每一維（面子數、搭子數）的格數；超過 [targetMelds] 的數量都記在最後一格。 */
    private val width = targetMelds + 1

    /** 面子數已達上限的所有位元。 */
    private val lastRow = ((1L shl width) - 1) shl (targetMelds * width)

    /** 搭子數已達上限的所有位元。 */
    private val lastColumn = (0 until width).fold(0L) { mask, melds -> mask or bit(melds, targetMelds) }

    /** 每組牌的完整牌型可達的組合，以位元集合表示（見 [bit]）。 */
    private val reachableByGroup = LongTable()

    /**
     * 搜尋目前這組牌時，各搜尋狀態可達的組合，key 為狀態索引（見 [stateOf]）；每搜尋新的一組牌前清空，第一次需要時才建立。
     *
     * 處理到第 i 格時，只有第 i、i+1、i+2 格可能已被取走部分的牌，更後面的格子仍是原樣，
     * 因此以位置與這三格的剩餘張數表示狀態。
     */
    private var reachableByState: LongTable? = null

    /** 搜尋單組牌的狀態；每組牌重新設定後重複使用。 */
    private val groupSearch = GroupSearch()

    /**
     * 立牌中的面子與搭子能讓手牌最接近和牌的程度：`2 × 目標面子數 - 2 × 面子總數 - min(搭子總數, 目標面子數 - 面子總數)`。
     *
     * 面子總數含 [initialMelds] 且最多算到目標面子數；不扣雀頭，由呼叫端在選定雀頭後自行減 1。
     * [initialMelds] 已達目標面子數時直接回傳 `2 × 目標面子數 - 2 × [initialMelds]`。
     *
     * @param counts 34 格的立牌計數；搜尋時暫時取走牌，回傳前還原。
     * @param initialMelds 已副露的面子數。
     * @return 上述公式在所有拆法中的最小值。
     */
    fun meldShanten(counts: IntArray, initialMelds: Int): Int {
        if (initialMelds >= targetMelds) return 2 * targetMelds - 2 * initialMelds
        requireTileKinds(counts)
        var reachable = reachableInGroup(counts, start = 0, size = SUIT_SIZE, connected = true)
        reachable = combine(reachable, reachableInGroup(counts, start = SUIT_SIZE, size = SUIT_SIZE, connected = true))
        reachable = combine(reachable, reachableInGroup(counts, start = 2 * SUIT_SIZE, size = SUIT_SIZE, connected = true))
        reachable = combine(reachable, reachableInGroup(counts, start = HONOR_START, size = HONOR_KINDS, connected = false))
        var best = Int.MAX_VALUE
        for (melds in 0 until width) {
            for (partials in 0 until width) {
                if ((reachable and bit(melds, partials)) == 0L) continue
                val totalMelds = min(initialMelds + melds, targetMelds)
                val usefulPartials = min(partials, targetMelds - totalMelds)
                best = min(best, 2 * targetMelds - 2 * totalMelds - usefulPartials)
            }
        }
        return best
    }

    /**
     * 立牌最多能組成的面子數，允許有不屬於任何面子的剩餘牌；超過目標面子數時以目標面子數計。
     *
     * @param counts 34 格的立牌計數；搜尋時暫時取走牌，回傳前還原。
     * @return 面子數。
     */
    fun maxMelds(counts: IntArray): Int {
        requireTileKinds(counts)
        val melds = maxMelds(reachableInGroup(counts, start = 0, size = SUIT_SIZE, connected = true)) +
            maxMelds(reachableInGroup(counts, start = SUIT_SIZE, size = SUIT_SIZE, connected = true)) +
            maxMelds(reachableInGroup(counts, start = 2 * SUIT_SIZE, size = SUIT_SIZE, connected = true)) +
            maxMelds(reachableInGroup(counts, start = HONOR_START, size = HONOR_KINDS, connected = false))
        return min(melds, targetMelds)
    }

    /** 確認 [counts] 是 34 格的計數陣列。 */
    private fun requireTileKinds(counts: IntArray) {
        require(counts.size == TILE_KINDS) { "Tile counts must have $TILE_KINDS entries" }
    }

    /** 一組可達組合中最多的面子數。 */
    private fun maxMelds(reachable: Long): Int {
        for (melds in targetMelds downTo 1) {
            if ((reachable and rowMask(melds)) != 0L) return melds
        }
        return 0
    }

    /**
     * 一組牌（一個數牌花色或全部字牌）可達的組合；同一個牌型只搜尋一次。
     *
     * @param counts 34 格的立牌計數。
     * @param start 這組牌的第一格。
     * @param size 這組牌的格數。
     * @param connected 是否為數牌（可組成順子與順子搭子）。
     */
    private fun reachableInGroup(counts: IntArray, start: Int, size: Int, connected: Boolean): Long {
        var key = if (connected) 1L else 0L
        var tiles = 0
        var withinStateCounts = true
        for (position in start until start + size) {
            require(counts[position] in 0 until GROUP_COUNT_LIMIT) { "Tile count must be in 0 until $GROUP_COUNT_LIMIT" }
            if (counts[position] >= STATE_COUNTS) withinStateCounts = false
            tiles += counts[position]
            key = (key shl 4) or counts[position].toLong()
        }
        val known = reachableByGroup[key]
        if (known != 0L) return known
        val states = if (withinStateCounts && tiles >= STATE_MEMO_MIN_TILES) {
            (reachableByState ?: LongTable().also { reachableByState = it }).apply { clear() }
        } else {
            null
        }
        val reachable = groupSearch.search(counts, start, start + size, connected, states)
        reachableByGroup[key] = reachable
        return reachable
    }

    /** 在計數陣列的一段之間搜尋一組牌；取走的牌在每次遞迴返回時還原。 */
    private inner class GroupSearch {
        /** 正在搜尋的計數陣列（與呼叫端共用，搜尋中暫時取走牌）。 */
        private var counts = IntArray(0)

        /** 這組牌的第一格。 */
        private var start = 0

        /** 這組牌最後一格的下一格。 */
        private var end = 0

        /** 是否為數牌。 */
        private var connected = false

        /** 記憶搜尋狀態的表；不記憶時為 null。 */
        private var states: LongTable? = null

        /**
         * 搜尋 [counts] 的 [start] 到 [end]（不含）這組牌可達的組合。
         *
         * @param connected 是否為數牌（可組成順子與順子搭子）。
         * @param states 記憶搜尋狀態的空表；牌少到直接搜尋較快，或某一格超過 4 張時為 null。
         */
        fun search(counts: IntArray, start: Int, end: Int, connected: Boolean, states: LongTable?): Long {
            this.counts = counts
            this.start = start
            this.end = end
            this.connected = connected
            this.states = states
            return reachableFrom(start)
        }

        /** 從 [index] 起（較小的位置已處理完）可達的組合；在同一個位置可以連續取多組牌，或放棄這個位置剩下的牌改看下一個位置。 */
        private fun reachableFrom(index: Int): Long {
            if (index == end) return bit(0, 0)
            if (counts[index] == 0) return reachableFrom(index + 1)
            val state = if (states != null) stateOf(index).toLong() else NO_STATE
            if (state != NO_STATE) {
                val known = checkNotNull(states)[state]
                if (known != 0L) return known
            }
            var reachable = reachableFrom(index + 1)
            if (counts[index] >= 3) reachable = reachable or withTaken(index, offsets = TRIPLET, meld = true)
            if (counts[index] >= 2) reachable = reachable or withTaken(index, offsets = PAIR, meld = false)
            if (connected && index + 1 < end && counts[index + 1] > 0) {
                if (index + 2 < end && counts[index + 2] > 0) reachable = reachable or withTaken(index, offsets = SEQUENCE, meld = true)
                reachable = reachable or withTaken(index, offsets = ADJACENT, meld = false)
            }
            if (connected && index + 2 < end && counts[index + 2] > 0) {
                reachable = reachable or withTaken(index, offsets = GAPPED, meld = false)
            }
            if (state != NO_STATE) checkNotNull(states)[state] = reachable
            return reachable
        }

        /**
         * 從 [index] 取出 [offsets] 指定的牌組成一組後，剩下的牌可達的組合加上這一組。
         *
         * @param offsets 相對於 [index] 要各取走一張的位置。
         * @param meld 這一組是面子（true）還是搭子（false）。
         */
        private fun withTaken(index: Int, offsets: IntArray, meld: Boolean): Long {
            offsets.forEach { counts[index + it]-- }
            val rest = reachableFrom(index)
            offsets.forEach { counts[index + it]++ }
            return if (meld) addMeld(rest) else addPartial(rest)
        }

        /** 位置 [index] 與其後兩格的剩餘張數組成的狀態索引；超出這組牌的格子以 0 計。 */
        private fun stateOf(index: Int): Int {
            val next = if (index + 1 < end) counts[index + 1] else 0
            val afterNext = if (index + 2 < end) counts[index + 2] else 0
            return (((index - start) * STATE_COUNTS + counts[index]) * STATE_COUNTS + next) * STATE_COUNTS + afterNext
        }
    }

    /**
     * 兩組獨立牌的可達組合合併：任取 [first] 的一個組合與 [second] 的一個組合相加，超過上限的仍記在最後一格。
     */
    private fun combine(first: Long, second: Long): Long {
        var combined = 0L
        var withMelds = second
        for (melds in 0 until width) {
            var shifted = withMelds
            for (partials in 0 until width) {
                if ((first and bit(melds, partials)) != 0L) combined = combined or shifted
                shifted = addPartial(shifted)
            }
            withMelds = addMeld(withMelds)
        }
        return combined
    }

    /** 每個組合多一組面子；已在最後一格的仍留在最後一格。 */
    private fun addMeld(reachable: Long): Long = ((reachable and lastRow.inv()) shl width) or (reachable and lastRow)

    /** 每個組合多一組搭子；已在最後一格的仍留在最後一格。 */
    private fun addPartial(reachable: Long): Long = ((reachable and lastColumn.inv()) shl 1) or (reachable and lastColumn)

    /** （面子數, 搭子數）在位元集合中的位元。 */
    private fun bit(melds: Int, partials: Int): Long = 1L shl (melds * width + partials)

    /** 面子數為 [melds] 的所有位元。 */
    private fun rowMask(melds: Int): Long = ((1L shl width) - 1) shl (melds * width)

    /**
     * 以非負 key 查可達組合的開放定址雜湊表，避免每次查詢配置物件；查不到時回傳 0（任何牌型與狀態至少可達「沒有面子與搭子」）。
     */
    private class LongTable {
        /** 各格的 key；[NO_KEY] 表示空格。容量固定為 2 的次方。 */
        private var keys = LongArray(INITIAL_CAPACITY) { NO_KEY }

        /** 與 [keys] 同一格的值。 */
        private var values = LongArray(INITIAL_CAPACITY)

        /** 已存放的筆數。 */
        private var size = 0

        /**
         * 查詢 [key] 的值。
         *
         * @param key 非負的 key。
         * @return 存放的值；不存在時為 0。
         */
        operator fun get(key: Long): Long {
            var slot = slotOf(key, keys.size)
            while (true) {
                val stored = keys[slot]
                if (stored == key) return values[slot]
                if (stored == NO_KEY) return 0L
                slot = (slot + 1) and (keys.size - 1)
            }
        }

        /** 移除所有資料，保留容量。 */
        fun clear() {
            if (size == 0) return
            keys.fill(NO_KEY)
            size = 0
        }

        /**
         * 寫入或覆蓋 [key] 的值；存放的筆數超過容量一半時先擴充。
         *
         * @param key 非負的 key。
         * @param value 要存放的值，不可為 0（0 代表不存在）。
         */
        operator fun set(key: Long, value: Long) {
            if ((size + 1) * 2 > keys.size) grow()
            if (insert(keys, values, key, value)) size++
        }

        /** 容量加倍，並把現有資料重新放進新的格子。 */
        private fun grow() {
            val newKeys = LongArray(keys.size * 2) { NO_KEY }
            val newValues = LongArray(keys.size * 2)
            keys.indices.forEach { slot -> if (keys[slot] != NO_KEY) insert(newKeys, newValues, keys[slot], values[slot]) }
            keys = newKeys
            values = newValues
        }

        /**
         * 在 [keys]／[values] 中寫入一筆，遇到已有其他 key 的格子就往下一格找。
         *
         * @return [key] 原本是否不存在。
         */
        private fun insert(keys: LongArray, values: LongArray, key: Long, value: Long): Boolean {
            var slot = slotOf(key, keys.size)
            while (keys[slot] != NO_KEY && keys[slot] != key) slot = (slot + 1) and (keys.size - 1)
            val added = keys[slot] == NO_KEY
            keys[slot] = key
            values[slot] = value
            return added
        }

        /**
         * [key] 在容量為 [capacity]（2 的次方）的表中的起始格：乘上常數打散後取最高的幾個位元。
         */
        private fun slotOf(key: Long, capacity: Int): Int {
            val mixed = key * HASH_MULTIPLIER
            return (mixed ushr (Long.SIZE_BITS - capacity.countTrailingZeroBits())).toInt()
        }

        private companion object {
            /** 初始容量；必須是 2 的次方。 */
            const val INITIAL_CAPACITY = 16

            /** key 都不是負數，不會等於這個值。 */
            const val NO_KEY = Long.MIN_VALUE

            /** 打散 key 用的 64 位元黃金比例常數。 */
            const val HASH_MULTIPLIER = -7046029254386353131L
        }
    }

    private companion object {
        /** 位元集合使用 Long，每一維最多 8 格。 */
        const val MAX_TARGET_MELDS = 7

        /** 計數陣列的格數。 */
        const val TILE_KINDS = 34

        /** 每個數牌花色的格數。 */
        const val SUIT_SIZE = 9

        /** 字牌的第一格。 */
        const val HONOR_START = 27

        /** 字牌的格數。 */
        const val HONOR_KINDS = 7

        /** 每組牌型的 key 每格佔 4 位元。 */
        const val GROUP_COUNT_LIMIT = 16

        /** 搜尋狀態中每格的張數上限（0～4）。 */
        const val STATE_COUNTS = 5

        /** 不記憶搜尋狀態時的狀態值。 */
        const val NO_STATE = -1L

        /** 一組牌至少有這麼多張時才記憶搜尋狀態；牌更少時直接搜尋較快。 */
        const val STATE_MEMO_MIN_TILES = 6

        /** 刻子：從同一格取三張。以下各組都是相對於起始格要取走的位置。 */
        val TRIPLET = intArrayOf(0, 0, 0)

        /** 順子：連續三格各取一張。 */
        val SEQUENCE = intArrayOf(0, 1, 2)

        /** 對子搭子：從同一格取兩張。 */
        val PAIR = intArrayOf(0, 0)

        /** 兩面或邊張搭子：相鄰兩格各取一張。 */
        val ADJACENT = intArrayOf(0, 1)

        /** 嵌張搭子：隔一格的兩格各取一張。 */
        val GAPPED = intArrayOf(0, 2)
    }
}
