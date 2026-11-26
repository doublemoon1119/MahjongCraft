package com.doublemoon1119.mahjongcraft.example.minecraft.mod

import com.doublemoon1119.mahjongcraft.example.minecraft.ExampleMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.character.ExampleActorProviders
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.seating.ExampleSeatPlacements
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.table.ExampleHandRevealChipTablePropKind
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.RoomParticipantDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.AiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.RoomParticipantRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.SeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.SeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.registration.TablePropKindRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.NativeMahjongExtension

/** 由各載入器的擴充宣告發現，將同一個範例交給核心、Minecraft 與原生擴充登記流程。 */
class ExampleNativeMahjongExtension :
    ExampleMinecraftMahjongExtension(),
    NativeMahjongExtension {
    /**
     * 登記椅子與坐墊的解析能力。此時不讀取原生家具或世界。
     *
     * @param registry 本體提供的座位 registry。
     */
    override fun registerSeatProviders(registry: SeatProviderRegistrar) {
        ExampleFurniture.seatProviders().forEach(registry::register)
    }

    /** 登記範例攤牌籌碼的桌面物件種類。 */
    override fun registerTablePropKinds(registry: TablePropKindRegistrar) {
        registry.register(ExampleHandRevealChipTablePropKind)
    }

    /** 方塊椅子與實體坐墊皆可由手持物品放置在 AI 席位。 */
    override fun registerSeatPlacements(registry: SeatPlacementRegistrar) {
        ExampleSeatPlacements.definitions().forEach(registry::register)
    }

    /** 只登記範例自己的人偶資格，不將其他原版或第三方生物納入。 */
    override fun registerRoomParticipants(registry: RoomParticipantRegistrar) {
        registry.register(RoomParticipantDefinition(ExampleCharacterIds.ACTOR, ExampleActorProviders.PARTICIPANT))
    }

    /** 借用與自有能力分開登記，不在初始化時生成實體。 */
    override fun registerAiCharacterCapabilities(registry: AiCharacterCapabilityRegistrar) {
        registry.register(AiCharacterCapabilityDefinition(ExampleCharacterIds.ACTOR, ExampleActorProviders.BORROWED))
        registry.register(AiCharacterCapabilityDefinition(ExampleCharacterIds.OWNED_ACTOR, ExampleActorProviders.OWNED))
    }
}
