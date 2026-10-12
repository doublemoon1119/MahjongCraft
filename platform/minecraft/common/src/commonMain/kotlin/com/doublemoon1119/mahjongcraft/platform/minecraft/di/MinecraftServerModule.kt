package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableCornerWidthTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Minecraft loader 與版本無關的 server-side Koin 定義。
 *
 * 這些服務與共用呈現 registry 位於相同的 config／table 功能套件，無法靠套件掃描區分 graph。
 * 因此在此明確提供，只由 server module 管理，避免共用 module 的掃描提早建立伺服器狀態；
 * session 結束時仍由既有生命週期服務清理，而不是假設 Koin 會自動重建 single。
 */
@Module
class MinecraftServerModule {
    /** 在 server graph 建立設定狀態，明確使用預設的有效設定作為初值。 */
    @Single
    fun provideMinecraftServerConfigState(): MinecraftServerConfigState = MinecraftServerConfigState(MinecraftServerConfig())

    /** 在 server graph 建立嚴格 TOML codec，避免共用套件掃描擴大此服務的歸屬。 */
    @Single
    fun provideMinecraftServerConfigTomlCodec(): MinecraftServerConfigTomlCodec = MinecraftServerConfigTomlCodec()

    /** 在 server graph 建立桌子位置索引，其內容由 session 生命週期清理。 */
    @Single
    fun provideTableLocationRegistry(): TableLocationRegistry = TableLocationRegistry()

    /** 在 server graph 建立桌上物件角落寬度紀錄，其內容由 session 生命週期清理。 */
    @Single
    fun provideTableCornerWidthTracker(): TableCornerWidthTracker = TableCornerWidthTracker()
}
