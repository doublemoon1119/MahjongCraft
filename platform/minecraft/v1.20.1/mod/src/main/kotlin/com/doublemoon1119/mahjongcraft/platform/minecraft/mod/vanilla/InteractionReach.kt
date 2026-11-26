package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType

/**
 * 本機玩家能與方塊互動的距離。
 *
 * @param client 目前客戶端。
 * @return 距離。沒有進行中的遊戲時為 null。
 */
fun blockInteractionReach(client: Minecraft): Double? = client.gameMode?.pickRange?.toDouble()

/**
 * 伺服器上這位玩家能與方塊互動的距離。
 *
 * @param player 玩家。
 * @return 距離，以方塊為單位。
 */
fun blockInteractionReach(player: ServerPlayer): Double = if (player.gameMode.gameModeForPlayer == GameType.CREATIVE) CREATIVE_BLOCK_REACH else BLOCK_REACH

/**
 * 本機玩家能與實體互動的距離。
 *
 * @param client 目前客戶端。
 * @return 距離。沒有進行中的遊戲時為 null。
 */
fun entityInteractionReach(client: Minecraft): Double? = client.gameMode?.let { if (it.hasFarPickRange()) FAR_ENTITY_REACH else ENTITY_REACH }

/** 一般模式下與實體互動的距離。 */
private const val ENTITY_REACH = 3.0

/** 創造模式等遠距離模式下與實體互動的距離。 */
private const val FAR_ENTITY_REACH = 6.0

/** 一般模式下與方塊互動的距離。 */
private const val BLOCK_REACH = 4.5

/** 創造模式下與方塊互動的距離。 */
private const val CREATIVE_BLOCK_REACH = 5.0
