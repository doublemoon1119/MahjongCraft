package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestCheck
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestStatus
import kotlinx.serialization.Serializable

/**
 * 一次宣傳截圖的結果，寫成輸出資料夾的 `showcase.json`。
 *
 * @property startedAt 開始時間，ISO-8601 格式。
 * @property durationMillis 總耗時，毫秒。
 * @property status 全部檢查都通過時為通過；有檢查失敗或發生例外時為失敗；遊戲在途中關閉時為中止。
 * @property site 擺放牌桌的位置。還沒找到時為 null。
 * @property shots 拍到的截圖，依拍攝順序。
 * @property checks 過程中的檢查。
 * @property error 中斷拍攝的例外。沒有時為 null。
 */
@Serializable
internal data class ShowcaseManifest(
    val startedAt: String,
    val durationMillis: Long,
    val status: AutotestStatus,
    val site: ShowcasePosition?,
    val shots: List<ShowcaseShot>,
    val checks: List<AutotestCheck>,
    val error: String?,
)

/**
 * 一張宣傳截圖與它的鏡頭。
 *
 * @property name 截圖名稱。
 * @property file 原始截圖，相對於輸出資料夾。
 * @property eye 鏡頭位置。
 * @property yaw 鏡頭的水平角度，與原版相同：0 朝南。
 * @property pitch 鏡頭的俯仰角度，正值朝下。
 * @property outputs 後製產生的檔案，相對於輸出資料夾。
 */
@Serializable
internal data class ShowcaseShot(
    val name: String,
    val file: String,
    val eye: ShowcasePosition,
    val yaw: Float,
    val pitch: Float,
    val outputs: List<String>,
)

/**
 * 世界中的位置。
 *
 * @property x x 座標。
 * @property y y 座標。
 * @property z z 座標。
 */
@Serializable
internal data class ShowcasePosition(val x: Double, val y: Double, val z: Double)
