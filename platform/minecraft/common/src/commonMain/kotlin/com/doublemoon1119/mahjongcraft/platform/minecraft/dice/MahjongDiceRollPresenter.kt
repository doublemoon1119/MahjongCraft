package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的單顆正式骰子資料。
 *
 * @property point 最終朝上的點數。
 * @property animationSeed 確定性動畫使用的 seed。
 */
data class MahjongDicePresentation(
    val point: Int,
    val animationSeed: Long,
) {
    init {
        require(point in VALID_POINT_RANGE) { "Dice point must be between 1 and 6" }
    }

    /** 骰子點數合法範圍。 */
    private companion object {
        val VALID_POINT_RANGE: IntRange = 1..6
    }
}

/**
 * 正式擲骰呈現所需的版本無關資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property throwSide 擲骰者相對麻將桌的局部側面。
 * @property rollSequence 同桌遞增的投擲序號，用來輪替安全 layout。
 * @property dice 由伺服器權威流程決定的骰子點數與動畫 seed；目前支援兩顆或三顆。
 * @property extraLeadDelayTicks 這次投擲動畫在每顆骰子自己的動畫佇列最前面該多等待的 tick 數（等
 * 牌牆掉落動畫播完才輪到擲骰），折算進每顆骰子自己的佇列，理由同 [MahjongInitialDealPresentation.extraLeadDelayTicks]。
 */
data class MahjongDiceRollPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val throwSide: MahjongTableSide,
    val rollSequence: Long,
    val dice: List<MahjongDicePresentation>,
    val extraLeadDelayTicks: Int = 0,
) {
    init {
        require(rollSequence >= 0) { "Roll sequence must not be negative" }
        require(dice.size in SUPPORTED_DICE_COUNTS) { "Dice presentation must contain two or three dice" }
    }

    /** 目前已提供安全桌面 layout 的骰子數量。 */
    private companion object {
        val SUPPORTED_DICE_COUNTS: IntRange = 2..3
    }
}

/** 正式骰子呈現請求的處理結果。 */
enum class MahjongDiceRollPresentationResult {
    /** 已替換同桌舊擲骰呈現，並建立所有桌面骰子及聚合結果舞台。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 任一桌面骰子或聚合結果舞台無法加入世界；已回滾本次建立的 entity。 */
    SPAWN_FAILED,
}

/**
 * 將權威擲骰結果呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供點數、動畫 seed、投入側與序號；entity、開始時間、舊骰替換及版本 API 均由實作處理。
 */
interface MahjongDiceRollPresenter {
    /** 在指定桌面呈現兩顆或三顆正式骰子。 */
    fun present(presentation: MahjongDiceRollPresentation): MahjongDiceRollPresentationResult

    /** 清除指定桌子目前的正式桌面骰子及聚合結果舞台；回傳實際移除的 entity 數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
