package doublemoon.mahjongcraft.util

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.text.Text


/**
 * 對玩家發送顯示標題的數據包, 不能控制時間
 *
 * @param title 要顯示的標題
 * @param subtitle 要顯示的副標題
 * */
fun ServerPlayerEntity.sendTitles(
    title: Text = LiteralText(""),
    subtitle: Text? = null,
) {
    with(networkHandler) {
        sendPacket(TitleS2CPacket(title))
        subtitle?.also { sendPacket(SubtitleS2CPacket(it)) }
    }
}

/**
 * 對玩家發送顯示標題的數據包
 *
 * @param title 要顯示的標題
 * @param subtitle 要顯示的副標題
 * */
fun Collection<ServerPlayerEntity>.sendTitles(
    title: Text = LiteralText(""),
    subtitle: Text? = null
) {
    forEach { it.sendTitles(title, subtitle) }
}