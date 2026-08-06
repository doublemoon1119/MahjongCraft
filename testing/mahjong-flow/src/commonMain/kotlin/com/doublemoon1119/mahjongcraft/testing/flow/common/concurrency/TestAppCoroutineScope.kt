package com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScopeImpl
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 建立一個用於測試的 [AppCoroutineScope]。
 *
 * 透過注入 [TestCoroutineDispatchers] 與選用的 [exceptionHandler]，
 * 可以確保在該作用域內啟動的協程遵循測試環境的同步行為，並自定義異常捕獲邏輯。
 */
fun createTestAppCoroutineScope(
    dispatchers: CoroutineDispatchers = TestCoroutineDispatchers(),
    exceptionHandler: CoroutineExceptionHandler? = null,
): AppCoroutineScope = AppCoroutineScopeImpl(dispatchers, exceptionHandler)
