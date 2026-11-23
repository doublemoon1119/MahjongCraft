package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.Minecraft

/**
 * 本機玩家能與方塊互動的距離。
 *
 * @param client 目前客戶端。
 * @return 距離；沒有進行中的遊戲時為 null。
 */
fun blockInteractionReach(client: Minecraft): Double? = client.gameMode?.pickRange?.toDouble()

/**
 * 本機玩家能與實體互動的距離。
 *
 * @param client 目前客戶端。
 * @return 距離；沒有進行中的遊戲時為 null。
 */
fun entityInteractionReach(client: Minecraft): Double? = client.gameMode?.let { if (it.hasFarPickRange()) FAR_ENTITY_REACH else ENTITY_REACH }

/** 一般模式下與實體互動的距離。 */
private const val ENTITY_REACH = 3.0

/** 創造模式等遠距離模式下與實體互動的距離。 */
private const val FAR_ENTITY_REACH = 6.0
