package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import kotlinx.serialization.Serializable

/** [RonResolution] 的網路 DTO。 */
@Serializable
enum class RonResolutionDto { NEAREST_WINNER, ALL_WINNERS, ABORTIVE_DRAW }

/** [MultiRonPolicy] 的網路 DTO。 */
@Serializable
data class MultiRonPolicyDto(
    val doubleRonResolution: RonResolutionDto,
    val tripleRonResolution: RonResolutionDto,
)

fun RonResolution.toDto(): RonResolutionDto = when (this) {
    RonResolution.NEAREST_WINNER -> RonResolutionDto.NEAREST_WINNER
    RonResolution.ALL_WINNERS -> RonResolutionDto.ALL_WINNERS
    RonResolution.ABORTIVE_DRAW -> RonResolutionDto.ABORTIVE_DRAW
}

fun RonResolutionDto.toDomain(): RonResolution = when (this) {
    RonResolutionDto.NEAREST_WINNER -> RonResolution.NEAREST_WINNER
    RonResolutionDto.ALL_WINNERS -> RonResolution.ALL_WINNERS
    RonResolutionDto.ABORTIVE_DRAW -> RonResolution.ABORTIVE_DRAW
}

fun MultiRonPolicy.toDto(): MultiRonPolicyDto = MultiRonPolicyDto(
    doubleRonResolution = doubleRonResolution.toDto(),
    tripleRonResolution = tripleRonResolution.toDto(),
)

fun MultiRonPolicyDto.toDomain(): MultiRonPolicy = MultiRonPolicy(
    doubleRonResolution = doubleRonResolution.toDomain(),
    tripleRonResolution = tripleRonResolution.toDomain(),
)
