package com.doublemoon1119.mahjongcraft.platform.minecraft.ruler

/** 放在方塊上的牌尺的擺法。 */
enum class MahjongRulerPosture {
    /** 寬面朝下平放；剛放下時的擺法。 */
    FLAT,

    /** 窄邊朝下豎著立起。 */
    UPRIGHT,
    ;

    /** @return 切換後的擺法。 */
    fun next(): MahjongRulerPosture = entries[(ordinal + 1) % entries.size]

    companion object {
        /**
         * 依列舉名稱取得擺法。
         *
         * @param name 列舉名稱，例如 `FLAT`。
         * @return 對應的擺法；名稱不存在時為平放。
         */
        fun fromNameOrDefault(name: String): MahjongRulerPosture = entries.firstOrNull { it.name == name } ?: FLAT
    }
}
