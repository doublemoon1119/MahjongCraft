package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.ExampleExtensionIds
import com.doublemoon1119.mahjongcraft.example.fabric.ExampleFabricMahjongClientExtension
import com.doublemoon1119.mahjongcraft.example.fabric.ExampleFabricMahjongExtension
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterCapabilityDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipantDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterCapabilityRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricAiCharacterPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricRoomParticipantRegistrar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 正式 extension 回呼在原生內容初始化之前也能登記，不要求世界或 DI。 */
class ExampleActorRegistrationTest {
    /** 世界能力、自動入房及 client 預覽都使用同一組完整 ID。 */
    @Test
    fun `public extension callbacks register borrowed owned and client capabilities without creating entities`() {
        val capture = Capture()
        val extension = ExampleFabricMahjongExtension()
        val client = ExampleFabricMahjongClientExtension()
        extension.registerRoomParticipants(capture)
        extension.registerAiCharacterCapabilities(capture)
        client.registerCharacterPreviews(capture)
        assertEquals(ExampleExtensionIds.EXTENSION, client.id)
        assertEquals(listOf(ExampleCharacterIds.ACTOR), capture.participants.map { it.id })
        assertEquals(listOf(ExampleCharacterIds.ACTOR, ExampleCharacterIds.OWNED_ACTOR), capture.world.map { it.id })
        assertEquals(listOf(ExampleCharacterIds.ACTOR), capture.previews.map { it.id })
        assertSame(ExampleActorProviders.PARTICIPANT, capture.participants.single().provider)
        assertSame(ExampleActorProviders.BORROWED, capture.world.first().provider)
        assertSame(ExampleActorProviders.OWNED, capture.world.last().provider)
    }

    /** 分開收集三種公開回呼，只觀察登記結果，不執行工廠。 */
    private class Capture :
        FabricRoomParticipantRegistrar,
        FabricAiCharacterCapabilityRegistrar,
        FabricAiCharacterPreviewRegistrar {
        /** 已收集的既有實體資格。 */
        val participants = mutableListOf<FabricRoomParticipantDefinition>()

        /** 已收集的原生世界能力。 */
        val world = mutableListOf<FabricAiCharacterCapabilityDefinition>()

        /** 已收集的獨立預覽能力。 */
        val previews = mutableListOf<FabricAiCharacterPreviewDefinition>()

        override val registrationKeys: Set<String> get() = (participants.map { it.id } + world.map { it.id } + previews.map { it.id }).toSet()

        override val isFrozen: Boolean = false

        override fun register(definition: FabricRoomParticipantDefinition) {
            participants += definition
        }

        override fun register(definition: FabricAiCharacterCapabilityDefinition) {
            world += definition
        }

        override fun register(definition: FabricAiCharacterPreviewDefinition) {
            previews += definition
        }
    }
}
