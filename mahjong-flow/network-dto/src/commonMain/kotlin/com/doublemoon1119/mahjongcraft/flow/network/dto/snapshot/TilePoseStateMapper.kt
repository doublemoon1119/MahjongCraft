package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import kotlin.uuid.Uuid

/**
 * 將逐張姿態轉換成網路資料。
 *
 * @return 只含牌張識別碼與擺放姿態的資料。
 */
fun TilePoseState.toDto(): TilePoseStateDto = TilePoseStateDto(
    tileId = tileId.toString(),
    pose = when (pose) {
        TilePose.UPRIGHT -> TilePoseDto.UPRIGHT
        TilePose.FACE_UP -> TilePoseDto.FACE_UP
        TilePose.FACE_DOWN -> TilePoseDto.FACE_DOWN
    },
)

/**
 * 還原逐張姿態的網路資料。
 *
 * @return 不攜帶授權名單的姿態。
 */
fun TilePoseStateDto.toDomain(): TilePoseState = TilePoseState(
    tileId = Uuid.parse(tileId),
    pose = when (pose) {
        TilePoseDto.UPRIGHT -> TilePose.UPRIGHT
        TilePoseDto.FACE_UP -> TilePose.FACE_UP
        TilePoseDto.FACE_DOWN -> TilePose.FACE_DOWN
    },
)
