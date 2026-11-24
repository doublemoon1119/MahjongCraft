package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.GenericDirtMessageScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.levelgen.WorldDimensions
import net.minecraft.world.level.levelgen.WorldOptions

/**
 * 建立新的單人世界並開始進入；進入的過程由原版的載入畫面接手。
 *
 * @param client 目前客戶端。
 * @param levelId 存檔名稱。
 * @param settings 世界設定。
 * @param options 種子等生成選項。
 * @param dimensions 依登記表建立世界的維度。
 * @param returnScreen 建立失敗時回到的畫面；這個版本的原版自行決定，不使用。
 */
@Suppress("UNUSED_PARAMETER")
fun createFreshWorld(
    client: Minecraft,
    levelId: String,
    settings: LevelSettings,
    options: WorldOptions,
    dimensions: (RegistryAccess) -> WorldDimensions,
    returnScreen: Screen,
) {
    client.createWorldOpenFlows().createFreshLevel(levelId, settings, options, dimensions)
}

/**
 * 進入已經存在的單人世界；進入的過程由原版的載入畫面接手。
 *
 * @param client 目前客戶端。
 * @param levelId 存檔名稱。
 * @param returnScreen 取消或失敗時回到的畫面。
 */
fun openExistingWorld(client: Minecraft, levelId: String, returnScreen: Screen) {
    client.createWorldOpenFlows().loadLevel(returnScreen, levelId)
}

/**
 * 離開目前的世界，存檔時顯示訊息畫面。
 *
 * @param client 目前客戶端。
 * @param message 存檔時顯示的訊息。
 */
fun leaveWorld(client: Minecraft, message: Component) {
    client.clearLevel(GenericDirtMessageScreen(message))
}
