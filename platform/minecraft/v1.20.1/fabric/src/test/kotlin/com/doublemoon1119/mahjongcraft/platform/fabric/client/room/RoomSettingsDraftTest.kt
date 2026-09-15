package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigFieldDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證房間設定草稿的狀態轉換與按鈕可用性判定。 */
class RoomSettingsDraftTest {
    /** 剛建立草稿且未編輯時，只有 Done 可用。 */
    @Test
    fun `offers only done before anything is edited`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)

        assertFalse(draft.hasUnsavedChanges(AUTHORITATIVE))
        assertFalse(draft.canApply(AUTHORITATIVE))
        assertFalse(draft.canUndo(AUTHORITATIVE))
        assertTrue(draft.canDone())
    }

    /** 已有草稿時再次進入設定頁不覆蓋編輯中的內容。 */
    @Test
    fun `keeps an existing draft when re-entering the page`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))

        draft.beginIfAbsent(AUTHORITATIVE)

        assertEquals(45, draft.config?.flowConfig?.preparationBaseSeconds)
    }

    /** 編輯欄位後 Apply 與 Undo 開啟，改回與權威相同時再次關閉。 */
    @Test
    fun `enables apply and undo only while the draft differs`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)

        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))
        assertTrue(draft.canApply(AUTHORITATIVE))
        assertTrue(draft.canUndo(AUTHORITATIVE))

        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(30))
        assertFalse(draft.canApply(AUTHORITATIVE))
        assertFalse(draft.canUndo(AUTHORITATIVE))
    }

    /** updater 拋例外時欄位標記為無效、草稿不變，Apply 與 Done 停用。 */
    @Test
    fun `marks the field invalid when the updater rejects the value`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)

        draft.updateField(rejectingField(), GameConfigPresentationValue.IntegerValue(1))

        assertTrue(draft.isFieldInvalid(REJECTING_FIELD_ID))
        assertTrue(draft.hasInvalidFields)
        assertEquals(AUTHORITATIVE, draft.config)
        assertFalse(draft.canApply(AUTHORITATIVE))
        assertFalse(draft.canDone())
        assertTrue(draft.canUndo(AUTHORITATIVE), "無效欄位時仍要能放棄草稿")
    }

    /** 同一欄位改回合法值後清除該欄位的無效標記。 */
    @Test
    fun `clears the invalid mark once the same field becomes valid`() {
        val draft = RoomSettingsDraft()
        val field = preparationSecondsField()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.markNumericInput(field, GameConfigEditorSpec.IntegerInput(minimum = 10, maximum = 60), "999")
        assertTrue(draft.isFieldInvalid(field.id))

        draft.markNumericInput(field, GameConfigEditorSpec.IntegerInput(minimum = 10, maximum = 60), "45")

        assertFalse(draft.isFieldInvalid(field.id))
        assertFalse(draft.hasInvalidFields)
        assertTrue(draft.canDone())
    }

    /** 編輯另一個欄位不會清除既有欄位的無效標記。 */
    @Test
    fun `keeps another field invalid while editing a different one`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(rejectingField(), GameConfigPresentationValue.IntegerValue(1))

        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))

        assertTrue(draft.isFieldInvalid(REJECTING_FIELD_ID))
        assertTrue(draft.hasInvalidFields)
        assertFalse(draft.canDone(), "仍有欄位無效時不可結束設定")
        assertEquals(45, draft.config?.flowConfig?.preparationBaseSeconds, "合法欄位的編輯仍會寫入草稿")
    }

    /** 第一段驗證擋下超出範圍的輸入，且不改動草稿。 */
    @Test
    fun `rejects an out of range numeric input without touching the draft`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        val field = preparationSecondsField()
        val editor = GameConfigEditorSpec.IntegerInput(minimum = 10, maximum = 60)

        assertFalse(draft.markNumericInput(field, editor, "999"))
        assertTrue(draft.isFieldInvalid(field.id))
        assertEquals(AUTHORITATIVE, draft.config)
        assertFalse(draft.canDone())
    }

    /** 第一段通過後第二段才套用 updater。 */
    @Test
    fun `applies the numeric input once it is in range`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        val field = preparationSecondsField()
        val editor = GameConfigEditorSpec.IntegerInput(minimum = 10, maximum = 60)

        assertTrue(draft.markNumericInput(field, editor, "45"))
        draft.applyNumericInput(field, "45")

        assertFalse(draft.isFieldInvalid(field.id))
        assertEquals(45, draft.config?.flowConfig?.preparationBaseSeconds)
        assertTrue(draft.canApply(AUTHORITATIVE))
    }

    /** 可為空的整數欄位接受空字串。 */
    @Test
    fun `accepts an empty string for a nullable integer field`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        val editor = GameConfigEditorSpec.IntegerInput(minimum = 10, maximum = 60, nullable = true)

        assertTrue(draft.markNumericInput(preparationSecondsField(), editor, ""))
    }

    /** 關閉旁觀時一併關閉旁觀者手牌公開。 */
    @Test
    fun `hides spectator hands when spectating is disabled`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)

        draft.updateField(spectatingPolicyField(), GameConfigPresentationValue.BooleanValue(false))

        assertEquals(SpectatingPolicy.DISABLED, draft.config?.flowConfig?.spectatingPolicy)
        assertEquals(SpectatorHandVisibility.HIDDEN, draft.config?.flowConfig?.spectatorHandVisibility)
    }

    /** 同一條一致性規則也套用在預設值上。 */
    @Test
    fun `normalizes a supplied config with the same rule`() {
        val draft = RoomSettingsDraft()
        val disabled = AUTHORITATIVE.copy(
            flowConfig = AUTHORITATIVE.flowConfig.copy(spectatingPolicy = SpectatingPolicy.DISABLED),
        )

        assertEquals(SpectatorHandVisibility.HIDDEN, draft.normalized(disabled).flowConfig.spectatorHandVisibility)
    }

    /** Reset 與規則切換清除既有的無效欄位標記。 */
    @Test
    fun `clears invalid marks when resetting`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(rejectingField(), GameConfigPresentationValue.IntegerValue(1))

        draft.resetTo(AUTHORITATIVE)

        assertFalse(draft.hasInvalidFields)
        assertFalse(draft.isFieldInvalid(REJECTING_FIELD_ID))
    }

    /** 權威設定沒有變動時不做任何反應。 */
    @Test
    fun `ignores an authoritative update that changes nothing`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)

        assertIs<RoomSettingsDraft.Outcome.Unchanged>(draft.onAuthoritativeChanged(AUTHORITATIVE))
        assertFalse(draft.isStale)
    }

    /** 使用者尚未編輯時直接採用新的權威設定。 */
    @Test
    fun `adopts the new authoritative config when the draft was untouched`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        val updated = withPreparationSeconds(45)

        val outcome = draft.onAuthoritativeChanged(updated)

        assertEquals(RoomSettingsDraft.Outcome.Adopted(returnToRoom = false), outcome)
        assertEquals(updated, draft.config)
        assertFalse(draft.isStale)
    }

    /** 送出後收到與草稿一致的權威回覆時採用並要求返回房間頁。 */
    @Test
    fun `reports the pending return once the applied config comes back`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))
        draft.markReturnToRoomAfterApply()

        val outcome = draft.onAuthoritativeChanged(withPreparationSeconds(45))

        assertEquals(RoomSettingsDraft.Outcome.Adopted(returnToRoom = true), outcome)
        assertFalse(draft.isReturningToRoomAfterApply)
        assertFalse(draft.isStale)
    }

    /** 草稿與權威同時被改動時標記為過期並取消待返回狀態。 */
    @Test
    fun `marks the draft stale when both sides changed`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))
        draft.markReturnToRoomAfterApply()

        val outcome = draft.onAuthoritativeChanged(withPreparationSeconds(20))

        assertIs<RoomSettingsDraft.Outcome.BecameStale>(outcome)
        assertTrue(draft.isStale)
        assertFalse(draft.isReturningToRoomAfterApply)
        assertEquals(45, draft.config?.flowConfig?.preparationBaseSeconds, "過期時草稿內容保持不變")
    }

    /** 過期後只剩 Undo 可用。 */
    @Test
    fun `leaves only undo available once stale`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))
        draft.onAuthoritativeChanged(withPreparationSeconds(20))
        val authoritative = withPreparationSeconds(20)

        assertFalse(draft.canApply(authoritative))
        assertFalse(draft.canDone())
        assertTrue(draft.canUndo(authoritative))
    }

    /** Undo 還原權威設定並清除過期與待返回狀態。 */
    @Test
    fun `restores the authoritative config and clears every pending flag`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(rejectingField(), GameConfigPresentationValue.IntegerValue(1))
        draft.updateField(preparationSecondsField(), GameConfigPresentationValue.IntegerValue(45))
        draft.markReturnToRoomAfterApply()
        draft.onAuthoritativeChanged(withPreparationSeconds(20))

        draft.restoreAuthoritative(AUTHORITATIVE)

        assertEquals(AUTHORITATIVE, draft.config)
        assertFalse(draft.isStale)
        assertFalse(draft.hasInvalidFields)
        assertFalse(draft.isReturningToRoomAfterApply)
    }

    /** 房間清空時丟棄草稿。 */
    @Test
    fun `drops the draft when the room is cleared`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)
        draft.updateField(rejectingField(), GameConfigPresentationValue.IntegerValue(1))

        draft.clear()

        assertNull(draft.config)
        assertFalse(draft.hasInvalidFields)
        assertIs<RoomSettingsDraft.Outcome.Unchanged>(draft.onAuthoritativeChanged(withPreparationSeconds(45)))
    }

    /** Reset 只在草稿與預設值不同時可用。 */
    @Test
    fun `enables reset only when the draft differs from the defaults`() {
        val draft = RoomSettingsDraft()
        draft.beginIfAbsent(AUTHORITATIVE)

        assertFalse(draft.canReset(AUTHORITATIVE))
        assertFalse(draft.canReset(null))
        assertTrue(draft.canReset(withPreparationSeconds(45)))
    }

    private companion object {
        /** 測試用的權威設定。 */
        val AUTHORITATIVE = GameConfig(RiichiRuleConfig(), GameFlowConfig())

        /** 一定會被 updater 拒絕的欄位 ID。 */
        const val REJECTING_FIELD_ID = "mahjongcraft:rejecting"

        /** 依開局準備秒數建立權威設定。 */
        fun withPreparationSeconds(seconds: Int): GameConfig = AUTHORITATIVE.copy(flowConfig = AUTHORITATIVE.flowConfig.copy(preparationBaseSeconds = seconds))

        /** 可更新開局準備秒數的測試欄位。 */
        fun preparationSecondsField(): GameConfigFieldDefinition = GameConfigFieldDefinition(
            id = "mahjongcraft:preparation_seconds",
            categoryId = "mahjongcraft:flow",
            nameTranslationKey = "test.name",
            descriptionTranslationKey = "test.description",
            editor = GameConfigEditorSpec.IntegerInput(minimum = 10, maximum = 60),
            isEditable = true,
            read = { GameConfigPresentationValue.IntegerValue(it.flowConfig.preparationBaseSeconds) },
            update = { config, value ->
                val seconds = (value as GameConfigPresentationValue.IntegerValue).number ?: error("missing number")
                config.copy(flowConfig = config.flowConfig.copy(preparationBaseSeconds = seconds))
            },
        )

        /** 可切換旁觀開關的測試欄位。 */
        fun spectatingPolicyField(): GameConfigFieldDefinition = GameConfigFieldDefinition(
            id = "mahjongcraft:spectating",
            categoryId = "mahjongcraft:flow",
            nameTranslationKey = "test.name",
            descriptionTranslationKey = "test.description",
            editor = GameConfigEditorSpec.BooleanToggle,
            isEditable = true,
            read = { GameConfigPresentationValue.BooleanValue(it.flowConfig.spectatingPolicy == SpectatingPolicy.ENABLED) },
            update = { config, value ->
                val enabled = (value as GameConfigPresentationValue.BooleanValue).enabled
                config.copy(
                    flowConfig = config.flowConfig.copy(
                        spectatingPolicy = if (enabled) SpectatingPolicy.ENABLED else SpectatingPolicy.DISABLED,
                    ),
                )
            },
        )

        /** updater 一律拋例外的測試欄位。 */
        fun rejectingField(): GameConfigFieldDefinition = GameConfigFieldDefinition(
            id = REJECTING_FIELD_ID,
            categoryId = "mahjongcraft:flow",
            nameTranslationKey = "test.name",
            descriptionTranslationKey = "test.description",
            editor = GameConfigEditorSpec.IntegerInput(minimum = 0, maximum = 10),
            isEditable = true,
            read = { GameConfigPresentationValue.IntegerValue(0) },
            update = { _, _ -> error("always rejects") },
        )
    }
}
