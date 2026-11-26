package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.contents.LiteralContents

/**
 * 純文字內容的文字。
 *
 * @param contents 文字元件的內容。
 * @return 內容是純文字時為該文字，否則為 null。
 */
fun literalText(contents: ComponentContents): String? = (contents as? LiteralContents)?.text()
