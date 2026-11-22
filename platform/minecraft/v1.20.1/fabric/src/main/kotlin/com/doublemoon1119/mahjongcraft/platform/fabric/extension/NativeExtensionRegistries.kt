package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshot
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSnapshotCategory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.AiCharacterCapabilityRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.RoomParticipantRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatPlacementRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.SeatProviderRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.TablePropKindRegistry

/**
 * 集中保存原生整合擴充登記使用的 runtime registry。
 *
 * @property tablePropKindRegistry 規則桌面物件種類 registry。
 * @property seatProviderRegistry 座位 provider registry。
 * @property characterCapabilityRegistry 原生角色能力 registry。
 * @property seatPlacementRegistry 座位物品放置 registry。
 * @property roomParticipantRegistry 既有實體參加能力。
 */
internal class NativeExtensionRegistries(
    val tablePropKindRegistry: TablePropKindRegistry,
    val seatProviderRegistry: SeatProviderRegistryImpl,
    val characterCapabilityRegistry: AiCharacterCapabilityRegistry,
    val seatPlacementRegistry: SeatPlacementRegistry,
    val roomParticipantRegistry: RoomParticipantRegistry,
) {
    /**
     * 取得原生整合登記類別的不可變診斷快照。
     *
     * @return 目前各 registry 的登記快照。
     */
    fun registrationSnapshot(): ExtensionRegistrationSnapshot = ExtensionRegistrationSnapshot(
        categories = listOf(
            ExtensionRegistrationSnapshotCategory(ExtensionRegistrationCategoryIds.ROOM_PARTICIPANT, "Room Participant", roomParticipantRegistry.registrationKeys),
            ExtensionRegistrationSnapshotCategory(
                id = ExtensionRegistrationCategoryIds.TABLE_PROP_KIND,
                displayName = "Table Prop Kind",
                registrationKeys = tablePropKindRegistry.registrationKeys,
            ),
            ExtensionRegistrationSnapshotCategory(
                id = ExtensionRegistrationCategoryIds.SEAT_PROVIDER,
                displayName = "Seat Provider",
                registrationKeys = seatProviderRegistry.registrationKeys,
            ),
            ExtensionRegistrationSnapshotCategory(
                id = ExtensionRegistrationCategoryIds.AI_CHARACTER_CAPABILITY,
                displayName = "AI Character Capability",
                registrationKeys = characterCapabilityRegistry.registrationKeys,
            ),
            ExtensionRegistrationSnapshotCategory(
                id = ExtensionRegistrationCategoryIds.SEAT_PLACEMENT,
                displayName = "Seat Placement",
                registrationKeys = seatPlacementRegistry.registrationKeys,
            ),
        ),
    )

    /** 依固定順序凍結原生整合 registry。 */
    fun freezeAll() {
        tablePropKindRegistry.freeze()
        seatProviderRegistry.freeze()
        characterCapabilityRegistry.freeze()
        seatPlacementRegistry.freeze()
        roomParticipantRegistry.freeze()
    }
}
