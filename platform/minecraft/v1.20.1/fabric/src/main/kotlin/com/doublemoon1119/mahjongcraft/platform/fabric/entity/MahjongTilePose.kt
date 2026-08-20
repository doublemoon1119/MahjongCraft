package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/** 麻將牌相對於所在表面的三種呈現與碰撞姿態。 */
enum class MahjongTilePose {
    /** 牌底接觸表面，牌面接近垂直。 */
    STANDING,

    /** 牌平放且牌面朝上。 */
    FACE_UP,

    /** 牌平放且牌背朝上。 */
    FACE_DOWN,
    ;

    /** 取得固定循環順序中的下一個姿態。 */
    fun next(): MahjongTilePose = entries[(ordinal + 1) % entries.size]

    /**
     * 轉成以局部 X 軸為基準的 renderer 旋轉角度；牌牆生成掉落動畫（
     * `FabricMahjongTileWallPresenter.present`）也需要這個值當作 [MahjongTileEntity.startMotionAnimation]
     * 的起訖姿態旋轉角，因此定義在姿態本身而不是 renderer 專屬檔案，讓 server 端呼叫端不需要依賴
     * client 端渲染檔案。
     */
    val rotationDegrees: Float
        get() = when (this) {
            STANDING -> 0.0f
            FACE_UP -> 90.0f
            FACE_DOWN -> -90.0f
        }

    companion object {
        /** 從持久化或 tracked ordinal 安全還原姿態，非法值回退為 [STANDING]。 */
        fun fromOrdinalOrDefault(ordinal: Int): MahjongTilePose = entries.getOrNull(ordinal) ?: STANDING

        /** 從持久化名稱安全還原姿態，非法值回退為 [STANDING]。 */
        fun fromNameOrDefault(name: String?): MahjongTilePose = entries.firstOrNull { it.name == name } ?: STANDING
    }
}
