package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.logic.module.HandTilePose
import com.doublemoon1119.mahjongcraft.logic.module.HandTileState
import kotlin.uuid.Uuid

/**
 * 將逐張姿態轉換成網路資料。
 *
 * @return 只含牌張識別碼與擺放姿態的資料。
 */
fun HandTileState.toDto(): HandTileStateDto = HandTileStateDto(
    tileId = tileId.toString(),
    pose = when (pose) {
        HandTilePose.UPRIGHT -> HandTilePoseDto.UPRIGHT
        HandTilePose.FACE_UP -> HandTilePoseDto.FACE_UP
    },
)

/**
 * 還原逐張姿態的網路資料。
 *
 * @return 不攜帶授權名單的姿態。
 */
fun HandTileStateDto.toDomain(): HandTileState = HandTileState(
    tileId = Uuid.parse(tileId),
    pose = when (pose) {
        HandTilePoseDto.UPRIGHT -> HandTilePose.UPRIGHT
        HandTilePoseDto.FACE_UP -> HandTilePose.FACE_UP
    },
)
