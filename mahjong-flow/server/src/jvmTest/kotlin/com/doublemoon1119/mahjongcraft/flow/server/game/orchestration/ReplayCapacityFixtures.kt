package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryOutboxEventPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * 提供 Replay 容量測試使用的固定權威歷史事件。
 *
 * 事件以 classpath 資源保存，避免容量測試依賴即時洗牌、擲骰、座位分配或 UUID 生成。
 */
internal object ReplayCapacityFixtures {
    /** 東風戰第一場固定權威歷史事件。 */
    val east1: Fixture = load("capacity/east-1.history.json.gz.base64", RiichiGameLength.East)

    /** 東風戰第二場固定權威歷史事件。 */
    val east2: Fixture = load("capacity/east-2.history.json.gz.base64", RiichiGameLength.East)

    /** 半莊第一場固定權威歷史事件。 */
    val twoWinds1: Fixture = load("capacity/two-winds-1.history.json.gz.base64", RiichiGameLength.TwoWinds)

    /** 半莊第二場固定權威歷史事件。 */
    val twoWinds2: Fixture = load("capacity/two-winds-2.history.json.gz.base64", RiichiGameLength.TwoWinds)

    /** 所有容量測試固定權威歷史事件，依東風戰、半莊及場次排列。 */
    val all: List<Fixture> = listOf(east1, east2, twoWinds1, twoWinds2)

    /**
     * 已解碼的單場固定權威歷史事件。
     *
     * @property gameLength 此場事件所使用的日麻對局長度。
     * @property events 依權威事件序號排列的完整歷史事件。
     */
    data class Fixture(
        val gameLength: RiichiGameLength,
        val events: List<HistoryOutboxEvent>,
    )

    /**
     * 從壓縮 Base64 classpath 資源載入並解碼固定歷史事件。
     *
     * @param resourcePath 固定事件資源的 classpath 路徑。
     * @param gameLength 此場事件的對局長度。
     * @return 已解碼的完整權威事件樣本。
     */
    private fun load(resourcePath: String, gameLength: RiichiGameLength): Fixture {
        val resource = checkNotNull(ReplayCapacityFixtures::class.java.classLoader.getResourceAsStream(resourcePath)) {
            "Replay capacity fixture resource not found: $resourcePath"
        }
        val encoded = resource.use { it.readBytes().toString(Charsets.UTF_8).trim() }
        val compressed = Base64.getDecoder().decode(encoded)
        val jsonText = GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
        val persistenceEvents = Json.decodeFromString(
            ListSerializer(HistoryOutboxEventPersistenceDto.serializer()),
            jsonText,
        )
        val mapper = HistoryRecordingPersistenceMapper(bundledPersistenceRegistries())
        return Fixture(gameLength, persistenceEvents.map(mapper::decodePendingEvent))
    }
}
