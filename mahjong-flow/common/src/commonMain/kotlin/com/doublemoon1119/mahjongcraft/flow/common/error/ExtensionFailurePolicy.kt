package com.doublemoon1119.mahjongcraft.flow.common.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * 判斷呼叫擴充提供的程式碼時發生的失敗，能否隔離後改用該呼叫點的失敗處理繼續執行。
 *
 * 可以隔離的失敗只代表那段擴充程式碼無法使用，其餘流程照常進行；協程取消與代表執行環境已不可靠的嚴重錯誤
 * 不得隔離，呼叫點應將它們原樣往外傳。平台可依自己的錯誤型別擴大可隔離的範圍，例如 JVM 平台的類別連結錯誤。
 */
fun interface ExtensionFailurePolicy {
    /**
     * 判斷 [failure] 能否隔離。
     *
     * @param failure 呼叫擴充程式碼時拋出的失敗。
     * @return 可以記錄後改用失敗處理繼續執行時為 true。
     */
    fun isIsolatable(failure: Throwable): Boolean

    /** [Default] 所在的伴生物件。 */
    companion object {
        /** 只隔離一般例外，不隔離協程取消與任何錯誤。 */
        val Default: ExtensionFailurePolicy = ExtensionFailurePolicy { failure ->
            failure is Exception && failure !is CancellationException
        }
    }
}
