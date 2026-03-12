package com.doublemoon1119.mahjongcraft.application.ports.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [CoroutineDispatchers] 的預設實作。
 *
 * 直接封裝 Kotlin Coroutines 標準庫提供的 [Dispatchers]。
 * 適用於大多數標準 JVM 環境或不需要特殊執行緒調度的場景。
 */
class DefaultCoroutineDispatchers : CoroutineDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
}
