package com.doublemoon1119.mahjongcraft.platform.minecraft.ai

/**
 * 管理 AI 策略 key 對應顯示名稱 translation key 的 runtime registry。
 *
 * 比照 [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry] 的開放
 * 註冊表設計：內建與（未來的）第三方策略共用同一套 [register] 流程，不具特權。純粹服務聊天／指令
 * tooltip 等呈現用途，刻意獨立於 [com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry] 之外
 * ——那個介面刻意不讓策略透過參數夾帶額外設定，避免外洩到 `Room`／快照。
 *
 * 這裡只處理 AI 策略；日後若麻將規則等其他可擴充識別碼也需要顯示名稱對照，應該複製這個檔案改型別，
 * 而不是把所有概念塞進同一個通用 registry——不同概念的 key 空間放在一起容易撞名，個別介面能讓型別
 * 替呼叫端防呆。
 */
interface AiStrategyDisplayNameRegistry {
    /** registry 是否已禁止後續註冊。 */
    val isFrozen: Boolean

    /**
     * 註冊一個策略對應的顯示名稱 translation key。
     *
     * @throws IllegalStateException 當 registry 已凍結時拋出。
     * @throws IllegalArgumentException 當 [strategyKey] 已存在對應映射時拋出。
     */
    fun register(strategyKey: String, translationKey: String)

    /** 凍結 registry；後續呼叫 [register] 將失敗。 */
    fun freeze()

    /** 依 [strategyKey] 尋找對應的顯示名稱 translation key；尚未註冊時回傳 null。 */
    fun find(strategyKey: String): String?
}
