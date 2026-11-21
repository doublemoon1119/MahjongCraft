package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * 不依賴世界實體的射線命中結果。
 *
 * @property payload 被命中的候選物件。
 * @property position 射線與候選 bounding box 的交點。
 * @property distanceSquared 交點和射線起點之間的距離平方。
 */
internal data class HandTileTarget<T>(
    val payload: T,
    val position: Vec3,
    val distanceSquared: Double,
)

/**
 * 依候選包圍盒找出射線上最近且未被遮擋的物件。
 *
 * @param start 射線起點。
 * @param end 射線終點。
 * @param candidates 候選物件及其目前 bounding box。
 * @param occlusionDistanceSquared 遮擋物距離平方；候選在此距離或更遠時視為被遮擋。
 * @param maximumDistanceSquared 可接受的最遠命中距離平方。
 * @return 最近的有效候選；沒有命中時回傳 null。
 */
internal fun <T> findNearestHandTileTarget(
    start: Vec3,
    end: Vec3,
    candidates: Iterable<Pair<T, AABB>>,
    occlusionDistanceSquared: Double = Double.POSITIVE_INFINITY,
    maximumDistanceSquared: Double = Double.POSITIVE_INFINITY,
): HandTileTarget<T>? = candidates.mapNotNull { (payload, box) ->
    val position = box.clip(start, end).orElse(null) ?: return@mapNotNull null
    val distanceSquared = position.distanceToSqr(start)
    if (distanceSquared >= occlusionDistanceSquared || distanceSquared > maximumDistanceSquared) return@mapNotNull null
    HandTileTarget(payload, position, distanceSquared)
}.minByOrNull(HandTileTarget<T>::distanceSquared)
