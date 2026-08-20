package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的正式積棒（連莊棒）呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index——積棒只在莊家角落顯示。
 * @property stickCount 該顯示的積棒支數，恆等於 `TableState.comboCount`；`0` 代表這局還沒連莊過，
 * 等同只清除舊積棒、不生成新的。
 */
data class MahjongScoringStickPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val dealerSeatIndex: Int,
    val stickCount: Int,
)

/** 正式積棒呈現請求的處理結果。 */
enum class MahjongScoringStickPresentationResult {
    /** 已把這桌的積棒更新為本次要呈現的支數（或 [MahjongScoringStickPresentation.stickCount] 為 `0`、只清除舊積棒）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成新積棒 entity 失敗（例如世界拒絕 spawn）；已生成的部分會被回滾，舊積棒維持不變。 */
    SPAWN_FAILED,
}

/**
 * 將權威積棒（連莊棒）數量呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 積棒數量整局固定（等於 `TableState.comboCount`，只有連莊/換局時才變），生命週期綁在**牌牆生成**
 * 的時間點——跟牌牆同時生成，每回合結束（換局）就刪除，新回合再重新生成，不是每次打牌/摸牌/鳴牌都
 * 觸發（那是 [MahjongPlayerAreaPresenter] 的職責，只把積棒支數當成算讓開寬度用的數字，不管理積棒
 * entity 本身）。
 *
 * 積棒沒有 domain 層身分（不像牌有 `IdentifiedTile.id`），比照骰子（`MahjongDiceRollPresenter`）的
 * 按需生成模式：每次 [present] 都用 vanilla entity 隨機 UUID 生成本次要呈現的支數，新的全部生成
 * 成功後才清除舊的；不是像 [MahjongTileWallPresenter] 那樣預先生成、之後只搬移既有 entity。
 */
interface MahjongScoringStickPresenter {
    /** 在指定桌面呈現這桌目前的積棒；[MahjongScoringStickPresentation.stickCount] 為 `0` 時等同只清除舊積棒。 */
    fun present(presentation: MahjongScoringStickPresentation): MahjongScoringStickPresentationResult

    /** 清除指定桌子目前的正式積棒；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
