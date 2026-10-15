package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableCornerWidthTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Minecraft loader 與版本無關的 server-side Koin 定義。
 *
 * 這些服務只由 server graph 管理，避免 client graph 建立伺服器狀態。設定狀態以預設的有效設定作為初值；桌子位置索引與
 * 桌上物件角落寬度紀錄的內容由 session 生命週期服務清理，而不是假設 Koin 會自動重建 single。
 */
val minecraftServerModule = module {
    single { MinecraftServerConfigState(MinecraftServerConfig()) }
    single<MinecraftServerConfigTomlCodec>()
    single<TableLocationRegistry>()
    single<TableCornerWidthTracker>()
}
