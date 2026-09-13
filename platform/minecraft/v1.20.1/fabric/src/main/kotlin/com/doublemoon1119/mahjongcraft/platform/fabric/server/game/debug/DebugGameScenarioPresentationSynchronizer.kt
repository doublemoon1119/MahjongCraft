package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTablePresentationCleaner
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single

/** 使用正式 presentation contract 重建 debug scenario 的完整桌面呈現。 */
@Single
class DebugGameScenarioPresentationSynchronizer(
    private val serverHolder: FabricServerHolder,
    private val tableLocationRegistry: TableLocationRegistry,
    private val presentationCleaner: FabricTablePresentationCleaner,
    private val presentationPublisher: DebugGameScenarioPresentationPublisher,
) {
    /** 清除上一個玩家區並從權威 scenario 結果完整發布新桌況。 */
    fun synchronize(result: DebugGameScenarioResult) {
        val game = result.game
        val location = requireNotNull(tableLocationRegistry.get(game.id)?.location) { "No table location for debug scenario" }
        val dimensionId = requireNotNull(Identifier.tryParse(location.dimensionId)) { "Invalid table dimension ID" }
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, dimensionId)
        val world = requireNotNull(serverHolder.current()?.getWorld(worldKey)) { "Table world is not loaded" }
        presentationCleaner.clear(world, game.id, BlockPos(location.x, location.y, location.z))
        presentationPublisher.publish(result)
    }
}
