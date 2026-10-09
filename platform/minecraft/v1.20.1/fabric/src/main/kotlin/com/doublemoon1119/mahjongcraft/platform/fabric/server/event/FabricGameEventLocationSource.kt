package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.event.GameEventLocationSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 從已發布的不可變桌子位置快照提供事件位置。
 * @property locations 支援背景讀取的記憶體位置索引。
 */
@Single(binds = [GameEventLocationSource::class])
internal class FabricGameEventLocationSource(
    private val locations: TableLocationRegistry,
) : GameEventLocationSource {
    /**
     * 只讀取記憶體索引，不接觸世界或阻塞；此方法可在權威 store 交易鎖內由背景執行緒呼叫。
     *
     * @param venueId 事件所屬場地 UUID。
     * @return 已知桌子位置，或尚未登記時的 null。
     */
    override fun find(venueId: Uuid): TableLocation? = locations.get(venueId)?.location
}
