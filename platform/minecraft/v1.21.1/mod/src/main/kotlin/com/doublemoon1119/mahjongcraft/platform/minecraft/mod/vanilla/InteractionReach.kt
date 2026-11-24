package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.Minecraft

/**
 * 本機玩家能與方塊互動的距離。
 *
 * @param client 目前客戶端。
 * @return 距離；沒有進行中的遊戲時為 null。
 */
fun blockInteractionReach(client: Minecraft): Double? = client.player?.blockInteractionRange()

/**
 * 本機玩家能與實體互動的距離。
 *
 * @param client 目前客戶端。
 * @return 距離；沒有進行中的遊戲時為 null。
 */
fun entityInteractionReach(client: Minecraft): Double? = client.player?.entityInteractionRange()
