package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** Minecraft loader 與版本無關的 server-side Koin 定義。 */
@Module
class MinecraftServerModule {
    /** 提供目前使用預設值的伺服器政策；後續由磁碟 config adapter 取代。 */
    @Single
    fun provideMinecraftServerConfig(): MinecraftServerConfig = MinecraftServerConfig()

    /** 建立目前 server session 使用的桌子位置索引。 */
    @Single
    fun provideTableLocationRegistry(): TableLocationRegistry = TableLocationRegistry()
}
