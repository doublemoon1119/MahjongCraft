package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 伺服器自動測試一個步驟的檢查紀錄與等待條件的操作。全部操作都在伺服器主執行緒上執行。 */
internal class AutotestServerContext {
    /** 這個步驟記錄的檢查，依發生順序。 */
    val checks = mutableListOf<AutotestCheck>()

    /**
     * 記錄一項檢查。
     *
     * @param name 檢查內容。
     * @param passed 是否通過。
     * @param detail 補充說明，例如失敗時的實際狀態。
     * @return [passed]。
     */
    fun check(name: String, passed: Boolean, detail: String? = null): Boolean {
        checks += AutotestCheck(name, passed, detail)
        return passed
    }

    /**
     * 每個遊戲刻檢查一次條件，直到成立或逾時。
     *
     * @param timeout 最長等待時間。
     * @param condition 要等待的條件。
     * @return 在時限內成立時為 true。
     */
    suspend fun waitUntil(timeout: Duration, condition: suspend () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (!condition()) delay(TICK)
        true
    } ?: false

    private companion object {
        /** 一個遊戲刻的時間。 */
        val TICK = 50.milliseconds
    }
}
