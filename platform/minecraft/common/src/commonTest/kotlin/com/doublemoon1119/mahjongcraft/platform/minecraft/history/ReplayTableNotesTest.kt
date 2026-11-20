package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ScatteredDiscardLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableDiscardArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.table.testRoundRandomness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證牌譜保存的牌桌擺放附註。 */
class ReplayTableNotesTest {
    /** 附註寫成 JSON 後讀回內容相同；寬度為 0 的座位不寫。 */
    @Test
    fun `notes round trip through saved text`() {
        val notes = ReplayTableNotes(
            extraWallReach = setOf(
                TileWallPlacement(TileWallPosition(1, 3, 0)),
                TileWallPlacement(TileWallPosition(1, 4, 1), TileWallPlacementOffset(towardTableCenterTiles = 0.5)),
            ),
            scatterPatternSeed = 42,
            cornerWidthBySeat = mapOf(0 to 0.0, 2 to 0.375),
        )

        val decoded = ReplayTableNotes.decode(assertNotNull(notes.encode()).toString())

        assertEquals(notes.copy(cornerWidthBySeat = mapOf(2 to 0.375)), decoded)
    }

    /** 沒有內容的附註不寫，讀回沒有附註時得到空附註。 */
    @Test
    fun `empty notes are omitted`() {
        assertNull(ReplayTableNotes.EMPTY.encode())
        assertEquals(ReplayTableNotes.EMPTY, ReplayTableNotes.decode(null))
    }

    /** 格式不符的附註不使用。 */
    @Test
    fun `malformed notes are rejected`() {
        assertNull(ReplayTableNotes.decode("[]"))
        assertNull(ReplayTableNotes.decode("""{"unknown":1}"""))
        assertNull(ReplayTableNotes.decode("""{"scatterSeed":"1"}"""))
        assertNull(ReplayTableNotes.decode("""{"cornerWidths":{"-1":0.5}}"""))
        assertNull(ReplayTableNotes.decode("""{"wallReach":[-1]}"""))
    }

    /** 記錄的讓位範圍只包含目前沒有牌牆牌的格位，與規則回報的範圍合起來相同；散放牌河記下這一局的位置表種子。 */
    @Test
    fun `recorder saves reach beyond the wall and the scatter seed`() {
        val ruleModuleId = "mahjongcraft:riichi"
        val modules = MahjongModuleRegistryImpl().apply {
            register(MahjongModuleDefinition(RiichiRuleConfig::class, ruleModuleId) { config, id -> RiichiRuleModule(id, config) })
        }
        val module = RiichiRuleModule(ruleModuleId, RiichiRuleConfig())
        val state = GameInitializer.initialize(Uuid.random(), List(4) { Uuid.random() }, module, randomness = testRoundRandomness()).tableState
        val standard = ReplayTableNotesRecorder(modules, TableTileLayoutProfileRegistryImpl(), TablePropDescriberRegistryImpl()).notes(state)
        val scatteredProfiles = TableTileLayoutProfileRegistryImpl().apply {
            register(TableTileLayoutProfileDefinition(ruleModuleId, TableTileLayoutProfile(discardArea = TableDiscardArea.SCATTERED)))
        }
        val scattered = ReplayTableNotesRecorder(modules, scatteredProfiles, TablePropDescriberRegistryImpl()).notes(state)

        val current = assertNotNull(state.physicalWallLayout).placements.values.toSet()
        val reach = assertNotNull(module.createPhysicalWallLayoutPolicy()).roundReach(state)
        assertTrue(standard.extraWallReach.none(current::contains))
        assertEquals(reach, current + standard.extraWallReach)
        assertNull(standard.scatterPatternSeed)
        assertEquals(ScatteredDiscardLayout.roundPatternSeed(state.id, state.roundSerial), scattered.scatterPatternSeed)
        assertTrue(standard.cornerWidthBySeat.values.all { it == 0.0 })
    }
}
