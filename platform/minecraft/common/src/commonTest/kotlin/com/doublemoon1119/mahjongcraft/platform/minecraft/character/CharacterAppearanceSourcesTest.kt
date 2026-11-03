package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 角色預設來源與第三方都經相同的 registry 優先序，不以快照捷徑覆蓋。 */
class CharacterAppearanceSourcesTest {
    /** 內建機器人可提供模型，但第三方仍能明確指定頭像。 */
    @Test
    fun `explicit room portrait wins over the default character model`() {
        val registry = RoomMemberAppearanceSourceRegistryImpl(ExtensionFailurePolicy.Default)
        BuiltInMinecraftMahjongExtension.registerRoomMemberAppearanceSources(registry)
        val context = RoomMemberAppearanceContext(Uuid.random(), true, BuiltInAiCharacters.TILE_MASCOT)
        assertEquals(RoomMemberAppearanceSource.ActorPreview(BuiltInAiCharacters.TILE_MASCOT_CAPABILITY_ID), registry.resolve(context))
        assertEquals(RoomMemberAppearanceSource.WorldEntityModel, registry.resolve(context.copy(hasLiveModel = true)))
        registry.register(RoomMemberAppearanceSourceDefinition("example:portrait", priority = 0) { RoomMemberAppearanceSource.Portrait })
        registry.freeze()
        assertEquals(RoomMemberAppearanceSource.Portrait, registry.resolve(context))
        assertEquals(RoomMemberAppearanceSource.Portrait, registry.resolve(context.copy(hasLiveModel = true)))
    }

    /** 即使角色已提供 FACE，優先序較高的頭像來源仍先選用。 */
    @Test
    fun `custom portrait wins even when the character supplies a face`() {
        val registry = PlayerPortraitSourceRegistryImpl(ExtensionFailurePolicy.Default)
        BuiltInMinecraftMahjongExtension.registerPlayerPortraitSources(registry)
        val context = PlayerPortraitSourceContext(Uuid.random(), true, BuiltInAiCharacters.TILE_MASCOT)
        assertEquals(BuiltInAiCharacters.PORTRAIT_SOURCE_ID, registry.resolve(context)?.providerId)
        registry.register(PlayerPortraitSourceDefinition("example:portrait", priority = 0) { PlayerPortraitSource.TileFace("east") })
        registry.freeze()
        assertEquals("example:portrait", registry.resolve(context)?.providerId)
        assertEquals(PlayerPortraitSource.TileFace("east"), registry.resolve(context)?.source)
    }
}
