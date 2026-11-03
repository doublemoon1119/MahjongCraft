package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import net.minecraft.client.world.ClientWorld

/** 客戶端主執行緒的模型預覽工廠，不得搜尋或接管世界實體。 */
fun interface FabricAiCharacterPreviewProvider {
    /**
     * 只依已擷取的公開描述建立獨立模型，不能讀取目前世界角色來決定歷史外觀。
     *
     * @param world 提供渲染所需的客戶端世界，不授權加入實體或載入區塊。
     * @param character 含完整參數的當場公開描述。
     * @return 可直接繪製及截取頭像的獨立物件；能力暫時不可用時為 null。
     */
    fun create(world: ClientWorld, character: AiCharacterDefinition): FabricAiCharacterPreviewHandle?
}
