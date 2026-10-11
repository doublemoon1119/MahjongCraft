package com.doublemoon1119.mahjongcraft.flow.network.dto.rule
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RonResolution.toDto(): RonResolutionDto = when (this) {
    RonResolution.NEAREST_WINNER -> RonResolutionDto.NEAREST_WINNER
    RonResolution.ALL_WINNERS -> RonResolutionDto.ALL_WINNERS
    RonResolution.ABORTIVE_DRAW -> RonResolutionDto.ABORTIVE_DRAW
}

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun RonResolutionDto.toDomain(): RonResolution = when (this) {
    RonResolutionDto.NEAREST_WINNER -> RonResolution.NEAREST_WINNER
    RonResolutionDto.ALL_WINNERS -> RonResolution.ALL_WINNERS
    RonResolutionDto.ABORTIVE_DRAW -> RonResolution.ABORTIVE_DRAW
}

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun MultiRonPolicy.toDto(): MultiRonPolicyDto = MultiRonPolicyDto(
    doubleRonResolution = doubleRonResolution.toDto(),
    tripleRonResolution = tripleRonResolution.toDto(),
)

/**
 * 將網路 DTO 還原為領域資料。
 *
 * @return 轉換後的資料。
 */
fun MultiRonPolicyDto.toDomain(): MultiRonPolicy = MultiRonPolicy(
    doubleRonResolution = doubleRonResolution.toDomain(),
    tripleRonResolution = tripleRonResolution.toDomain(),
)
