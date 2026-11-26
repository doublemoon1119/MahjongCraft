package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import com.mojang.brigadier.builder.LiteralArgumentBuilder

/**
 * 只在用戶端執行的指令。
 *
 * 各載入器的用戶端指令使用不同的來源型別，因此指令樹依載入器指定的型別建立。指令本身不讀取來源，
 * 需要回饋時改用與來源無關的方式。
 */
interface ClientCommand {
    /**
     * 建立這個指令的指令樹。
     *
     * @param S 載入器的用戶端指令來源型別。
     * @return 從根節點開始的指令樹。
     */
    fun <S : Any> build(): LiteralArgumentBuilder<S>
}
