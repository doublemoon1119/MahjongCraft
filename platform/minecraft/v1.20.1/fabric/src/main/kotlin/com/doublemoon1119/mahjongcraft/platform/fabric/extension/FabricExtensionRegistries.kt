package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshot
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshotCategory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterCapabilityRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricRoomParticipantRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatPlacementRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry

/**
 * 集中保存 Fabric extension bootstrap 使用的runtime registry。
 *
 * @property tablePropKindRegistry 規則桌面物件種類 registry。
 * @property seatProviderRegistry 座位 provider registry。
 * @property characterCapabilityRegistry 原生角色能力 registry。
 * @property seatPlacementRegistry 座位物品放置 registry。
 * @property roomParticipantRegistry 既有實體參加能力。
 */
internal class FabricExtensionRegistries(
    val tablePropKindRegistry: FabricTablePropKindRegistry,
    val seatProviderRegistry: FabricSeatProviderRegistryImpl,
    val characterCapabilityRegistry: FabricAiCharacterCapabilityRegistry,
    val seatPlacementRegistry: FabricSeatPlacementRegistry,
    val roomParticipantRegistry: FabricRoomParticipantRegistry,
) {
    /**
     * 取得Fabric 登記類別的不可變診斷快照。
     *
     * @return 目前各 registry 的登記快照。
     */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        categories = listOf(
            ExtensionRegistrationSnapshotCategory(FabricExtensionRegistrationCategoryIds.ROOM_PARTICIPANT, "Room Participant", roomParticipantRegistry.registrationKeys),
            ExtensionRegistrationSnapshotCategory(
                id = FabricExtensionRegistrationCategoryIds.TABLE_PROP_KIND,
                displayName = "Table Prop Kind",
                registrationKeys = tablePropKindRegistry.registrationKeys,
            ),
            ExtensionRegistrationSnapshotCategory(
                id = FabricExtensionRegistrationCategoryIds.SEAT_PROVIDER,
                displayName = "Seat Provider",
                registrationKeys = seatProviderRegistry.registrationKeys,
            ),
            ExtensionRegistrationSnapshotCategory(
                id = FabricExtensionRegistrationCategoryIds.AI_CHARACTER_CAPABILITY,
                displayName = "AI Character Capability",
                registrationKeys = characterCapabilityRegistry.registrationKeys,
            ),
            ExtensionRegistrationSnapshotCategory(
                id = FabricExtensionRegistrationCategoryIds.SEAT_PLACEMENT,
                displayName = "Seat Placement",
                registrationKeys = seatPlacementRegistry.registrationKeys,
            ),
        ),
    )

    /** 依固定順序凍結 Fabric registry。 */
    fun freezeAll() {
        tablePropKindRegistry.freeze()
        seatProviderRegistry.freeze()
        characterCapabilityRegistry.freeze()
        seatPlacementRegistry.freeze()
        roomParticipantRegistry.freeze()
    }
}
