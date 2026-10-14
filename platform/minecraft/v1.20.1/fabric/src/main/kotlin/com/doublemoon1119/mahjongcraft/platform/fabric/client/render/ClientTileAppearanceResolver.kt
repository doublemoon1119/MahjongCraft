package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 依公開桌況解析牌組外觀，並在桌況暫時清除後保留目前演出的外觀選擇。
 *
 * 這個快取只保存公開的規則外觀，不保存牌種、牌面或實體牌 UUID。新的對局快照抵達時會覆蓋同一桌的
 * 舊值；客戶端連線結束或重新建立時由 [clear] 清除，避免桌子 UUID 在不同世界重用時沿用舊世界外觀。
 *
 * @property stateStore 提供每桌公開對局設定。
 * @property moduleRegistry 將公開規則設定解析為規則模組識別碼。
 * @property tileAssetRegistry 提供規則到牌組外觀的公開綁定。
 */
@Single
class ClientTileAppearanceResolver(
    @Provided private val stateStore: ClientMahjongStateStore,
    @Provided private val moduleRegistry: MahjongModuleRegistry,
    @Provided private val tileAssetRegistry: MinecraftTileAssetRegistry,
) {
    /** 暫存各桌最近一次由公開規則解析出的牌組外觀識別碼。 */
    private val appearanceIdsByTable = mutableMapOf<Uuid, String>()

    /**
     * 取得指定桌子的牌組外觀；沒有桌子或公開規則時使用內建預設。
     *
     * @param tableId 桌子 UUID；沒有桌子上下文時為 `null`。
     * @return 本次繪製應使用的固定牌組外觀。
     */
    fun resolve(tableId: Uuid?): MinecraftTileSetAppearanceDefinition {
        if (tableId == null) return BuiltInMinecraftTileAppearances.DEFAULT
        stateStore.gameSnapshot(tableId)?.let { snapshot ->
            val ruleModuleId = moduleRegistry.getModule(snapshot.config).id
            val appearance = tileAssetRegistry.findAppearanceForRule(ruleModuleId)
                ?: BuiltInMinecraftTileAppearances.DEFAULT
            appearanceIdsByTable[tableId] = appearance.id
            return appearance
        }
        val cachedId = appearanceIdsByTable[tableId]
        return cachedId?.let(tileAssetRegistry::findAppearance)
            ?: BuiltInMinecraftTileAppearances.DEFAULT
    }

    /** 清除所有桌子的 client-only 外觀快取；應在 JOIN 與 DISCONNECT 時呼叫。 */
    fun clear() {
        appearanceIdsByTable.clear()
    }
}
