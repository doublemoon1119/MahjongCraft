package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的正式立直棒（千分棒）呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property riichiSeatIndices 目前立直中的座位 index 集合（`MahjongRuleModule.isPlayerInRiichi`）；空集合
 * 代表這局目前沒有人立直，等同只清除舊立直棒、不生成新的。
 */
data class MahjongRiichiStickPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val riichiSeatIndices: Set<Int>,
)

/** 正式立直棒呈現請求的處理結果。 */
enum class MahjongRiichiStickPresentationResult {
    /** 已把這桌的立直棒更新為本次要呈現的座位集合（或 [MahjongRiichiStickPresentation.riichiSeatIndices] 為空、只清除舊立直棒）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成新立直棒 entity 失敗（例如世界拒絕 spawn）；已生成的部分會被回滾，舊立直棒維持不變。 */
    SPAWN_FAILED,
}

/**
 * 將權威立直棒呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 立直棒生命週期綁在**立直宣告**的時間點——玩家宣告立直就生成一支放在自己面前，換局（不論上一局
 * 有沒有人立直）一律清空，跟 [MahjongScoringStickPresenter]（積棒，綁在牌牆生成時間點）各自獨立。
 *
 * 立直棒沒有 domain 層身分（不像牌有 `IdentifiedTile.id`），比照 [MahjongScoringStickPresenter] 的
 * 按需生成模式：每次 [present] 都用 vanilla entity 隨機 UUID 生成本次要呈現的座位集合，新的全部生成
 * 成功後才清除舊的。
 */
interface MahjongRiichiStickPresenter {
    /** 在指定桌面呈現這桌目前立直中的座位；[MahjongRiichiStickPresentation.riichiSeatIndices] 為空時等同只清除舊立直棒。 */
    fun present(presentation: MahjongRiichiStickPresentation): MahjongRiichiStickPresentationResult

    /** 清除指定桌子目前的正式立直棒；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
