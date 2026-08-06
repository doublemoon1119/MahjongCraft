package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 定義協程 Dispatcher 的抽象介面 (Port)，遵循 Clean Architecture 原則。
 *
 * 此介面的主要職責是讓 `application` 層能夠以平台無關的方式宣告對協程調度器的需求。
 * 具體的 Dispatcher 實作 (Adapter) 將由 `platform` 層根據其執行環境（如 Minecraft Server Thread）提供。
 *
 * 此外，此抽象化設計也使得在單元測試中可以輕易地注入一個測試專用的 Dispatcher (如 `TestCoroutineDispatchers`)，
 * 從而實現對協程行為的精確控制與驗證。
 */
interface CoroutineDispatchers {
    /**
     * 預設的協程調度器，適用於 CPU 密集型計算任務。
     */
    val default: CoroutineDispatcher

    /**
     * I/O 密集型任務的協程調度器，例如檔案讀寫或網路請求。
     */
    val io: CoroutineDispatcher

    /**
     * 主執行緒或與平台 UI/主循環相關的協程調度器。
     */
    val main: CoroutineDispatcher
}
