package com.doublemoon1119.mahjongcraft.platform.fabric.block.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModBlocks
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid

/**
 * 麻將桌 controller 的持久化狀態。
 *
 * 除穩定 [tableId] 外，也作為整桌呈現時間軸的擁有者：[presentationBusyUntilGameTime] 保存目前
 * 所有已排定呈現的最晚結束絕對 game time。它不需要 block entity ticker；即使 chunk 曾被卸載，
 * 下次查詢時直接與世界時間比較即可正確收斂。未來 GUI／HUD 或役種演出可在同一個呈現狀態
 * 上擴充具名 cue，但這裡不執行任何權威遊戲流程。
 */
class MahjongTableBlockEntity(
    pos: BlockPos,
    state: BlockState,
) : BlockEntity(ModBlocks.mahjongTableBlockEntity, pos, state) {
    /** 同時作為等待階段 `Room.id` 與開局後 `TableState.id` 的穩定識別碼。 */
    var tableId: Uuid = Uuid.random()
        private set

    /** 整桌呈現保持忙碌的最晚絕對 game time；`0` 代表沒有額外桌級呈現等待。 */
    var presentationBusyUntilGameTime: Long = 0L
        private set

    /** 將整桌呈現時間軸延長到 [endGameTime]，已有更晚結束時間時保留原值。 */
    fun extendPresentationUntil(endGameTime: Long) {
        if (endGameTime <= presentationBusyUntilGameTime) return
        presentationBusyUntilGameTime = endGameTime
        markDirty()
    }

    /** 以 [currentGameTime] 判斷整桌呈現時間軸是否尚未結束。 */
    fun isPresenting(currentGameTime: Long): Boolean = currentGameTime < presentationBusyUntilGameTime

    /**
     * 中途胡牌演出專用的第二條呈現時間軸，語意與 [presentationBusyUntilGameTime] 完全相同，只是**刻意
     * 不列入整桌忙碌判定**：已完成本局的玩家在播結算演出時，其他仍在本局中的玩家必須能照常摸打。
     *
     * 兩條時間軸各自單調遞增，因此同一條上的多筆演出天然依序播放，不需要另外維護佇列——這正是既有
     * 那條的運作方式（見各 scheduler 的 `earliestStartGameTime`）。
     */
    var continuingWinPresentationBusyUntilGameTime: Long = 0L
        private set

    /** 將中途胡牌呈現時間軸延長到 [endGameTime]，已有更晚結束時間時保留原值。 */
    fun extendContinuingWinPresentationUntil(endGameTime: Long) {
        if (endGameTime <= continuingWinPresentationBusyUntilGameTime) return
        continuingWinPresentationBusyUntilGameTime = endGameTime
        markDirty()
    }

    /** 以 [currentGameTime] 判斷中途胡牌呈現時間軸是否尚未結束。 */
    fun isPresentingContinuingWin(currentGameTime: Long): Boolean = currentGameTime < continuingWinPresentationBusyUntilGameTime

    /** 從方塊實體 NBT 還原穩定 UUID；損壞或缺失時保留新生成的 UUID。 */
    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        nbt.getString(NBT_KEY_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
            ?.let { restored -> tableId = restored }
        presentationBusyUntilGameTime = nbt.getLong(NBT_KEY_PRESENTATION_BUSY_UNTIL_GAME_TIME)
        continuingWinPresentationBusyUntilGameTime = nbt.getLong(NBT_KEY_CONTINUING_WIN_PRESENTATION_BUSY_UNTIL_GAME_TIME)
    }

    /** 把穩定 UUID 寫入方塊實體 NBT。 */
    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt.putString(NBT_KEY_TABLE_ID, tableId.toString())
        nbt.putLong(NBT_KEY_PRESENTATION_BUSY_UNTIL_GAME_TIME, presentationBusyUntilGameTime)
        nbt.putLong(NBT_KEY_CONTINUING_WIN_PRESENTATION_BUSY_UNTIL_GAME_TIME, continuingWinPresentationBusyUntilGameTime)
    }

    /** 麻將桌方塊實體 NBT 欄位名稱。 */
    private companion object {
        const val NBT_KEY_TABLE_ID: String = "TableId"
        const val NBT_KEY_PRESENTATION_BUSY_UNTIL_GAME_TIME: String = "PresentationBusyUntilGameTime"
        const val NBT_KEY_CONTINUING_WIN_PRESENTATION_BUSY_UNTIL_GAME_TIME: String = "ContinuingWinPresentationBusyUntilGameTime"
    }
}
