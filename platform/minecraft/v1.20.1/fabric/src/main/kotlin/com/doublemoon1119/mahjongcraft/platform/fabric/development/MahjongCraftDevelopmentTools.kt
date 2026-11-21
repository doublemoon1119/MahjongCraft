package com.doublemoon1119.mahjongcraft.platform.fabric.development

import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import net.fabricmc.loader.api.FabricLoader

/** 找出開發專用模組的進入點；正式環境一律沒有。 */
internal object MahjongCraftDevelopmentTools {
    /** 開發專用模組在 `fabric.mod.json` 宣告的進入點名稱。 */
    const val ENTRYPOINT: String = "mahjongcraft:development"

    /**
     * 取得開發專用模組的進入點。
     *
     * @param environment 判斷是否為開發環境。
     * @return 開發環境中宣告的進入點；正式環境為空。
     */
    fun discover(environment: MinecraftEnvironment): List<MahjongCraftDevelopmentEntrypoint> = if (environment.isDevelopment) {
        FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, MahjongCraftDevelopmentEntrypoint::class.java)
    } else {
        emptyList()
    }
}
