package doublemoon.mahjongcraft.util

import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.text.Text


/**
 * 對玩家發送顯示標題的數據包, 好像不能控制時間(?
 *
 * @param title 一定要有才會顯示 [subtitle]
 * @param fadeInTime 單位: tick
 * @param stayTime 單位: tick
 * @param fadeOutTime 單位: tick
 * */
fun ServerPlayerEntity.sendTitle(
    title: Text,
    subtitle: Text = LiteralText(""),
    fadeInTime: Int = 20,
    stayTime: Int = 60,
    fadeOutTime: Int = 20,
) {
    with(networkHandler) {
        sendPacket(TitleS2CPacket(TitleS2CPacket.Action.TITLE, title, fadeInTime, stayTime, fadeOutTime))
        sendPacket(TitleS2CPacket(TitleS2CPacket.Action.SUBTITLE, subtitle, fadeInTime, stayTime, fadeOutTime))
    }
}

/**
 * 對玩家發送顯示標題的數據包
 *
 * @param title 一定要有才會顯示 [subtitle]
 * @param fadeInTime 單位: tick
 * @param stayTime 單位: tick
 * @param fadeOutTime 單位: tick
 * */
fun Collection<ServerPlayerEntity>.sendTitle(
    title: Text,
    subtitle: Text = LiteralText(""),
    fadeInTime: Int = 20,
    stayTime: Int = 60,
    fadeOutTime: Int = 20,
) {
    forEach { it.sendTitle(title, subtitle, fadeInTime, stayTime, fadeOutTime) }
}