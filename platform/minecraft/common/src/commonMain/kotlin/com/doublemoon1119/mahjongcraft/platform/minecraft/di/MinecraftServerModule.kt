package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Minecraft loader 與版本無關的 server-side Koin 定義。 */
@Module
class MinecraftServerModule {
    /** 建立保存最後一份有效伺服器設定的 runtime state。 */
    @Single
    fun provideMinecraftServerConfigState(): MinecraftServerConfigState = MinecraftServerConfigState(MinecraftServerConfig())

    /** 建立 server config 使用的嚴格 TOML codec。 */
    @Single
    fun provideMinecraftServerConfigTomlCodec(): MinecraftServerConfigTomlCodec = MinecraftServerConfigTomlCodec()

    /** 建立目前 server session 使用的桌子位置索引。 */
    @Single
    fun provideTableLocationRegistry(): TableLocationRegistry = TableLocationRegistry()
}
