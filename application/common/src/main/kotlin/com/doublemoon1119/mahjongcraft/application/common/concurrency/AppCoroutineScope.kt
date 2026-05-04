package com.doublemoon1119.mahjongcraft.application.common.concurrency

import kotlinx.coroutines.CoroutineScope

/**
 * 應用層全局協程作用域管理器。
 *
 * 此介面負責提供一個受控的 [CoroutineScope]，用於執行那些不與特定生命週期組件綁定的長期異步任務。
 * 遵循 Clean Architecture 原則，將協程作用域的生命週期管理抽象化。
 */
interface AppCoroutineScope : CoroutineScope {
    /**
     * 停止此作用域下的所有任務並釋放資源。
     * 通常應在插件卸載或應用程式關閉時呼叫。
     */
    fun cancel()
}
