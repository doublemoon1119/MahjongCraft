package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.table.TablePresentationCleaner
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

/**
 * 使用正式呈現介面重建開發情境的完整桌面；呼叫端須切換至伺服器主執行緒。
 *
 * @property serverHolder 目前伺服器，用於取得牌桌所在世界。
 * @property tableLocationRegistry 牌桌位置索引。
 * @property presentationCleaner 清除既有桌面呈現的服務。
 * @property presentationPublisher 發布新情境桌面的服務。
 */
class DebugGameScenarioPresentationSynchronizer(
    private val serverHolder: ServerHolder,
    private val tableLocationRegistry: TableLocationRegistry,
    private val presentationCleaner: TablePresentationCleaner,
    private val presentationPublisher: DebugGameScenarioPresentationPublisher,
) {
    /**
     * 清除既有呈現並從權威情境結果發布新桌況。
     *
     * @param result 已通過驗證的對局與呈現資料。
     * @throws IllegalArgumentException 牌桌位置、維度識別碼或所在世界不可用時拋出。
     */
    fun synchronize(result: DebugGameScenarioResult) {
        val game = result.game
        val location = requireNotNull(tableLocationRegistry.get(game.id)?.location) { "No table location for debug scenario" }
        val dimensionId = requireNotNull(ResourceLocation.tryParse(location.dimensionId)) { "Invalid table dimension ID" }
        val worldKey = ResourceKey.create(Registries.DIMENSION, dimensionId)
        val world = requireNotNull(serverHolder.current()?.getLevel(worldKey)) { "Table world is not loaded" }
        presentationCleaner.clear(world, game.id, BlockPos(location.x, location.y, location.z))
        presentationPublisher.publish(result)
    }
}
