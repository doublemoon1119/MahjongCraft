package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropContent
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableSeatOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ScatteredDiscardLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableDiscardArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import com.doublemoon1119.mahjongcraft.testing.logic.table.testRoundRandomness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證牌譜保存的呈現附註。 */
class ReplayTableNotesTest {
    /** 讀寫桌上物件牌面所用的網路資料登記。 */
    private val registries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() }

    /** 附註寫成 JSON 後讀回內容相同；寬度為 0 的座位與沒有標記的座位不寫。 */
    @Test
    fun `notes round trip through saved text`() {
        val notes = ReplayTableNotes(
            extraWallReach = setOf(
                TileWallPlacement(TileWallPosition(1, 3, 0)),
                TileWallPlacement(TileWallPosition(1, 4, 1), TileWallPlacementOffset(towardTableCenterTiles = 0.5)),
            ),
            scatterPatternSeed = 42,
            cornerWidthBySeat = mapOf(0 to 0.0, 2 to 0.375),
            roundInfo = listOf(RoundInfoLine("mahjongcraft:test/title"), RoundInfoLine("mahjongcraft:test/remaining", listOf(RoundInfoArgument.Number(70)))),
            playerIndicators = mapOf(
                0 to emptyList(),
                1 to listOf(
                    PublicPlayerIndicator("mahjongcraft:test/marker"),
                    PublicPlayerIndicator("mahjongcraft:test/count", PublicPlayerIndicatorValue.Count(2)),
                    PublicPlayerIndicator("mahjongcraft:test/option", PublicPlayerIndicatorValue.Option("mahjongcraft:test/choice")),
                ),
            ),
            tableProps = listOf(
                TablePropPlacement(
                    id = "mahjongcraft:test/stick",
                    kind = "mahjongcraft:test/stick_kind",
                    content = TablePropContent.CustomVariant("thousand"),
                    location = TablePropLocation.Seat(seatIndex = 2, anchor = TableSeatAnchor.MELD_CORNER, offset = TableSeatOffset(x = 0.1, y = 0.0, z = -0.2), yawOffset = 90f),
                ),
                TablePropPlacement(
                    id = "mahjongcraft:test/indicator",
                    kind = "mahjongcraft:test/tile_kind",
                    content = TablePropContent.FaceUpTile(TileFaceView(Tile.Numeric(Tile.Suit.Character, 3))),
                    location = TablePropLocation.Center(order = 1, yaw = 180f),
                ),
            ),
            drawHandReveals = mapOf(0 to true, 1 to false),
        )

        val decoded = ReplayTableNotes.decode(assertNotNull(notes.encode(registries)).toString(), registries)

        assertEquals(notes.copy(cornerWidthBySeat = mapOf(2 to 0.375), playerIndicators = notes.playerIndicators.filterValues { it.isNotEmpty() }), decoded)
    }

    /** 沒有內容的附註不寫，讀回沒有附註時得到空附註。 */
    @Test
    fun `empty notes are omitted`() {
        assertNull(ReplayTableNotes.EMPTY.encode(registries))
        assertEquals(ReplayTableNotes.EMPTY, ReplayTableNotes.decode(null, registries))
    }

    /** 格式不符的附註不使用。 */
    @Test
    fun `malformed notes are rejected`() {
        listOf(
            "[]",
            """{"unknown":1}""",
            """{"scatterSeed":"1"}""",
            """{"cornerWidths":{"-1":0.5}}""",
            """{"wallReach":[-1]}""",
            """{"drawHands":{"0":"yes"}}""",
            """{"indicators":{"0":[["mahjongcraft:test/count","c",-1]]}}""",
            """{"indicators":{"x":[]}}""",
            """{"props":[["mahjongcraft:test/a","mahjongcraft:test/b",["v","x"],["s",0,"NOWHERE",0,0,0,0]]]}""",
            """{"props":[["mahjongcraft:test/a","mahjongcraft:test/b",["v","x"]]]}""",
        ).forEach { text -> assertNull(ReplayTableNotes.decode(text, registries), text) }
    }

    /** 記錄的讓位範圍只包含目前沒有牌牆牌的格位，與規則回報的範圍合起來相同；散放牌河記下這一局的位置表種子。 */
    @Test
    fun `recorder saves reach beyond the wall and the scatter seed`() {
        val modules = riichiModules()
        val module = RiichiRuleModule(RULE_MODULE_ID, RiichiRuleConfig())
        val state = GameInitializer.initialize(Uuid.random(), List(4) { Uuid.random() }, module, randomness = testRoundRandomness()).tableState
        val standard = recorder(modules, TableTileLayoutProfileRegistryImpl()).notes(state)
        val scatteredProfiles = TableTileLayoutProfileRegistryImpl().apply {
            register(TableTileLayoutProfileDefinition(RULE_MODULE_ID, TableTileLayoutProfile(discardArea = TableDiscardArea.SCATTERED)))
        }
        val scattered = recorder(modules, scatteredProfiles).notes(state)

        val current = assertNotNull(state.physicalWallLayout).placements.values.toSet()
        val reach = assertNotNull(module.createPhysicalWallLayoutPolicy()).roundReach(state)
        assertTrue(standard.extraWallReach.none(current::contains))
        assertEquals(reach, current + standard.extraWallReach)
        assertNull(standard.scatterPatternSeed)
        assertEquals(ScatteredDiscardLayout.roundPatternSeed(state.id, state.roundSerial), scattered.scatterPatternSeed)
        assertTrue(standard.cornerWidthBySeat.values.all { it == 0.0 })
    }

    /**
     * 建立只登記日麻的規則模組登記。
     *
     * @return 規則模組登記。
     */
    private fun riichiModules(): MahjongModuleRegistryImpl = MahjongModuleRegistryImpl().apply {
        register(MahjongModuleDefinition(RiichiRuleConfig::class, RULE_MODULE_ID) { config, id -> RiichiRuleModule(id, config) })
    }

    /**
     * 建立沒有本局資訊與桌上物件登記的附註來源。
     *
     * @param modules 規則模組登記。
     * @param profiles 桌面牌位配置登記。
     * @return 附註來源。
     */
    private fun recorder(modules: MahjongModuleRegistryImpl, profiles: TableTileLayoutProfileRegistryImpl): ReplayTableNotesRecorder = ReplayTableNotesRecorder(modules, profiles, TablePropDescriberRegistryImpl(), RoundInfoLineDisplayRegistryImpl(), ComponentRegistryImpl(), registries)

    /** 測試用的規則模組 ID。 */
    private companion object {
        /** 測試用的規則模組 ID。 */
        const val RULE_MODULE_ID = "mahjongcraft:riichi"
    }
}
