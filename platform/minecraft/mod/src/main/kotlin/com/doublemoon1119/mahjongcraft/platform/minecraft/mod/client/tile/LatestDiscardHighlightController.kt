package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistry
import net.minecraft.client.Minecraft
import kotlin.uuid.toKotlinUuid

/**
 * 逐 client tick 讓每桌最後打出、仍留在牌河的那張牌外框發光。規則的 [TableTileLayoutProfile.highlightsLatestDiscard]
 * 與玩家的顯示設定都開著才顯示，牌還沒落到牌河或還在移動時不亮。
 */
class LatestDiscardHighlightController(
    private val stateStore: ClientMahjongStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
    private val layoutProfiles: TableTileLayoutProfileRegistry,
    private val configStore: MahjongClientConfigStore,
    private val settlement: ClientDiscardSettlementTracker,
) {
    /**
     * 註冊 client tick 更新。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onClientTickEnd(::tick)
    }

    /**
     * 依各桌快照的最新捨牌更新所有麻將牌的標示。
     *
     * @param client Minecraft client 狀態與當前世界。
     */
    private fun tick(client: Minecraft) {
        val tiles = client.level?.entitiesForRendering()?.filterIsInstance<MahjongTileEntity>()?.toList().orEmpty()
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
            .filter { tile ->
                val tileId = tile.uuid.toKotlinUuid()
                tile.managedTableId?.let { tableId -> (tableId to tileId) in latestDiscards && settlement.isSettled(tableId, tileId) } == true
            }
            .forEach { tile -> tile.setLatestDiscardHighlight(HIGHLIGHT_COLOR) }
    }

    /** 外框顏色。其他畫面重現最新捨牌標示時共用。 */
    internal companion object {
        /** 最新捨牌的外框色彩：固定不閃爍的珊瑚紅，與準星同種牌提示的青色、金色呼吸光區隔。 */
        const val HIGHLIGHT_COLOR: Int = 0xFF6B4A
    }
}
