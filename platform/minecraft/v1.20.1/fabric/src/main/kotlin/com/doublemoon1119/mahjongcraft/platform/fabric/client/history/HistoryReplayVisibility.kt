package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

/**
 * 牌譜回放場景中各項顯示的設定，在同一次牌譜瀏覽中沿用，換局、換一場也不重設。
 *
 * 人物與玩家資訊依玩家的初始座位記錄；沒有改過的項目使用預設值：人物自動、本局資訊與玩家資訊隱藏、桌上物件顯示。
 */
internal class HistoryReplayVisibility {
    /** 改過的人物顯示方式，依初始座位。 */
    private val figures = mutableMapOf<Int, HistoryReplayVisibilityOption>()

    /** 改過的玩家資訊顯示與否，依初始座位。 */
    private val playerInfo = mutableMapOf<Int, Boolean>()

    /** 是否畫出桌子中央上方的本局資訊。 */
    var roundInfo: Boolean = DEFAULT_ROUND_INFO

    /** 是否畫出桌上物件。 */
    var tableProps: Boolean = DEFAULT_TABLE_PROPS

    /** 是否所有項目都是預設值。 */
    val isDefault: Boolean
        get() = figures.isEmpty() && playerInfo.isEmpty() && roundInfo == DEFAULT_ROUND_INFO && tableProps == DEFAULT_TABLE_PROPS

    /**
     * 一家人物的顯示方式。
     *
     * @param seat 初始座位。
     * @return 顯示方式。
     */
    fun figure(seat: Int): HistoryReplayVisibilityOption = figures[seat] ?: DEFAULT_FIGURE

    /**
     * 設定一家人物的顯示方式。
     *
     * @param seat 初始座位。
     * @param visibility 顯示方式。
     */
    fun setFigure(seat: Int, visibility: HistoryReplayVisibilityOption) {
        if (visibility == DEFAULT_FIGURE) figures.remove(seat) else figures[seat] = visibility
    }

    /**
     * 一家的玩家資訊是否畫出。
     *
     * @param seat 初始座位。
     * @return 畫出時為 true。
     */
    fun playerInfo(seat: Int): Boolean = playerInfo[seat] ?: DEFAULT_PLAYER_INFO

    /**
     * 設定一家的玩家資訊是否畫出。
     *
     * @param seat 初始座位。
     * @param shown 是否畫出。
     */
    fun setPlayerInfo(seat: Int, shown: Boolean) {
        if (shown == DEFAULT_PLAYER_INFO) playerInfo.remove(seat) else playerInfo[seat] = shown
    }

    /** 所有項目恢復預設值。 */
    fun reset() {
        figures.clear()
        playerInfo.clear()
        roundInfo = DEFAULT_ROUND_INFO
        tableProps = DEFAULT_TABLE_PROPS
    }

    /** 各項目的預設值。 */
    companion object {
        /** 人物的預設顯示方式。 */
        val DEFAULT_FIGURE: HistoryReplayVisibilityOption = HistoryReplayVisibilityOption.AUTO

        /** 本局資訊預設不畫。 */
        const val DEFAULT_ROUND_INFO: Boolean = false

        /** 玩家資訊預設不畫。 */
        const val DEFAULT_PLAYER_INFO: Boolean = false

        /** 桌上物件預設畫出。 */
        const val DEFAULT_TABLE_PROPS: Boolean = true
    }
}
