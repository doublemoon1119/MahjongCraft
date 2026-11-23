package com.doublemoon1119.mahjongcraft.platform.forge.debug

import com.doublemoon1119.mahjongcraft.platform.forge.di.forgeServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modClientModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.debugServerModule
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * 與客戶端開發執行相同的服務組合：本體的 [forgeServerModule]、[modClientModule] 加上 [debugServerModule]。
 *
 * 開發執行時 debug 服務由本體載入既有的 Koin；這個組合讓編譯期就檢查 debug 的伺服器服務與客戶端指令需要的依賴都有登記，
 * 測試也以它建立服務。
 *
 * @return 尚未啟動的 Koin 應用程式。
 */
internal fun debugKoinApplication(): KoinApplication = koinApplication {
    allowOverride(false)
    modules(forgeServerModule, modClientModule, debugServerModule)
}
