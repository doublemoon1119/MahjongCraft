package com.doublemoon1119.mahjongcraft.flow.common.game.random

import com.doublemoon1119.mahjongcraft.logic.table.RoundRandomness
import com.doublemoon1119.mahjongcraft.logic.table.opening.RandomDiceRollSource
import kotlin.random.Random

/**
 * 一場對局全部亂數的不公開種子。
 *
 * 每一局的座位、牌山、牌張 ID、骰子與規則組件初值都由種子、局序與用途以 HMAC-SHA256 衍生，因此同一個種子重現同一場對局，
 * 而看到已公開的結果也推不回種子或尚未公開的牌山。種子只存在伺服器的權威對局狀態與伺服器存檔，不得放進快照、
 * 網路資料、事件或牌譜；[toString] 不顯示內容，避免隨對局物件寫進紀錄。
 */
class MatchSeed private constructor(private val bytes: ByteArray) {
    /**
     * 取得種子內容，只供伺服器存檔使用。
     *
     * @return 種子位元組的副本。
     */
    fun toByteArray(): ByteArray = bytes.copyOf()

    /**
     * 衍生一局使用的亂數來源。
     *
     * @param roundOrdinal 這場對局中第幾次開局，從 0 起算；連莊或重新開局也各算一次，不可重複使用。
     * @return 這一局的座位、牌山、擲骰與規則組件亂數來源。
     */
    fun roundRandomness(roundOrdinal: Int): RoundRandomness {
        require(roundOrdinal >= 0) { "Round ordinal must not be negative" }
        return RoundRandomness(
            seatOrder = stream(MatchRandomPurpose.SEAT_ORDER, roundOrdinal),
            wall = stream(MatchRandomPurpose.WALL, roundOrdinal),
            dice = RandomDiceRollSource(stream(MatchRandomPurpose.DICE, roundOrdinal)),
            components = stream(MatchRandomPurpose.COMPONENTS, roundOrdinal),
        )
    }

    /**
     * 建立一個用途在指定局序的亂數串。
     *
     * @param purpose 亂數用途。
     * @param roundOrdinal 開局序號。
     * @return 新的亂數串，從第一個位元組開始讀。
     */
    private fun stream(purpose: MatchRandomPurpose, roundOrdinal: Int): Random = HmacStreamRandom(key = bytes, label = "$LABEL_PREFIX/${purpose.label}/$roundOrdinal/".encodeToByteArray())

    override fun equals(other: Any?): Boolean = other is MatchSeed && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "MatchSeed(hidden)"

    /** 建立種子的入口。 */
    companion object {
        /** 種子的位元組數。 */
        const val SIZE_BYTES: Int = 32

        /** 衍生標籤的固定前綴；改變它會讓同一個種子產生不同的對局。 */
        private const val LABEL_PREFIX = "mahjongcraft:match_random"

        /**
         * 以安全亂數產生新種子。
         *
         * @return 無法被猜出的新種子。
         */
        fun generate(): MatchSeed = MatchSeed(secureRandomBytes(SIZE_BYTES))

        /**
         * 由既有內容建立種子，供讀取伺服器存檔或測試指定種子使用。
         *
         * @param bytes 長度為 [SIZE_BYTES] 的種子內容。
         * @return 內容相同的種子；之後修改 [bytes] 不影響它。
         */
        fun fromByteArray(bytes: ByteArray): MatchSeed {
            require(bytes.size == SIZE_BYTES) { "Match seed must be $SIZE_BYTES bytes" }
            return MatchSeed(bytes.copyOf())
        }
    }
}
