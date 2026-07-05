package com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher


/**
 * 測試用的 CoroutineDispatchers 實作。
 * 所有的 Dispatcher 都使用 [UnconfinedTestDispatcher]，這使得在測試中協程會立即執行，
 * 避免了多執行緒帶來的複雜性，簡化了測試邏輯。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineDispatchers(
    override val default: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val io: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val main: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : CoroutineDispatchers
