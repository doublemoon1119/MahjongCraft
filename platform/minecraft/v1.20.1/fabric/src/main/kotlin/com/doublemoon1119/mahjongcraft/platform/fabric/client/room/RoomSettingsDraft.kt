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

    /** 指定欄位目前是否未通過驗證。 */
    fun isFieldInvalid(fieldId: String): Boolean = fieldId in invalidFieldIds

    /** 草稿與權威設定是否不同。 */
    fun hasUnsavedChanges(authoritative: GameConfig?): Boolean {
        val draft = config
        return draft != null && authoritative != null && draft != authoritative
    }

    /** 是否可以送出草稿。 */
    fun canApply(authoritative: GameConfig?): Boolean = hasUnsavedChanges(authoritative) && !isStale && !hasInvalidFields

    /** 是否可以放棄草稿還原成權威設定。 */
    fun canUndo(authoritative: GameConfig?): Boolean = hasUnsavedChanges(authoritative) || isStale || hasInvalidFields

    /** 是否可以重設為規則預設值。 */
    fun canReset(defaults: GameConfig?): Boolean {
        val draft = config
        return draft != null && defaults != null && draft != defaults
    }

    /** 是否可以結束設定。 */
    fun canDone(): Boolean = !isStale && !hasInvalidFields

    /** 進入設定頁時建立草稿；已有草稿時不覆蓋編輯中的內容。 */
    fun beginIfAbsent(authoritative: GameConfig) {
        if (config != null && authoritativeAtStart != null) return
        config = authoritative
        authoritativeAtStart = authoritative
    }

    /**
     * 套用欄位的 updater 並更新驗證狀態。
     *
     * updater 拋出例外代表該值不合法，欄位標記為無效且草稿不變。
     */
    fun updateField(field: GameConfigFieldDefinition, value: GameConfigPresentationValue) {
        val updater = field.update ?: return
        val current = config ?: return
        val updated = runCatching { updater(current, value) }.getOrNull()
        applyFieldResult(field.id, updated)
    }

    /**
     * 直接輸入整數時的第一段驗證：判定輸入字串本身是否落在編輯器允許的範圍。
     *
     * 與 [applyNumericInput] 分成兩段，是因為超出範圍的輸入必須標記欄位無效但不得改動草稿——直接交給
     * updater 會讓它把值夾回合法範圍，使用者就看不出自己打錯了。
     *
     * @return 是否通過；未通過時呼叫端不應再呼叫 [applyNumericInput]。
     */
    fun markNumericInput(
        field: GameConfigFieldDefinition,
        editor: GameConfigEditorSpec.IntegerInput,
        raw: String,
    ): Boolean {
        val number = raw.toIntOrNull()
        val valid = (editor.nullable && raw.isEmpty()) || number != null && number in editor.minimum..editor.maximum
        markFieldValidity(field.id, valid)
        return valid
    }

    /** [markNumericInput] 通過後的第二段：套用 updater 並更新驗證狀態。 */
    fun applyNumericInput(field: GameConfigFieldDefinition, raw: String) {
        val updater = field.update ?: return
        val current = config ?: return
        val updated = runCatching {
            updater(current, GameConfigPresentationValue.IntegerValue(raw.toIntOrNull()))
        }.getOrNull()
        applyFieldResult(field.id, updated)
    }

    /** 重設為指定設定；Reset 與切換規則共用，一併清除既有的無效欄位標記。 */
    fun resetTo(config: GameConfig) {
        this.config = config
        invalidFieldIds.clear()
    }

    /** 放棄草稿並還原成權威設定，同時清除過期與待返回狀態。 */
    fun restoreAuthoritative(authoritative: GameConfig) {
        config = authoritative
        authoritativeAtStart = authoritative
        invalidFieldIds.clear()
        isStale = false
        isReturningToRoomAfterApply = false
    }

    /** 送出草稿並等待權威回覆，回覆到達時返回房間頁。 */
    fun markReturnToRoomAfterApply() {
        isReturningToRoomAfterApply = true
    }

    /** 權威回覆已與草稿一致，採用它並清除待返回狀態。 */
    fun adoptAuthoritative(authoritative: GameConfig?) {
        config = authoritative
        authoritativeAtStart = authoritative
        isReturningToRoomAfterApply = false
    }

    /**
     * 依草稿的一致性規則正規化一份設定。
     *
     * 供呼叫端準備規則預設值時使用，讓「重設為預設值」與 [canReset] 的比較對象跟草稿內容適用同一組約束。
     */
    fun normalized(config: GameConfig): GameConfig = config.withConsistentSpectatorVisibility()

    /** 房間清空時丟棄草稿。 */
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

    /** 記錄欄位驗證結果。 */
    private fun markFieldValidity(fieldId: String, valid: Boolean) {
        if (valid) invalidFieldIds.remove(fieldId) else invalidFieldIds.add(fieldId)
    }

    /** 套用 updater 結果：成功時更新草稿並保持一致性，失敗時標記欄位無效。 */
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

        /** 已採用新的權威設定；[returnToRoom] 為 true 時代表這是送出後等待的回覆。 */
        data class Adopted(val returnToRoom: Boolean) : Outcome

        /** 草稿與權威同時被改動，草稿已標記為過期。 */
        data object BecameStale : Outcome
    }
}
