package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character

import net.minecraft.world.entity.Entity

/**
 * 供客戶端直接繪製或擷取頭像的獨立預覽物件，不加入世界、不執行 tick 或原生 AI。
 * 框架可有界持有此模型。淘汰、換世界、斷線或資源重載時呼叫 close，不持有世界本人。
 */
interface AiCharacterPreviewHandle {
    /**
     * 工廠新建的獨立預覽實體，不可回傳世界中已存在的實體。
     *
     * - 場景要畫坐姿時，讓這個實體乘坐一個座位實體，並把它放在坐著時腳底的高度；畫完立即離開座位。
     * - 實體應依自己是否正在乘坐決定坐姿，與原版玩家相同。預覽實體不執行 tick，只在 tick 裡更新的坐姿不會套用。
     * - 使用原版人形或玩家模型的實體會自動坐下；自訂姿勢的實體要在開始與結束乘坐時自行切換。
     */
    val entity: Entity

    /** 圖片中要當頭像的範圍。null 表示使用整張模型圖片。 */
    val portraitRegion: AiCharacterPreviewRegion? get() = null

    /** 釋放工廠擁有的暫態資源。不得移除或改動世界中的其他實體。 */
    fun close()
}
