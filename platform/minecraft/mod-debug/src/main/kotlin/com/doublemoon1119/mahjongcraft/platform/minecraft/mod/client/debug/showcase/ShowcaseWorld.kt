package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.showcase

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.createFreshWorld
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.leaveWorld
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets

/** 宣傳截圖使用的世界：每次刪掉上次留下的世界，重新建立固定種子的一般地形創造世界，也可以中途離開再重建。 */
internal object ShowcaseWorld {
    /** 宣傳截圖世界的存檔名稱。 */
    private const val LEVEL_ID: String = "mahjongcraft-showcase"

    /** 宣傳截圖世界的種子。離出生點最近的櫻花林旁有平坦的空地，周圍櫻花樹多。 */
    private const val SEED: Long = 2L

    /**
     * 刪掉上次留下的世界，建立新的世界並開始進入。進入的過程由原版的載入畫面接手。
     *
     * @param client 目前客戶端。
     */
    fun createAndEnter(client: Minecraft) {
        val storage = client.levelSource
        if (storage.levelExists(LEVEL_ID)) storage.createAccess(LEVEL_ID).use { it.deleteLevel() }
        val settings = LevelSettings(LEVEL_ID, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, GameRules(), WorldDataConfiguration.DEFAULT)
        createFreshWorld(
            client = client,
            levelId = LEVEL_ID,
            settings = settings,
            options = WorldOptions(SEED, true, false),
            dimensions = { registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions() },
            returnScreen = TitleScreen(),
        )
    }

    /**
     * 存檔並離開宣傳截圖世界，回到主選單。與暫停選單的「儲存並離開」相同。
     *
     * @param client 目前客戶端。
     */
    fun leave(client: Minecraft) {
        client.level?.disconnect()
        leaveWorld(client, Component.translatable("menu.savingLevel"))
        client.setScreen(TitleScreen())
    }
}
