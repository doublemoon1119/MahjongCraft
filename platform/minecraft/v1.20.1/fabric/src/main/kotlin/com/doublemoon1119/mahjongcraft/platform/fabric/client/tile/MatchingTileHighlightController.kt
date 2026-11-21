package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.EntityHitResult
import kotlin.math.sin
import kotlin.uuid.toKotlinUuid

/** 逐 client tick 更新同桌同種牌的本地原版 glowing 狀態。 */
class MatchingTileHighlightController(
    private val stateStore: ClientMahjongStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
    private val configStore: MahjongClientConfigStore,
) {
    /** 註冊 client tick 更新。 */
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /**
     * 依準星目標與目前可見牌面更新所有管理中麻將牌。
     *
     * @param client Minecraft client 狀態與當前世界。
     */
    private fun tick(client: Minecraft) {
        val tiles = client.level?.entitiesForRendering()?.filterIsInstance<MahjongTileEntity>()?.toList().orEmpty()
        tiles.forEach(MahjongTileEntity::clearMatchingHighlight)
        if (!configStore.current.presentationVisibility.matchingTileHighlightEnabled) return
        val target = (client.hitResult as? EntityHitResult)?.entity as? MahjongTileEntity ?: return
        if (!target.canParticipate()) return
        val tableId = target.managedTableId ?: return
        val targetTile = stateStore.findManagedTileSnapshot(tableId, target.uuid.toKotlinUuid())?.tile ?: return
        val snapshot = stateStore.gameSnapshot(tableId) ?: return
        val interpretation = moduleRegistry.getModule(snapshot.config).createTileInterpretationPolicy()
        val canonicalTarget = interpretation.canonicalize(targetTile)
        val time = target.level().gameTime + client.frameTime.toDouble()
        val targetColor = targetColor(time)
        val otherMatchColor = otherMatchColor(time)
        tiles.asSequence()
            .filter { tile -> tile.managedTableId == tableId && tile.canParticipate() }
            .filter { tile ->
                stateStore.findManagedTileSnapshot(tableId, tile.uuid.toKotlinUuid())?.tile?.let(interpretation::canonicalize) == canonicalTarget
            }
            .forEach { tile -> tile.setMatchingHighlight(if (tile.uuid == target.uuid) targetColor else otherMatchColor) }
    }

    /** 只有停止動畫的正式牌局牌可參與同種牌提示；牌面姿態不影響判斷，未知牌的快照本身就是 null。 */
    private fun MahjongTileEntity.canParticipate(): Boolean = managedByGame &&
        !animating &&
        managedTableId?.let { tableId -> stateStore.findManagedTileSnapshot(tableId, uuid.toKotlinUuid())?.tile != null } == true

    /** 呼吸光的顏色與速度；其他畫面重現同種牌提示時共用。 */
    internal companion object {
        /** 準星目標呼吸效果最低亮度。 */
        const val TARGET_PULSE_DARK_COLOR: Int = 0x1A6B63

        /** 準星目標呼吸效果最高亮度。 */
        const val TARGET_PULSE_BRIGHT_COLOR: Int = 0x3ED9C8

        /** 其他同種牌呼吸效果最低亮度，與準星目標的冷色調區隔。 */
        const val OTHER_MATCH_PULSE_DARK_COLOR: Int = 0x6B4A18

        /** 其他同種牌呼吸效果最高亮度。 */
        const val OTHER_MATCH_PULSE_BRIGHT_COLOR: Int = 0xFFC94D

        /** 呼吸效果每 tick 的相位增量。 */
        const val PULSE_SPEED: Double = 0.16

        /**
         * 準星指著的牌在 [time] 時的呼吸光顏色。
         *
         * @param time 遊戲時間，以 tick 為單位，可含本幀進度。
         * @return RGB 顏色。
         */
        fun targetColor(time: Double): Int = interpolateColor(TARGET_PULSE_DARK_COLOR, TARGET_PULSE_BRIGHT_COLOR, wave(time))

        /**
         * 其他同種牌在 [time] 時的呼吸光顏色。
         *
         * @param time 遊戲時間，以 tick 為單位，可含本幀進度。
         * @return RGB 顏色。
         */
        fun otherMatchColor(time: Double): Int = interpolateColor(OTHER_MATCH_PULSE_DARK_COLOR, OTHER_MATCH_PULSE_BRIGHT_COLOR, wave(time))

        /**
         * 呼吸效果在 [time] 時的亮度比例。
         *
         * @param time 遊戲時間，以 tick 為單位，可含本幀進度。
         * @return 介於 0 與 1。
         */
        private fun wave(time: Double): Float = ((sin(time * PULSE_SPEED) + 1.0) / 2.0).toFloat()

        /**
         * 在兩個 RGB 色彩之間線性內插。
         *
         * @param from 起始 RGB 色彩。
         * @param to 結束 RGB 色彩。
         * @param progress 內插比例；計算後的色彩通道會限制在 0–255。
         */
        private fun interpolateColor(from: Int, to: Int, progress: Float): Int {
            fun component(shift: Int): Int {
                val start = from shr shift and 0xFF
                val end = to shr shift and 0xFF
                return (start + (end - start) * progress).toInt().coerceIn(0, 255)
            }
            return component(16) shl 16 or (component(8) shl 8) or component(0)
        }
    }
}
