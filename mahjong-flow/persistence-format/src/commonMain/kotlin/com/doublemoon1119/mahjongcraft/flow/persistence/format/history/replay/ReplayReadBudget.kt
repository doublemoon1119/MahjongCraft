package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 讀取 Replay 時集中執行資源上限與取消檢查的內部記帳器。
 * @property limits 每次獨立讀取的資源上限。
 * @property checkCancellation 檢查執行作業是否已取消；取消例外直接傳播。
 */
internal class ReplayReadBudget(
    internal val limits: ReplayReadLimits,
    private val checkCancellation: () -> Unit,
) {
    /** 目前讀取作業已消耗的工作單位。 */
    private var workUnits = 0L

    /** 上次取消檢查後累積的工作單位。 */
    private var unitsSinceCancellation = 0L

    /** 投影樹累積展開的節點數。 */
    private var expandedNodes = 0L

    /** 累積展開字串的 UTF-8 位元組數。 */
    private var expandedStringBytes = 0L

    /** 累積重建的交易數。 */
    private var transactionsRebuilt = 0

    /** 累積檢查的輸入節點數。 */
    private var inputNodes = 0L

    init {
        checkCancellation()
    }

    /** 記帳指定數量的工作單位並定期檢查取消狀態。
     * @param units 要記帳的工作單位數。
     */
    fun charge(units: Long = 1L) {
        require(units >= 0L)
        workUnits = checkedAdd(workUnits, units)
        if (workUnits > limits.maxWorkUnits) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        unitsSinceCancellation = checkedAdd(unitsSinceCancellation, units)
        while (unitsSinceCancellation >= limits.cancellationInterval) {
            checkCancellation()
            unitsSinceCancellation -= limits.cancellationInterval
        }
    }

    /** 驗證輸入 JSON 的節點與深度限制。
     * @param element 待檢查的輸入 JSON 樹。
     */
    fun inspectInput(element: JsonElement) {
        val stack = mutableListOf(element to 0)
        while (stack.isNotEmpty()) {
            val (current, depth) = stack.removeAt(stack.lastIndex)
            if (depth >= limits.maxDepth) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            inputNodes = checkedAdd(inputNodes, 1L)
            if (inputNodes > limits.maxInputNodes) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            charge()
            when (current) {
                is JsonObject -> {
                    preflightStack(stack.size, current.size, limits.maxInputNodes - inputNodes)
                    current.values.forEach { stack += it to depth + 1 }
                }
                is JsonArray -> {
                    preflightStack(stack.size, current.size, limits.maxInputNodes - inputNodes)
                    current.forEach { stack += it to depth + 1 }
                }
                is JsonPrimitive -> Unit
            }
        }
    }

    /** 驗證投影 JSON 的存活節點與深度；既有字串不重複記為展開配置。
     * @param element 待檢查的投影 JSON 樹。
     */
    fun inspectProjection(element: JsonElement) {
        val stack = mutableListOf<ProjectionFrame>(ProjectionFrame(element, 0))
        var treeNodes = 0L
        while (stack.isNotEmpty()) {
            val frame = stack.removeAt(stack.lastIndex)
            if (frame.depth >= limits.maxDepth) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            treeNodes = checkedAdd(treeNodes, 1L)
            if (treeNodes > limits.maxProjectionNodes) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            charge()
            when (val current = frame.element) {
                is JsonObject -> {
                    preflightStack(stack.size, current.size, limits.maxProjectionNodes - treeNodes)
                    current.values.toList().asReversed().forEach { stack += ProjectionFrame(it, frame.depth + 1) }
                }
                is JsonArray -> {
                    preflightStack(stack.size, current.size, limits.maxProjectionNodes - treeNodes)
                    current.asReversed().forEach { stack += ProjectionFrame(it, frame.depth + 1) }
                }
                is JsonPrimitive -> Unit
            }
        }
    }

    /** 驗證牌索引並記帳一個牌參照。
     * @param index 牌種索引。
     * @param catalogSize 牌種目錄大小。
     * @return 原索引。
     */
    fun tile(index: Int, catalogSize: Int): Int {
        charge()
        if (index !in 0 until catalogSize || catalogSize > limits.maxTiles) throw ReplayReadException(ReplayReadError.INVALID_DOCUMENT)
        return index
    }

    /** 驗證座位索引並記帳一個玩家座位。
     * @param index 座位索引。
     * @param seatCount 座位數。
     * @return 原索引。
     */
    fun seat(index: Int, seatCount: Int): Int {
        charge()
        if (index !in 0 until seatCount || seatCount > limits.maxPlayers) throw ReplayReadException(ReplayReadError.INVALID_DOCUMENT)
        return index
    }

    /** 記帳一個展開字串的 UTF-8 大小。
     * @param value 待計算大小的字串。
     */
    fun expandedString(value: String) {
        var bytes = 0L
        var index = 0
        while (index < value.length) {
            val character = value[index]
            bytes = checkedAdd(
                bytes,
                when {
                    character.code <= 0x7f -> 1L
                    character.code <= 0x7ff -> 2L
                    character.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate() -> {
                        index++
                        4L
                    }
                    else -> 3L
                },
            )
            if (checkedAdd(expandedStringBytes, bytes) > limits.maxExpandedStringBytes) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
            expandedStringBytes = checkedAdd(expandedStringBytes, bytes)
            bytes = 0L
            charge()
            index++
        }
    }

    /** 記帳一個已重建的交易。 */
    fun transactionRebuilt() {
        if (transactionsRebuilt == Int.MAX_VALUE) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        transactionsRebuilt++
    }

    /** 取得目前讀取作業的統計資料。 */
    fun diagnostics(): ReplayReadDiagnostics = ReplayReadDiagnostics(workUnits, expandedNodes, expandedStringBytes, transactionsRebuilt)

    /** 記帳一個實際展開的 JSON 節點，不將重複檢查計入配置統計。 */
    internal fun expandedNode() {
        expandedNodes = checkedAdd(expandedNodes, 1L)
    }

    /** 在推入子節點前檢查待處理節點數不會超過輸入上限。
     * @param stackSize 目前堆疊中的節點數。
     * @param childCount 即將推入的子節點數。
     * @param maximumNodes 此遍歷允許的節點上限。
     */
    private fun preflightStack(stackSize: Int, childCount: Int, maximumNodes: Long) {
        val pending = checkedAdd(stackSize.toLong(), childCount.toLong())
        if (pending > maximumNodes) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
    }

    /** 執行不溢位的非負整數加法。
     * @param left 左運算元。
     * @param right 右運算元。
     * @return 加法結果。
     */
    private fun checkedAdd(left: Long, right: Long): Long {
        if (right > Long.MAX_VALUE - left) throw ReplayReadException(ReplayReadError.LIMIT_EXCEEDED)
        return left + right
    }

    /** 投影遍歷的一個堆疊框架。
     * @property element 框架對應的 JSON 節點。
     * @property depth 節點在樹中的深度。
     */
    private data class ProjectionFrame(val element: JsonElement, val depth: Int)
}

/** 讀取格式或限制失敗時使用的內部例外。
 * @property error 有型別的失敗原因。
 */
internal class ReplayReadException(val error: ReplayReadError) : IllegalArgumentException(error.message)

/** Replay 錯誤種類的英文錯誤訊息。 */
private val ReplayReadError.message: String
    get() = when (this) {
        ReplayReadError.INVALID_DOCUMENT -> "Invalid replay document"
        ReplayReadError.UNSUPPORTED_CONTENT -> "Unsupported replay content"
        ReplayReadError.LIMIT_EXCEEDED -> "Replay read limit exceeded"
        ReplayReadError.SELECTION_NOT_FOUND -> "Replay selection not found"
    }
