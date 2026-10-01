package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證多型 persistence DTO 使用固定且具語意的 JSON discriminator 名稱。 */
class StablePolymorphicSerialNameTest {
    /** 驗證牌張 DTO 的所有分支名稱與 JSON 編解碼往返結果。 */
    @Test
    fun `tile variants use stable discriminator names`() {
        assertRoundTrip(TilePersistenceDto.serializer(), TilePersistenceDto.Numeric(SuitPersistenceDto.DOT, 5), "numeric")
        assertRoundTrip(TilePersistenceDto.serializer(), TilePersistenceDto.Extension("taiwan.plum"), "extension")
        assertRoundTrip(
            TilePersistenceDto.serializer(),
            TilePersistenceDto.Honor(HonorPersistenceValue.WHITE),
            "honor",
        )
    }

    /** 驗證遊戲動作 DTO 的所有分支名稱與 JSON 編解碼往返結果。 */
    @Test
    fun `game action variants use stable discriminator names`() {
        val typedValue = TypedPersistenceDto("test.value", buildJsonObject { })
        val actions = listOf(
            GameActionPersistenceDto.GameStarted to "game_started",
            GameActionPersistenceDto.RoundStarted to "round_started",
            GameActionPersistenceDto.MatchEnded to "match_ended",
            GameActionPersistenceDto.DiceRolled(listOf(1, 6)) to "dice_rolled",
            GameActionPersistenceDto.Draw to "draw",
            GameActionPersistenceDto.Discard("tile-id") to "discard",
            GameActionPersistenceDto.Chi("tile-id", listOf("with-id")) to "chi",
            GameActionPersistenceDto.Pon("tile-id", listOf("with-id")) to "pon",
            GameActionPersistenceDto.Kan(KanTypePersistenceDto.OPEN_KAN, "tile-id", emptyList()) to "kan",
            GameActionPersistenceDto.Ron("tile-id") to "ron",
            GameActionPersistenceDto.Tsumo to "tsumo",
            GameActionPersistenceDto.Extension(typedValue) to "extension",
            GameActionPersistenceDto.Pass to "pass",
            GameActionPersistenceDto.ExhaustiveDraw(typedValue) to "exhaustive_draw",
        )

        actions.forEach { (action, serialName) ->
            assertRoundTrip(GameActionPersistenceDto.serializer(), action, serialName)
        }
    }

    /** 驗證副露種類 DTO 的所有分支名稱與 JSON 編解碼往返結果。 */
    @Test
    fun `meld type variants use stable discriminator names`() {
        val meldTypes = listOf(
            MeldTypePersistenceDto.Chi to "chi",
            MeldTypePersistenceDto.Pon to "pon",
            MeldTypePersistenceDto.OpenKan to "open_kan",
            MeldTypePersistenceDto.ClosedKan to "closed_kan",
            MeldTypePersistenceDto.AddedKan to "added_kan",
            MeldTypePersistenceDto.Extension("custom.meld") to "extension",
        )

        meldTypes.forEach { (meldType, serialName) ->
            assertRoundTrip(MeldTypePersistenceDto.serializer(), meldType, serialName)
        }
    }

    /**
     * 驗證指定 DTO 的型別識別字固定，且 JSON 編解碼保持值不變。
     *
     * @param T 待驗證的 DTO 型別。
     * @param serializer 對應多態階層的序列化器。
     * @param value 待驗證的資料值。
     * @param serialName 預期的穩定型別識別字。
     */
    private fun <T> assertRoundTrip(serializer: KSerializer<T>, value: T, serialName: String) {
        val encoded = Json.encodeToString(serializer, value)
        assertEquals(serialName, Json.parseToJsonElement(encoded).jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(value, Json.decodeFromString(serializer, encoded))
    }
}
