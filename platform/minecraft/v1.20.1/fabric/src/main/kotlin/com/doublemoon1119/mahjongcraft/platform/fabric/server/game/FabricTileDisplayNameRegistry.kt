package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import org.koin.core.annotation.Single

/**
 * [TileDisplayNameRegistry] 的 1.20.1 Fabric Koin binding。
 *
 * 介面本身跨版本／loader 共用，定義在 `platform/minecraft/common`；實作與 Koin binding 放在這裡，
 * 比照 `AiStrategyDisplayNameRegistry` 與其 Fabric 實作 `FabricAiStrategyDisplayNameRegistry` 的分工
 * 方式。實際邏輯委派給 [TileDisplayNameRegistryImpl]；內建與第三方牌種的註冊、凍結時機交給
 * [com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar]。
 *
 * 放在 `server.game` 套件（而不是跟 [com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText]
 * 同一個 `text` 套件）——`@ComponentScan` 只掃描 `platform.fabric.server` 底下，Koin 帶註解的類別要放在
 * 掃描範圍內才會被發現。
 */
@Single(binds = [TileDisplayNameRegistry::class])
class FabricTileDisplayNameRegistry : TileDisplayNameRegistry by TileDisplayNameRegistryImpl()
