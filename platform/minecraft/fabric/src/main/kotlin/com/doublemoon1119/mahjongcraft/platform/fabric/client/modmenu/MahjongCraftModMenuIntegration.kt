package com.doublemoon1119.mahjongcraft.platform.fabric.client.modmenu

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigScreenController
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import org.koin.core.context.GlobalContext

/**
 * ModMenu 的整合：模組清單中麻將模組的「設定」按鈕開啟客戶端設定，返回時回到模組清單。
 *
 * 只有 ModMenu 會經由 `modmenu` 進入點載入這個類別；沒有安裝 ModMenu 時不會載入，也不需要 ModMenu 的類別。
 */
class MahjongCraftModMenuIntegration : ModMenuApi {
    /**
     * 提供「設定」按鈕開啟的畫面。
     *
     * @return 以模組清單為上一層的客戶端設定畫面。
     */
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent ->
        GlobalContext.get().get<MahjongClientConfigScreenController>().screen(parent)
    }
}
