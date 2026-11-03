package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import net.minecraft.entity.Entity

/**
 * 供客戶端直接繪製或擷取頭像的獨立預覽物件，不加入世界、不執行 tick 或原生 AI。
 * 框架可有界持有此模型；淘汰、換世界、斷線或資源重載時呼叫 close，不持有世界本人。
 */
interface FabricAiCharacterPreviewHandle {
    /** 工廠新建的獨立預覽實體，不可回傳世界中已存在的實體。 */
    val entity: Entity

    /** 圖片中要當頭像的範圍；null 表示使用整張模型圖片。 */
    val portraitRegion: FabricAiCharacterPreviewRegion? get() = null

    /** 釋放工廠擁有的暫態資源；不得移除或改動世界中的其他實體。 */
    fun close()
}
