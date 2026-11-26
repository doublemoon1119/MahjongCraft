package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterMatchAppearance
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證牌譜回放依保存的角色描述選擇 AI 的畫法。 */
class HistoryReplayCharacterAppearanceTest {
    /** 內建機器人畫出開局時選定的牌面。 */
    @Test
    fun `built-in mascot keeps the face chosen for the match`() {
        val captured = AiCharacterMatchAppearance.capture(
            definition = BuiltInAiCharacters.TILE_MASCOT,
            matchId = Uuid.random(),
            playerId = Uuid.random(),
        )
        val face = assertIs<AiCharacterFace.Tile>(captured?.face).assetKey
        assertFalse(HistoryReplayCharacterAppearance.usesPreviewModel(captured))
        assertEquals(expected = face, actual = HistoryReplayCharacterAppearance.botFace(captured))
    }

    /** 其他預覽能力的角色改用自己的模型。不以牌面當頭像時備援的機器人用未知牌面。 */
    @Test
    fun `third-party characters use their preview model`() {
        val definition = AiCharacterDefinition(
            id = "example:character",
            nameKey = "example.character",
            face = AiCharacterFace.TextureRegion(resourceId = "example:textures/face.png", u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f),
            worldCapabilityId = null,
            preview = AiCharacterPreview("example:preview", emptyMap()),
        )
        assertTrue(HistoryReplayCharacterAppearance.usesPreviewModel(definition))
        assertEquals(expected = UNKNOWN_TILE_ASSET_KEY, actual = HistoryReplayCharacterAppearance.botFace(definition))
    }

    /** 沒有保存描述或沒有預覽能力時畫成內建機器人。 */
    @Test
    fun `missing description or preview falls back to the mascot`() {
        val definition = AiCharacterDefinition(
            id = "example:plain",
            nameKey = "example.plain",
            face = AiCharacterFace.Tile("east"),
            worldCapabilityId = null,
            preview = null,
        )
        assertFalse(HistoryReplayCharacterAppearance.usesPreviewModel(null))
        assertEquals(expected = UNKNOWN_TILE_ASSET_KEY, actual = HistoryReplayCharacterAppearance.botFace(null))
        assertFalse(HistoryReplayCharacterAppearance.usesPreviewModel(definition))
        assertEquals(expected = "east", actual = HistoryReplayCharacterAppearance.botFace(definition))
    }
}
