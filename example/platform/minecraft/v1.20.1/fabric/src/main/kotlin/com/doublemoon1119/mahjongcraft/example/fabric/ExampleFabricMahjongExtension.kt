package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.character.ExampleActorProviders
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleSeatPlacements
import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipTablePropKind
import com.doublemoon1119.mahjongcraft.example.minecraft.ExampleMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipantDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricRoomParticipantRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatPlacementRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricSeatProviderRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropKindRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtension

/** 由 Fabric entrypoint 發現，將同一個範例交給核心、Minecraft 與原生擴充登記流程。 */
class ExampleFabricMahjongExtension :
    ExampleMinecraftMahjongExtension(),
    FabricMahjongExtension {
    /**
     * 登記椅子與坐墊的解析能力；此時不讀取原生家具或世界。
     *
     * @param registry 本體提供的座位 registry。
     */
    override fun registerSeatProviders(registry: FabricSeatProviderRegistrar) {
        ExampleFurniture.seatProviders().forEach(registry::register)
    }

    /** 登記範例攤牌籌碼的桌面物件種類。 */
    override fun registerTablePropKinds(registry: FabricTablePropKindRegistrar) {
        registry.register(ExampleHandRevealChipTablePropKind)
    }

    /** 方塊椅子與實體坐墊皆可由手持物品放置在 AI 席位。 */
    override fun registerSeatPlacements(registry: FabricSeatPlacementRegistrar) {
        ExampleSeatPlacements.definitions().forEach(registry::register)
    }

    /** 只登記範例自己的人偶資格，不將其他原版或第三方生物納入。 */
    override fun registerRoomParticipants(registry: FabricRoomParticipantRegistrar) {
        registry.register(FabricRoomParticipantDefinition(ExampleCharacterIds.ACTOR, ExampleActorProviders.PARTICIPANT))
    }

    /** 借用與自有能力分開登記，不在初始化時生成實體。 */
    override fun registerAiCharacterCapabilities(registry: FabricAiCharacterCapabilityRegistrar) {
        registry.register(FabricAiCharacterCapabilityDefinition(ExampleCharacterIds.ACTOR, ExampleActorProviders.BORROWED))
        registry.register(FabricAiCharacterCapabilityDefinition(ExampleCharacterIds.OWNED_ACTOR, ExampleActorProviders.OWNED))
    }
}
