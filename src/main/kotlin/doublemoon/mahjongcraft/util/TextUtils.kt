package doublemoon.mahjongcraft.util

import net.minecraft.text.MutableText

/**
 * 簡化 [MutableText.append] 用
 * */
operator fun <T : MutableText> T.plus(text: T): MutableText =
    this.append(text)

operator fun <T : MutableText> T.plus(text: String): MutableText =
    this.append(text)


