package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 時間長度是否為有限、不為負，且剛好是整數秒。 */
internal fun Duration.isNonNegativeWholeSeconds(): Boolean = isFinite() && !isNegative() && this == inWholeSeconds.seconds
