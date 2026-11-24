package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import org.koin.core.Koin
import org.koin.core.module.Module

/**
 * 開發專用模組的進入點，載入器依 [MahjongCraftDevelopmentTools.ENTRYPOINT] 找到它。
 *
 * 只在開發環境呼叫。本體啟動 Koin 後先載入 [koinModules]，接著登記擴充，最後在登記本體指令時呼叫 [initialize]。
 */
interface MahjongCraftDevelopmentEntrypoint {
    /**
     * 要載入本體 Koin 的模組；擴充登記之前載入，擴充可以取用其中的服務。
     *
     * @return 開發工具的 Koin 模組。
     */
    fun koinModules(): List<Module>

    /**
     * 登記指令與本體提供的開發用接口。
     *
     * @param koin 已載入 [koinModules] 的本體 Koin。
     * @param events 登記事件與指令用的伺服器事件來源。
     */
    fun initialize(koin: Koin, events: ServerEvents)
}
