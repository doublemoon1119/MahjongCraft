package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

/**
 * 一幀世界繪製期間暫存的面板，等整個世界畫完再依距離由遠到近畫出。
 *
 * 不在世界繪製期間時不收集，呼叫端改為立刻畫，例如牌譜回放的畫面場景。
 */
internal class WorldPanelQueue {
    /** 已收集的面板與它離鏡頭距離的平方。 */
    private val pending = mutableListOf<Pair<Double, () -> Unit>>()

    /** 是否正在世界繪製期間收集面板。 */
    var collecting: Boolean = false
        private set

    /** 一幀的世界繪製開始：丟掉上一幀殘留的面板並開始收集。 */
    fun beginFrame() {
        pending.clear()
        collecting = true
    }

    /**
     * 收集一個面板。
     *
     * @param distanceSquared 面板離鏡頭距離的平方。
     * @param draw 畫出面板的動作。
     * @return 有收集時為 true；不在世界繪製期間時為 false，呼叫端應立刻畫。
     */
    fun submit(distanceSquared: Double, draw: () -> Unit): Boolean {
        if (!collecting) return false
        pending += distanceSquared to draw
        return true
    }

    /**
     * 結束收集並取出這一幀的面板；之後到下一幀開始前不再收集。
     *
     * @return 由遠到近排列的繪製動作；沒有面板時為空。
     */
    fun drain(): List<() -> Unit> {
        collecting = false
        val ordered = pending.sortedByDescending { it.first }.map { it.second }
        pending.clear()
        return ordered
    }
}
