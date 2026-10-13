package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshot
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshotCategory
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry

/**
 * 集中保存 Fabric extension bootstrap 使用的兩個 runtime registry。
 *
 * @property tablePropKindRegistry 規則桌面物件種類 registry。
 * @property seatProviderRegistry 座位 provider registry。
 */
internal class FabricExtensionRegistries(
    val tablePropKindRegistry: FabricTablePropKindRegistry,
    val seatProviderRegistry: FabricSeatProviderRegistry,
) {
    /**
     * 取得兩個 Fabric 登記類別的不可變診斷快照。
     *
     * @return 目前兩個 registry 的登記快照。
     */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        listOf(
            ExtensionRegistrationSnapshotCategory(FabricExtensionRegistrationCategoryIds.TABLE_PROP_KIND, "Table Prop Kind", tablePropKindRegistry.registrationKeys),
            ExtensionRegistrationSnapshotCategory(FabricExtensionRegistrationCategoryIds.SEAT_PROVIDER, "Seat Provider", seatProviderRegistry.registrationKeys),
        ),
    )

    /** 依固定順序凍結兩個 Fabric registry。 */
    fun freezeAll() {
        tablePropKindRegistry.freeze()
        seatProviderRegistry.freeze()
    }
}
