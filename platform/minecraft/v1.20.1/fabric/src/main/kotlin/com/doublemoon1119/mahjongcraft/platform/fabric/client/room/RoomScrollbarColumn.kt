package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

/**
 * 房間畫面右側 scrollbar 共用的水平座標。
 *
 * 設定欄位、成員卡片 grid 與對局資訊三處 scrollbar 畫在同一條垂直帶上，命中判定範圍也比繪製範圍稍寬，
 * 讓游標不必精確壓在軌道上就能拖曳。
 */
internal data class RoomScrollbarColumn(
    /** 目前視窗寬度。 */
    val windowWidth: Int,
) {
    /** 軌道繪製左界。 */
    val trackLeft: Int = windowWidth - 14

    /** 軌道繪製右界。 */
    val trackRight: Int = windowWidth - 9

    /** 命中判定左界。 */
    val hitLeft: Int = windowWidth - 18

    /** 命中判定右界。 */
    val hitRight: Int = windowWidth - 5

    /** 游標水平位置是否落在可拖曳範圍內。 */
    fun containsHorizontally(mouseX: Double): Boolean = mouseX >= hitLeft && mouseX <= hitRight
}
