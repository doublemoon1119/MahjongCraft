package com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * 測試用的 [CoroutineDispatchers] 實作。若未提供替代調度器，四種調度器參數各自預設為
 * [UnconfinedTestDispatcher]；
 * 協程啟動時會立即進入執行，方便在測試中直接觀察同步部分的結果。這些預設值不模擬多執行緒，
 * 也不代表掛起後的工作必定在原呼叫處完成；需要其他調度行為時可透過建構子替換。
 *
 * @property default 一般工作使用的調度器，預設為 [UnconfinedTestDispatcher]。
 * @property io I/O 工作使用的調度器，預設為 [UnconfinedTestDispatcher]。
 * @property main 主執行緒工作使用的調度器，預設為 [UnconfinedTestDispatcher]。
 * @property aiDecision AI 決策使用的調度器，預設為 [UnconfinedTestDispatcher]。
 * @property aiDecisionParallelism AI 決策平行度上限值，預設為 1；實際限制由使用此設定的呼叫端套用。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCoroutineDispatchers(
    override val default: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val io: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val main: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val aiDecision: CoroutineDispatcher = UnconfinedTestDispatcher(),
    override val aiDecisionParallelism: Int = 1,
) : CoroutineDispatchers
