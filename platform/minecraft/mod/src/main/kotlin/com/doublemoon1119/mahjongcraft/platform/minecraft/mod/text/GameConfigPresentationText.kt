package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.network.chat.Component

/**
 * 將 RoomScreen 與 `/mahjongcraft room config` 共用的設定值轉成本地化文字。
 *
 * @param value 要顯示的設定值。
 * @param editor 選項呈現定義。未提供選項翻譯鍵時會顯示完整選項 ID。
 * @return 對應設定值的 Minecraft 文字。
 */
fun gameConfigPresentationText(
    value: GameConfigPresentationValue,
    editor: GameConfigEditorSpec? = null,
): Component = when (value) {
    is GameConfigPresentationValue.BooleanValue -> Component.translatable(
        if (value.enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE,
    )
    is GameConfigPresentationValue.IntegerValue -> value.number?.let { Component.literal(it.toString()) }
        ?: Component.translatable(MinecraftRoomScreenKeys.NONE)
    is GameConfigPresentationValue.ChoiceValue -> {
        val translationKey = (editor as? GameConfigEditorSpec.SingleChoice)?.translationKeyFor(value.optionId)
        translationKey?.let(Component::translatable) ?: Component.literal(value.optionId)
    }
    is GameConfigPresentationValue.MultiChoiceValue -> {
        val multiChoice = editor as? GameConfigEditorSpec.MultiChoice
        val orderedIds = if (multiChoice == null) {
            value.optionIds.sorted()
        } else {
            multiChoice.optionIds.filter { it in value.optionIds } +
                (value.optionIds - multiChoice.optionIds.toSet()).sorted()
        }
        if (orderedIds.isEmpty()) {
            Component.translatable(MinecraftRoomScreenKeys.NONE)
        } else {
            orderedIds.map { optionId ->
                multiChoice?.translationKeyFor(optionId)?.let(Component::translatable) ?: Component.literal(optionId)
            }.reduce { text, optionText -> text.append(", ").append(optionText) }
        }
    }
}
