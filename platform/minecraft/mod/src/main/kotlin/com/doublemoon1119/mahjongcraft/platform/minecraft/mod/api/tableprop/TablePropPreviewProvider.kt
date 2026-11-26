package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.tableprop

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity

/** 客戶端主執行緒的桌上物件預覽工廠，例如牌譜回放畫出當時桌上的點棒。不得搜尋或接管世界實體。 */
fun interface TablePropPreviewProvider {
    /**
     * 只依物件的公開內容建立不加入世界的實體，外觀與對局中由同一種類生成的實體相同。
     *
     * 框架持有建立的實體，不再需要時呼叫 [Entity.discard]。位置與朝向由框架在繪製時決定。
     *
     * @param world 提供渲染所需的客戶端世界，不授權加入實體或載入區塊。
     * @param target 物件的身分、所屬規則與內容。座標不代表任何世界位置。
     * @return 不在世界中的新實體。內容無法呈現時為 null。
     */
    fun create(world: ClientLevel, target: TablePropTarget): Entity?
}
