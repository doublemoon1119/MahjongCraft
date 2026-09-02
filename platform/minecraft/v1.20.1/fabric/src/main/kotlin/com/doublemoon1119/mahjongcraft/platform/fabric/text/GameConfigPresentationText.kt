package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.text.Text

/** 將 RoomScreen 與 `/mahjongcraft room config` 共用的設定值轉成本地化文字。 */
fun gameConfigPresentationText(value: GameConfigPresentationValue): Text = when (value) {
    is GameConfigPresentationValue.BooleanValue -> Text.translatable(
        if (value.enabled) MinecraftRoomScreenKeys.TRUE else MinecraftRoomScreenKeys.FALSE,
    )
    is GameConfigPresentationValue.IntegerValue -> value.number?.let { Text.literal(it.toString()) }
        ?: Text.translatable(MinecraftRoomScreenKeys.NONE)
    is GameConfigPresentationValue.ChoiceValue ->
        Text.translatable("mahjongcraft.room.config.option.${value.optionId.substringAfter(':')}")
}
