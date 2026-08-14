package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import net.minecraft.util.math.BlockPos

/**
 * `/mahjongcraft room join|leave` 的桌子座標引數格式：`x_y_z`（例如 `123_64_-456`）。
 *
 * 這個引數永遠由 Tab 補全帶入完整字串，不是給玩家手動輸入座標的自由格式；用底線分隔而非逗號，是
 * 因為 Brigadier 未加引號的字串引數不允許逗號，底線則不受限制，可以維持「一次 Tab 補全整串帶入」。
 */
internal object TableCoordinateArgument {
    /** 將方塊座標格式化成 Tab 補全與指令引數共用的字串表示法。 */
    fun format(pos: BlockPos): String = "${pos.x}_${pos.y}_${pos.z}"

    /** 將 [format] 產生的字串解析回方塊座標；格式不符時回傳 `null`。 */
    fun parse(raw: String): BlockPos? {
        val parts = raw.split("_")
        if (parts.size != COORDINATE_COMPONENT_COUNT) return null
        val (x, y, z) = parts
        val blockX = x.toIntOrNull() ?: return null
        val blockY = y.toIntOrNull() ?: return null
        val blockZ = z.toIntOrNull() ?: return null
        return BlockPos(blockX, blockY, blockZ)
    }

    private const val COORDINATE_COMPONENT_COUNT = 3
}
