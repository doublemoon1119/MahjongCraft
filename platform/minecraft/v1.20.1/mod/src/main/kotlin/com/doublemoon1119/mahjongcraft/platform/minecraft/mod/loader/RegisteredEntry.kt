package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

/**
 * 交給 [ModRegistrar] 登記的一個項目。
 *
 * 載入器實際寫入登記表的時機各不相同，因此項目只在登記完成後才讀得到；登記前讀取會丟出例外。
 *
 * @param T 登記的物件型別。
 */
fun interface RegisteredEntry<out T : Any> {
    /**
     * 取得已登記的物件。
     *
     * @return 寫入登記表的物件。
     * @throws IllegalStateException 項目尚未登記。
     */
    fun get(): T
}
