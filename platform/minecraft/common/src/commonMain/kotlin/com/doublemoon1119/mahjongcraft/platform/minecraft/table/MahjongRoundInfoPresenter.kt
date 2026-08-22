package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.module.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的桌面中央局況顯示資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property lines 要顯示的完整內容，恆等於呼叫端當下算好的 `MahjongRuleModule.getRoundInfoLines`
 * 結果，依序顯示——這個 entity 是「找到既有的就地更新」模式，呼叫端每次都要重新算好完整內容一起
 * 傳入，不能只在部分呼叫點帶上，否則沒帶的呼叫會把之前顯示的內容覆蓋回空清單。
 */
data class MahjongRoundInfoPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val lines: List<RoundInfoLine>,
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
 * 將桌面中央局況顯示呈現於 Minecraft 世界的版本 adapter 邊界——實際顯示什麼內容完全由規則模組決定
 * （見 `MahjongRuleModule.getRoundInfoLines`），這裡不假設任何固定欄位。
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
