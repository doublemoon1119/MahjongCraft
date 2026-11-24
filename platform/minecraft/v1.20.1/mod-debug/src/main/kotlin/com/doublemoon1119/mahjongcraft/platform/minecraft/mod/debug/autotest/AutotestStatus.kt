package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 自動測試一個步驟或一項檢查的結果。 */
@Serializable
internal enum class AutotestStatus {
    /** 通過。 */
    @SerialName("passed")
    PASSED,

    /** 失敗。 */
    @SerialName("failed")
    FAILED,

    /** 沒有執行：沒有選擇這個步驟。 */
    @SerialName("skipped")
    SKIPPED,

    /** 執行到一半遊戲就被關閉。 */
    @SerialName("aborted")
    ABORTED,
}
