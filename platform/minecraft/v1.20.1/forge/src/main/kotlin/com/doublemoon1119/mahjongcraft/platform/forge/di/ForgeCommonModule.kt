package com.doublemoon1119.mahjongcraft.platform.forge.di

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeLoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.forge.loader.OculusShadowPassProbe
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di.modCommonModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.LinkageIsolatingExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ShadowPassProbe
import org.koin.dsl.module

/**
 * Forge client 與 server graph 共用的 Forge 定義。
 *
 * 在 [modCommonModule] 之外提供 Forge Mod Loader 的執行環境資訊、額外隔離 JVM 類別連結錯誤的擴充失敗判斷，
 * 以及以 Oculus 判斷光影陰影圖的方式（只在用戶端繪製時取用）。
 */
val forgeCommonModule = module {
    includes(modCommonModule)
    single<LoaderPlatform> { ForgeLoaderPlatform }
    single<ExtensionFailurePolicy> { LinkageIsolatingExtensionFailurePolicy() }
    single<ShadowPassProbe> { OculusShadowPassProbe(get()) }
}
