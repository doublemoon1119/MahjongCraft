package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue

/**
 * 房間設定頁的草稿狀態機。
 *
 * 保存編輯中的設定、草稿建立當下的權威設定、驗證結果與「送出後要返回房間頁」的待處理旗標，並集中全部
 * 狀態轉換規則。不認識任何 Minecraft 型別，也不接觸 widget、頁面路由或網路——呼叫端讀取查詢方法後自行
 * 決定要如何呈現。
 *
 * 權威設定在編輯途中變動時的判定見 [onAuthoritativeChanged]。
 */
internal class RoomSettingsDraft {
    /** 目前編輯中的設定；尚未進入設定頁或房間已清空時為 `null`。 */
    var config: GameConfig? = null
        private set

    /** 建立草稿當下的權威設定，用來判斷權威變更是否與草稿衝突。 */
    private var authoritativeAtStart: GameConfig? = null

    /** 權威設定已在編輯途中變動，且無法自動採用。 */
    var isStale: Boolean = false
        private set

    /** 送出後仍在等待權威回覆，回覆到達時要返回房間頁。 */
    var isReturningToRoomAfterApply: Boolean = false
        private set

    /** 目前未通過驗證的欄位 ID。 */
    private val invalidFieldIds = mutableSetOf<String>()

    /** 是否有任何欄位未通過驗證。 */
    val hasInvalidFields: Boolean
        get() = invalidFieldIds.isNotEmpty()

    /**
     * 判斷指定欄位目前是否未通過驗證。
     *
     * @param fieldId 欄位識別碼。
     * @return 已記錄為無效欄位時為 true。
     */
    fun isFieldInvalid(fieldId: String): Boolean = fieldId in invalidFieldIds

    /**
     * 判斷草稿與權威設定是否不同。
     *
     * @param authoritative 目前權威設定；未知時為 null。
     * @return 草稿與權威設定都存在且內容不同時為 true。
     */
    fun hasUnsavedChanges(authoritative: GameConfig?): Boolean {
        val draft = config
        return draft != null && authoritative != null && draft != authoritative
    }

    /**
     * 草稿規則的人數上限是否少於房間目前的 [roomPlayerCount] 人（含 AI）；此時伺服器會拒絕套用。
     * 人數不足不算：之後還能補人，開始對局時另有檢查。
     *
     * @param roomPlayerCount 房間目前人數，包含 AI。
     * @return 已有草稿且人數超過該規則上限時為 true。
     */
    fun exceedsPlayerLimit(roomPlayerCount: Int): Boolean = config?.let { roomPlayerCount > it.ruleConfig.maxPlayers } == true

    /**
     * 判斷草稿是否有變更且可送出。
     *
     * @param authoritative 目前權威設定。
     * @param roomPlayerCount 房間目前人數，包含 AI。
     * @return 草稿有變更、未過期、驗證通過且未超過人數上限時為 true。
     */
    fun canApply(authoritative: GameConfig?, roomPlayerCount: Int): Boolean = hasUnsavedChanges(authoritative) && !isStale && !hasInvalidFields && !exceedsPlayerLimit(roomPlayerCount)

    /**
     * 判斷是否需要提供復原操作。
     *
     * @param authoritative 目前權威設定。
     * @return 存在變更、過期狀態或無效欄位時為 true。
     */
    fun canUndo(authoritative: GameConfig?): Boolean = hasUnsavedChanges(authoritative) || isStale || hasInvalidFields

    /**
     * 判斷是否可以重設為規則預設值。
     *
     * @param defaults 要比較的規則預設設定。
     * @return 草稿與預設設定都存在且內容不同時為 true。
     */
    fun canReset(defaults: GameConfig?): Boolean {
        val draft = config
        return draft != null && defaults != null && draft != defaults
    }

    /**
     * 判斷目前是否可以完成設定，不要求草稿有變更。
     *
     * @param roomPlayerCount 房間目前人數，包含 AI。
     * @return 未過期、沒有無效欄位且未超過人數上限時為 true。
     */
    fun canDone(roomPlayerCount: Int): Boolean = !isStale && !hasInvalidFields && !exceedsPlayerLimit(roomPlayerCount)

    /**
     * 進入設定頁時建立草稿；草稿與起始權威值都存在時不覆蓋。
     *
     * @param authoritative 用來建立草稿的權威設定。
     */
    fun beginIfAbsent(authoritative: GameConfig) {
        if (config != null && authoritativeAtStart != null) return
        config = authoritative
        authoritativeAtStart = authoritative
    }

    /**
     * 套用欄位的 updater 並更新驗證狀態。
     *
     * updater 拋出例外代表該值不合法，欄位標記為無效且草稿不變。
     *
     * @param field 要更新的欄位；沒有 updater 時不處理。
     * @param value 欄位的新值。
     */
    fun updateField(field: GameConfigFieldDefinition, value: GameConfigPresentationValue) {
        val updater = field.update ?: return
        val current = config ?: return
        val updated = runCatching { updater(current, value) }.getOrNull()
        applyFieldResult(field.id, updated)
    }

    /**
     * 以外部整理過的完整設定取代草稿內容。
     *
     * 只用於設定欄位變更後重新整理依規則而定的共用流程欄位，不會重設權威基準或驗證狀態；若欄位因規則設定
     * 改變而消失，也會移除該欄位留下的無效標記，避免不可見的欄位阻擋送出。
     *
     * @param updated 整理後的草稿設定。
     * @param availableFieldIds 目前呈現中的欄位識別碼；省略時保留所有驗證標記。
     */
    fun replaceConfig(updated: GameConfig, availableFieldIds: Set<String>? = null) {
        if (config != null) {
            config = updated
            availableFieldIds?.let { invalidFieldIds.retainAll(it) }
        }
    }

    /**
     * 直接輸入整數時的第一段驗證：判定輸入字串本身是否落在編輯器允許的範圍。
     *
     * 與 [applyNumericInput] 分成兩段，是因為超出範圍的輸入必須標記欄位無效但不得改動草稿——直接交給
     * updater 會讓它把值夾回合法範圍，使用者就看不出自己打錯了。
     *
     * @param field 要驗證的欄位。
     * @param editor 整數輸入的範圍與可空規格。
     * @param raw 尚未轉換的輸入文字；可空欄位允許空字串。
     * @return 是否通過；未通過時呼叫端不應再呼叫 [applyNumericInput]。
     */
    fun markNumericInput(
        field: GameConfigFieldDefinition,
        editor: GameConfigEditorSpec.IntegerInput,
        raw: String,
    ): Boolean {
        val number = raw.toIntOrNull()
        val valid = (raw.isEmpty() || number != null) && editor.accepts(GameConfigPresentationValue.IntegerValue(number))
        markFieldValidity(field.id, valid)
        return valid
    }

    /**
     * 在 [markNumericInput] 通過後套用 updater 並更新驗證狀態。
     *
     * @param field 要更新的欄位；沒有 updater 時不處理。
     * @param raw 已通過輸入驗證的文字。
     */
    fun applyNumericInput(field: GameConfigFieldDefinition, raw: String) {
        val updater = field.update ?: return
        val current = config ?: return
        val updated = runCatching {
            updater(current, GameConfigPresentationValue.IntegerValue(raw.toIntOrNull()))
        }.getOrNull()
        applyFieldResult(field.id, updated)
    }

    /**
     * 重設為指定設定並清除無效欄位標記；不改變起始權威值與過期旗標。
     *
     * @param config 要採用的草稿設定。
     */
    fun resetTo(config: GameConfig) {
        this.config = config
        invalidFieldIds.clear()
    }

    /**
     * 放棄草稿並還原成權威設定，同時清除過期與待返回狀態。
     *
     * @param authoritative 要還原的權威設定。
     */
    fun restoreAuthoritative(authoritative: GameConfig) {
        config = authoritative
        authoritativeAtStart = authoritative
        invalidFieldIds.clear()
        isStale = false
        isReturningToRoomAfterApply = false
    }

    /** 標記收到權威回覆後要返回房間頁；本方法不負責送出草稿。 */
    fun markReturnToRoomAfterApply() {
        isReturningToRoomAfterApply = true
    }

    /**
     * 採用呼叫端已確認的權威回覆並清除待返回狀態；本方法不比對草稿是否一致。
     *
     * @param authoritative 要採用的權威設定；null 會清除草稿與起始權威值。
     */
    fun adoptAuthoritative(authoritative: GameConfig?) {
        config = authoritative
        authoritativeAtStart = authoritative
        isReturningToRoomAfterApply = false
    }

    /**
     * 依草稿的一致性規則正規化一份設定。
     *
     * 供呼叫端準備規則預設值時使用，讓「重設為預設值」與 [canReset] 的比較對象跟草稿內容適用同一組約束。
     *
     * @param config 要套用一致性約束的設定。
     * @return 關閉旁觀時也隱藏旁觀者手牌的設定；其他設定保持不變。
     */
    fun normalized(config: GameConfig): GameConfig = config.withConsistentSpectatorVisibility()

    /** 房間清空時丟棄草稿、起始權威值與無效欄位；過期與待返回旗標不在此重設。 */
    fun clear() {
        config = null
        authoritativeAtStart = null
        invalidFieldIds.clear()
    }

    /**
     * 處理編輯途中權威設定變動。
     *
     * 只有「草稿還停在草稿建立當下的權威值」（使用者尚未編輯）或「草稿剛好等於新的權威值」（其他人改成
     * 與草稿相同的內容）兩種情況可以直接採用新值；其餘情況代表草稿與權威同時被改動，標記為過期並取消
     * 待返回狀態，交由使用者決定要放棄還是重新編輯。
     *
     * @param authoritative 新收到的權威設定。
     * @return 未變更、已採用或草稿過期的處理結果。
     */
    fun onAuthoritativeChanged(authoritative: GameConfig): Outcome {
        val previous = authoritativeAtStart ?: return Outcome.Unchanged
        if (authoritative == previous) return Outcome.Unchanged
        return if (config == previous || authoritative == config) {
            val wasReturning = isReturningToRoomAfterApply
            config = authoritative
            authoritativeAtStart = authoritative
            isStale = false
            isReturningToRoomAfterApply = false
            Outcome.Adopted(returnToRoom = wasReturning)
        } else {
            isStale = true
            isReturningToRoomAfterApply = false
            Outcome.BecameStale
        }
    }

    /**
     * 記錄欄位驗證結果。
     *
     * @param fieldId 欄位識別碼。
     * @param valid 是否通過驗證。
     */
    private fun markFieldValidity(fieldId: String, valid: Boolean) {
        if (valid) invalidFieldIds.remove(fieldId) else invalidFieldIds.add(fieldId)
    }

    /**
     * 套用 updater 結果：成功時更新草稿並保持一致性，失敗時標記欄位無效。
     *
     * @param fieldId 被更新的欄位識別碼。
     * @param updated 更新成功的設定；null 代表失敗。
     */
    private fun applyFieldResult(fieldId: String, updated: GameConfig?) {
        markFieldValidity(fieldId, updated != null)
        if (updated != null) config = updated.withConsistentSpectatorVisibility()
    }

    /** 關閉旁觀時同時關閉旁觀者手牌公開，避免草稿存在互相矛盾的設定。 */
    private fun GameConfig.withConsistentSpectatorVisibility(): GameConfig = if (
        flowConfig.spectatingPolicy == SpectatingPolicy.DISABLED
    ) {
        copy(flowConfig = flowConfig.copy(spectatorHandVisibility = SpectatorHandVisibility.HIDDEN))
    } else {
        this
    }

    /** [onAuthoritativeChanged] 的結果，由呼叫端決定對應的畫面反應。 */
    sealed interface Outcome {
        /** 權威設定沒有實質變動，不需要任何反應。 */
        data object Unchanged : Outcome

        /**
         * 已採用新的權威設定。
         *
         * @property returnToRoom 採用前是否正在等待套用後返回房間；由呼叫端執行返回。
         */
        data class Adopted(val returnToRoom: Boolean) : Outcome

        /** 草稿與權威同時被改動，草稿已標記為過期。 */
        data object BecameStale : Outcome
    }
}
