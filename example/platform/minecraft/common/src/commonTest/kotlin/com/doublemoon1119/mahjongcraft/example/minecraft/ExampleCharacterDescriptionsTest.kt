package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AiCharacterRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshotCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 共用範例提供明確 FACE、模型備援及自有能力的描述，不觸及原生類別。 */
class ExampleCharacterDescriptionsTest {
    /** 三種能力各有獨立描述，FACE-only 不要求世界或預覽。 */
    @Test
    fun `character descriptions register before native initialization and preserve optional capabilities`() {
        val captured = mutableListOf<AiCharacterDefinition>()
        ExampleMinecraftMahjongExtension().registerAiCharacters(
            registry = object : AiCharacterRegistrar {
                override val registrationKeys: Set<String> get() = captured.map { it.id }.toSet()
                override val isFrozen: Boolean = false
                override fun register(definition: AiCharacterDefinition) {
                    captured += definition
                }
            },
        )
        assertEquals(ExampleCharacters.definitions(), captured)
        assertNull(ExampleCharacters.ACTOR.face)
        assertNotNull(ExampleCharacters.ACTOR.preview)
        assertNotNull(ExampleCharacters.ACTOR.worldCapabilityId)
        assertNotNull(ExampleCharacters.OWNED_ACTOR.worldCapabilityId)
        assertIs<AiCharacterFace.TextureRegion>(ExampleCharacters.FACE_ONLY.face)
        assertNull(ExampleCharacters.FACE_ONLY.worldCapabilityId)
        assertNull(ExampleCharacters.FACE_ONLY.preview)
    }

    /** 歷史保存公開描述及當場名稱，沒有本人或目前 registry 仍能還原。 */
    @Test
    fun `historical actor snapshots preserve their captured appearance and name without a live entity`() {
        val codec = AiCharacterSnapshotCodec()
        ExampleCharacters.definitions().forEach { definition ->
            val original = AiCharacterSnapshot(
                characterId = definition.id,
                definition = definition,
                displayName = "Archived actor",
            )
            val restored = codec.decode(codec.encode(original))
            assertEquals(original, restored)
        }
    }
}
