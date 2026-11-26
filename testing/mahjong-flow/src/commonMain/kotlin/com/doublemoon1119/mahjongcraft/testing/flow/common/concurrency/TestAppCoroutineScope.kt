package com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScopeImpl
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * 建立測試用的 [AppCoroutineScope]，預設使用 [TestCoroutineDispatchers] 提供的調度器。
 *
 * 可替換調度器，並選擇是否提供未捕獲例外的處理器。
 *
 * @param dispatchers 提供此作用域使用的協程調度器。
 * @param exceptionHandler 未捕獲例外的處理器；為 null 時使用協程的預設處理機制。
 */
fun createTestAppCoroutineScope(
    dispatchers: CoroutineDispatchers = TestCoroutineDispatchers(),
    exceptionHandler: CoroutineExceptionHandler? = null,
): AppCoroutineScope = AppCoroutineScopeImpl(dispatchers, exceptionHandler)
