package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.flow.replay.ReplayCapacityFixtures
import java.io.ByteArrayOutputStream
import java.util.logging.Logger
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 以 Minecraft 這層實際附上的呈現附註編碼固定對局，確認正式牌譜仍在容量上限內，附註內容與規則提供的相同。
 *
 * flow 的容量測試不附任何呈現附註。這裡用 [ReplayTableNotesRecorder] 與內建日麻的登記，量出遊戲中實際存下的大小。
 */
class MinecraftReplayCapacityTest {
    /** 用於轉換內建規則資料的 persistence registries。 */
    private val registries = bundledPersistenceRegistries()

    /** 內建規則模組。 */
    private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }

    /** 內建日麻的本局資訊內容。 */
    private val roundInfoLines = RoundInfoLineDisplayRegistryImpl().apply { BundledRiichiMinecraftExtension.registerRoundInfoPresentations(this) }

    /** 讀寫牌面所用的網路資料登記。 */
    private val networkRegistries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() }

    /** 內建日麻的桌上物件描述。 */
    private val tablePropDescribers = TablePropDescriberRegistryImpl().apply { BundledRiichiMinecraftExtension.registerTablePropDescribers(this) }

    /** 與遊戲中相同的呈現附註來源。 */
    private val recorder = ReplayTableNotesRecorder(
        moduleRegistry = moduleRegistry,
        tileLayoutProfiles = TableTileLayoutProfileRegistryImpl().apply { BundledRiichiMinecraftExtension.registerTableTileLayoutProfiles(this) },
        tablePropDescribers = tablePropDescribers,
        roundInfoLines = roundInfoLines,
        componentRegistry = ComponentRegistryImpl(),
        networkRegistries = networkRegistries,
    )

    /** 附上呈現附註後，每場固定對局的正式牌譜都在 JSON 與 gzip 上限內。 */
    @Test
    fun `fixed matches with presentation notes stay within replay capacity`() {
        ReplayCapacityFixtures.all.forEachIndexed { index, fixture ->
            val text = CompactReplayCodec.encodeCompact(fixture.events, HistoryRecordingPersistenceMapper(registries), registries, recorder).toString()
            val jsonBytes = text.toByteArray().size
            val gzipBytes = gzipSize(text)
            logger.info("Replay capacity with notes, fixture $index (${fixture.gameLength}): json=$jsonBytes gzip=$gzipBytes")
            assertTrue(jsonBytes <= MAX_JSON_BYTES, "Compact Replay JSON with notes exceeds the limit: $jsonBytes bytes")
            assertTrue(gzipBytes <= MAX_GZIP_BYTES, "Compact Replay gzip with notes exceeds the limit: $gzipBytes bytes")
        }
    }

    /** 實際對局開局桌況的附註，帶有規則提供的本局資訊、玩家公開標記與桌上物件，並能讀回相同內容。 */
    @Test
    fun `notes carry the panels and props the rule provides`() {
        val module = moduleRegistry.getModule(firstTable().config)
        val tables = ReplayCapacityFixtures.all.map { fixture -> fixture.events.firstNotNullOf { (it.fact as? HistoryFact.MatchStarted)?.tableState } }
        tables.forEach { table ->
            val notes = recorder.notes(table)

            assertEquals(roundInfoLines.buildLines(module.id, table), notes.roundInfo)
            assertTrue(notes.roundInfo.isNotEmpty())
            assertEquals(
                expected = table.players.withIndex().associate { (seat, player) -> seat to module.getPublicPlayerIndicators(table, player) }.filterValues { it.isNotEmpty() },
                actual = notes.playerIndicators,
            )
            assertEquals(tablePropDescribers.find(module.id)?.describe(table) { null }.orEmpty(), notes.tableProps)
            // 寬度為 0 的座位不寫進牌譜。
            val saved = notes.copy(cornerWidthBySeat = notes.cornerWidthBySeat.filterValues { it != 0.0 })
            assertEquals(saved, ReplayTableNotes.decode(notes.encode(networkRegistries)?.toString(), networkRegistries))
        }
    }

    /**
     * 第一場固定對局的開局桌況。
     *
     * @return 開局桌況。
     */
    private fun firstTable(): TableState = ReplayCapacityFixtures.east1.events.firstNotNullOf { (it.fact as? HistoryFact.MatchStarted)?.tableState }

    /**
     * 計算 gzip 壓縮後的大小。
     *
     * @param text 原文。
     * @return 壓縮後的位元組數。
     */
    private fun gzipSize(text: String): Int {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray()) }
        return output.size()
    }

    /** 容量上限，與 flow 的容量測試相同。 */
    private companion object {
        /** JSON 上限。 */
        const val MAX_JSON_BYTES = 290_000

        /** gzip 上限。歷史資料庫以 gzip 預設等級壓縮存放牌譜，這也是單場牌譜的存檔大小上限。 */
        const val MAX_GZIP_BYTES = 40_000

        /** 量測結果的紀錄。 */
        val logger: Logger = Logger.getLogger(MinecraftReplayCapacityTest::class.java.name)
    }
}
