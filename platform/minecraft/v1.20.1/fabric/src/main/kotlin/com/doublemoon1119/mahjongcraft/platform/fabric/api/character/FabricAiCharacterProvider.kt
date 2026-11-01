package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

/** 在主執行緒取得一個未開始接管的角色能力，不生成角色或強制載入區塊。 */
fun interface FabricAiCharacterProvider {
    /**
     * 取得自有新角色或借用既有角色。
     *
     * @param context 本場公開角色資料及固定席位。
     * @return 可嘗試接管的角色；暫時無法提供時為 null。
     */
    fun acquire(context: FabricAiCharacterContext): FabricAiCharacterHandle?
}
