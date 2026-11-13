package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import kotlin.uuid.toKotlinUuid

/**
 * 逐 client tick 讓每桌最後打出、仍留在牌河的那張牌外框發光；規則的 [TableTileLayoutProfile.highlightsLatestDiscard]
 * 與玩家的顯示設定都開著才顯示，牌還在飛行動畫中時不亮。
 */
class LatestDiscardHighlightController(
    private val stateStore: ClientMahjongStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
    private val layoutProfiles: TableTileLayoutProfileRegistry,
    private val configStore: MahjongClientConfigStore,
) {
    /** 註冊 client tick 更新。 */
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /**
     * 依各桌快照的最新捨牌更新所有麻將牌的標示。
     *
     * @param client Minecraft client 狀態與當前世界。
     */
    private fun tick(client: MinecraftClient) {
        val tiles = client.world?.entities?.filterIsInstance<MahjongTileEntity>()?.toList().orEmpty()
        tiles.forEach(MahjongTileEntity::clearLatestDiscardHighlight)
        if (!configStore.current.presentationVisibility.latestDiscardHighlightEnabled) return
        val latestDiscards = stateStore.gameTableIds().mapNotNull { tableId ->
            val snapshot = stateStore.gameSnapshot(tableId) ?: return@mapNotNull null
            val tileId = snapshot.latestDiscardTileId ?: return@mapNotNull null
            val profile = layoutProfiles.profileFor(moduleRegistry.getModule(snapshot.config).id)
            (tableId to tileId).takeIf { profile.highlightsLatestDiscard }
        }.toSet()
        if (latestDiscards.isEmpty()) return
        tiles.asSequence()
            .filter { tile -> tile.managedByGame && !tile.animating }
            .filter { tile -> tile.managedTableId?.let { tableId -> (tableId to tile.uuid.toKotlinUuid()) in latestDiscards } == true }
            .forEach { tile -> tile.setLatestDiscardHighlight(HIGHLIGHT_COLOR) }
    }

    private companion object {
        /** 最新捨牌的外框色彩：固定不閃爍的珊瑚紅，與準星同種牌提示的青色、金色呼吸光區隔。 */
        const val HIGHLIGHT_COLOR: Int = 0xFF6B4A
    }
}
