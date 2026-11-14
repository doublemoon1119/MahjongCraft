package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollSource
import kotlin.random.Random

/**
 * 開一局時使用的全部亂數來源。
 *
 * 規則層只透過這裡取得亂數，不自行使用系統亂數；同一組來源產生同一局的座位、牌山與骰子。來源由呼叫端決定，
 * 正式對局由伺服器從不公開的種子衍生，外部無法從已公開的結果推回尚未公開的部分。
 *
 * @property seatOrder 開場時排定座位順序；下一局不重新排座位，因此不使用。
 * @property wall 建立牌山時洗牌並產生每張牌的 ID。
 * @property dice 擲骰結果。
 * @property components 建立規則組件初值，例如由規則隨機決定的本局狀態。
 */
class RoundRandomness(
    val seatOrder: Random,
    val wall: Random,
    val dice: DiceRollSource,
    val components: Random,
)
