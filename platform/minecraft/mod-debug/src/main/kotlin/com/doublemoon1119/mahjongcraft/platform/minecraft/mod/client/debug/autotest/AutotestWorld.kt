package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.createFreshWorld
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.leaveWorld
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.openExistingWorld
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

/** 自動測試使用的世界：每次刪掉上次留下的世界，重新建立固定種子的超平坦創造世界。 */
internal object AutotestWorld {
    /** 測試世界的存檔名稱。 */
    const val LEVEL_ID: String = "mahjongcraft-autotest"

    /** 測試世界的種子。 */
    private const val SEED: Long = 1_119L

    /**
     * 刪掉上次留下的測試世界，建立新的世界並開始進入；進入的過程由原版的載入畫面接手。
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
            options = WorldOptions(SEED, false, false),
            dimensions = { registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },
            returnScreen = TitleScreen(),
        )
    }

    /**
     * 存檔並離開測試世界，回到主選單；與暫停選單的「儲存並離開」相同。
     *
     * @param client 目前客戶端。
     */
    fun saveAndQuit(client: Minecraft) {
        client.level?.disconnect()
        leaveWorld(client, Component.translatable("menu.savingLevel"))
        client.setScreen(TitleScreen())
    }

    /**
     * 重新進入測試世界；進入的過程由原版的載入畫面接手。
     *
     * @param client 目前客戶端。
     */
    fun reenter(client: Minecraft) {
        openExistingWorld(client, LEVEL_ID, TitleScreen())
    }
}
