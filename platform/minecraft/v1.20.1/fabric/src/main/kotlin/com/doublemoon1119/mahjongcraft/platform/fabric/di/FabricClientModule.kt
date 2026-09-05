package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.client.di.FlowClientModule
import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Fabric client 使用的 Koin 定義。
 *
 * 此 module 只由 Minecraft client graph 載入，可掃描 HUD、renderer 與其他 client-only adapter；
 * dedicated server graph 不會 include 此 module。
 *
 * 完整 client app 的 `@KoinApplication` 會同時載入 [FabricClientModule] 與 server 端的
 * `FabricServerModule`（後者間接 include [FlowCommonModule]），因此 runtime graph 本來就看得到
 * [FlowCommonModule] 提供的 single；這裡直接列出純粹是因為 Koin compiler plugin 的 strictSafety
 * 靜態依賴檢查只針對單一 `@Module` 自身的 includes 樹驗證，不會考慮其他同時載入的 module。Koin
 * runtime 對同一個 module 被多路徑重複 include 本來就會去重，不會造成 single 被註冊兩次。
 */
@Module(includes = [FlowClientModule::class, FlowCommonModule::class, FabricCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric.client")
class FabricClientModule
