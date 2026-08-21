package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的桌面中央局況顯示資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property prevalentWind 目前場風（圈風），例如東風戰的東圈。
 * @property localRoundNumber 目前場風內的第幾局（`1` 起算，由呼叫端依 `TableState.roundNumber` 與
 * 玩家人數換算好才傳入——這裡不重新做這個換算，維持純粹的呈現格式化職責）。
 * @property comboCount 本場數（連莊次數），恆等於 `TableState.comboCount`。
 * @property wallRemainingCount 牌山目前剩餘張數，恆等於 `TableState.tileWall.remainingCount`。
 */
data class MahjongRoundInfoPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val prevalentWind: Wind,
    val localRoundNumber: Int,
    val comboCount: Int,
    val wallRemainingCount: Int,
)

/** 正式桌面局況顯示呈現請求的處理結果。 */
enum class MahjongRoundInfoPresentationResult {
    /** 已把這桌的局況顯示更新為本次要呈現的內容。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 生成局況顯示 entity 失敗（例如世界拒絕 spawn）。 */
    SPAWN_FAILED,
}

/**
 * 將桌面中央局況顯示（莊家風位、局數、本場數、牌山剩餘）呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 每張桌子固定只有一個常駐 entity，找不到既有的才生成新的（不是每次都清除重建，比照
 * [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter] 的「找到、
 * 更新」慣例，而不是 [com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter]
 * 那種按需生成模式——局況顯示沒有「整批換新」的需求，通常只是文字內容更新）。
 */
interface MahjongRoundInfoPresenter {
    /** 在指定桌面呈現目前局況；找不到既有 entity 時建立一個。 */
    fun present(presentation: MahjongRoundInfoPresentation): MahjongRoundInfoPresentationResult

    /** 清除指定桌子目前的局況顯示；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
