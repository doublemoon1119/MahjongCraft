package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayPresentationNotes
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.cornerWidthsBySeat
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ScatteredDiscardLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableDiscardArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistry
import kotlinx.serialization.json.JsonObject

/**
 * 牌譜保存時，依每份桌況寫下 [ReplayTableNotes]，算法與對局中擺牌時相同。
 *
 * @property moduleRegistry 依規則設定取得規則模組。
 * @property tileLayoutProfiles 規則的桌面牌位配置。
 * @property tablePropDescribers 規則的桌上物件描述。
 */
class ReplayTableNotesRecorder(
    private val moduleRegistry: MahjongModuleRegistry,
    private val tileLayoutProfiles: TableTileLayoutProfileRegistry,
    private val tablePropDescribers: TablePropDescriberRegistry,
) : ReplayPresentationNotes {
    override fun notesFor(state: TableState): JsonObject? = notes(state).encode()

    /**
     * 算出一份桌況的擺放附註。
     *
     * @param state 交易完成後的權威桌況。
     * @return 擺放附註。
     */
    fun notes(state: TableState): ReplayTableNotes {
        val module = moduleRegistry.getModule(state.config)
        val current = state.physicalWallLayout?.placements?.values?.toSet().orEmpty()
        val reach = module.createPhysicalWallLayoutPolicy()?.roundReach(state).orEmpty()
        val scattered = tileLayoutProfiles.profileFor(module.id).discardArea == TableDiscardArea.SCATTERED
        return ReplayTableNotes(
            extraWallReach = reach - current,
            scatterPatternSeed = if (scattered) ScatteredDiscardLayout.roundPatternSeed(state.id, state.roundSerial) else null,
            cornerWidthBySeat = tablePropDescribers.find(module.id).cornerWidthsBySeat(state),
        )
    }
}
