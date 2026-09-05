package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongAnimationSounds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證內建聲音事件與跨版本聲音資源保持完整對應。 */
class ModSoundResourceTest {
    /** 驗證每個自訂事件恰好引用一個實際存在的 OGG 資源。 */
    @Test
    fun `custom sound events reference one existing ogg resource`() {
        val soundsJson = checkNotNull(javaClass.getResourceAsStream("/assets/mahjongcraft/sounds.json"))
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }
        assertEquals(expectedSounds.keys.map { it.substringAfter(':') }.toSet(), soundsJson.keys)

        expectedSounds.forEach { (soundId, resourcePath) ->
            val eventPath = soundId.substringAfter(':')
            val event = checkNotNull(soundsJson[eventPath]) { "Missing sound event: $soundId" }.jsonObject
            val sounds = event.getValue("sounds").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(listOf(resourcePath), sounds, "Unexpected resources for $soundId")
            checkNotNull(javaClass.getResource("/assets/${resourcePath.substringBefore(':')}/sounds/${resourcePath.substringAfter(':')}.ogg")) {
                "Missing OGG resource: $resourcePath"
            }
        }

        expectedSounds.values.toSet().forEach(::assertMonoFortyEightKilohertzVorbis)
    }

    /** 驗證指定資源的 Vorbis identification header 宣告單聲道與 48 kHz。 */
    private fun assertMonoFortyEightKilohertzVorbis(resourcePath: String) {
        val path = "/assets/${resourcePath.substringBefore(':')}/sounds/${resourcePath.substringAfter(':')}.ogg"
        val bytes = checkNotNull(javaClass.getResourceAsStream(path)).use { it.readBytes() }
        val headerIndex = bytes.findSubsequence(byteArrayOf(1, 'v'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'b'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte()))
        assertTrue(headerIndex >= 0, "Missing Vorbis identification header: $resourcePath")
        assertEquals(1, bytes[headerIndex + VORBIS_CHANNEL_OFFSET].toInt(), "Sound must be mono: $resourcePath")
        val sampleRate = (0 until INT_BYTE_COUNT).fold(0) { value, byteIndex ->
            value or ((bytes[headerIndex + VORBIS_SAMPLE_RATE_OFFSET + byteIndex].toInt() and BYTE_MASK) shl (byteIndex * BITS_PER_BYTE))
        }
        assertEquals(EXPECTED_SAMPLE_RATE, sampleRate, "Sound must use 48 kHz: $resourcePath")
    }

    /** 尋找 [subsequence] 在位元組陣列中的第一個起點；找不到時回傳 `-1`。 */
    private fun ByteArray.findSubsequence(subsequence: ByteArray): Int = indices.firstOrNull { startIndex ->
        startIndex + subsequence.size <= size && subsequence.indices.all { index -> this[startIndex + index] == subsequence[index] }
    } ?: -1

    /** 自訂聲音事件與其單一資源路徑。 */
    private val expectedSounds: Map<String, String> = linkedMapOf(
        MahjongAnimationSounds.DICE_LAND to "mahjongcraft:dice/land",
        MahjongAnimationSounds.SCORING_STICK_PLACE to "mahjongcraft:tile/draw_land",
        MahjongAnimationSounds.DEAL_BATCH to "mahjongcraft:tile/deal_batch",
        MahjongAnimationSounds.TILE_DISCARD_LAND to "mahjongcraft:tile/discard_land",
        MahjongAnimationSounds.DORA_REVEAL to "mahjongcraft:tile/draw_land",
        MahjongAnimationSounds.DRAW_TILE_LAND to "mahjongcraft:tile/draw_land",
        MahjongAnimationSounds.TILE_HAND_TURN to "mahjongcraft:tile/hand_turn",
        MahjongAnimationSounds.TILE_MELD_LAND to "mahjongcraft:tile/meld_land",
        MahjongAnimationSounds.WALL_STACK_LAND to "mahjongcraft:tile/wall_stack_land",
        MahjongAnimationSounds.WIN_LIGHTNING to "mahjongcraft:win/lightning",
    )

    private companion object {
        /** Vorbis identification header 中聲道數相對 packet type 的位移。 */
        const val VORBIS_CHANNEL_OFFSET: Int = 11

        /** Vorbis identification header 中 sample rate 相對 packet type 的位移。 */
        const val VORBIS_SAMPLE_RATE_OFFSET: Int = 12

        /** 32-bit little-endian 整數的位元組數。 */
        const val INT_BYTE_COUNT: Int = 4

        /** 一個位元組包含的位元數。 */
        const val BITS_PER_BYTE: Int = 8

        /** 將有號 Byte 轉為無號數值的遮罩。 */
        const val BYTE_MASK: Int = 0xFF

        /** 正式聲音資源必須使用的 sample rate。 */
        const val EXPECTED_SAMPLE_RATE: Int = 48_000
    }
}
