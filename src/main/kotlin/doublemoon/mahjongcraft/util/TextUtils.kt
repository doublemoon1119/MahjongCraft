package doublemoon.mahjongcraft.util

import net.minecraft.text.MutableText
import net.minecraft.text.Text

/**
 * 簡化 [MutableText.append] 用
 * */
operator fun <T : MutableText> T.plus(text: Text): MutableText =
    this.append(text)

operator fun <T : MutableText> T.plus(text: String): MutableText =
    this.append(text)


interface TextFormatting {
    fun toText(): Text
}

