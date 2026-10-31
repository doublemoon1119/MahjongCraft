package com.doublemoon1119.mahjongcraft.platform.minecraft.api.character

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 角色描述的資料大小限制；登記、存讀檔與同步使用相同邊界。 */
object AiCharacterLimits {
    /** 角色、能力與資源識別碼的最長字元數。 */
    const val MAX_ID_LENGTH: Int = 256

    /** 一個角色預覽最多攜帶的參數數量。 */
    const val MAX_PREVIEW_PARAMETERS: Int = 32

    /** 一個外觀參數的最長字元數。 */
    const val MAX_PARAMETER_LENGTH: Int = 256

    /** 同步目錄最多包含的角色數量。 */
    const val MAX_CATALOGUE_SIZE: Int = 256

    /** 單桌公開角色同步的成員上限，不由世界實體數量推斷。 */
    const val MAX_SYNC_PLAYERS: Int = 256

    /**
     * 驗證可傳遞的完整識別碼。
     *
     * @param id 要檢查的識別碼。
     */
    internal fun requireId(id: String) {
        require(id.length <= MAX_ID_LENGTH) { "Character ID is too long" }
        NamespacedId.requireValid(id) { "Invalid character ID: $id" }
    }
}
