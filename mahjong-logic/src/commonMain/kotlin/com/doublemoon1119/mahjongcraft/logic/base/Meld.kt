package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 代表玩家的副露（鳴牌組合）。
 *
 * 副露是指玩家透過鳴取他人的捨牌或宣告自己手中的槓牌，而將特定的牌組公開放置在桌面上的行為。
 * 在不同規則中，副露的呈現方式與對勝負的影響（如役種、番數）各有不同。
 *
 * @property type 副露的種類，例如 [MeldType.CHI] 或 [MeldType.PON]。
 * @property tiles 組成該副露的所有 [IdentifiedTile]。
 * @property sourceTile 從其他玩家處鳴取而來的特定牌。若為暗槓 ([MeldType.CLOSED_KAN]) 則通常為 null。
 * @property sourceDirection 鳴取來源的相對方位。用於決定渲染時哪張牌需要橫放（日麻標準）。
 */
data class Meld(
    val type: MeldType,
    val tiles: List<IdentifiedTile>,
    val sourceTile: IdentifiedTile? = null,
    val sourceDirection: RelativeDirection
)

/**
 * 副露的種類定義。
 * 採用國際通用英文命名，並在註釋中提供日文與中文對照。
 */
enum class MeldType {
    /** * 吃 (Chow)。
     * 日文：チー (Chi)。
     * 說明：順子組合。
     */
    CHI,

    /** * 碰 (Pung)。
     * 日文：ポン (Pon)。
     * 說明：刻子組合。
     */
    PON,

    /** * 明槓 (Exposed Kan)。
     * 日文：大明槓 (Daiminkan)。
     * 說明：鳴取他人的捨牌構成的槓子。
     */
    OPEN_KAN,

    /** * 暗槓 (Closed Kan)。
     * 日文：暗槓 (Ankan)。
     * 說明：由自己手中的四張相同牌構成的槓子。
     */
    CLOSED_KAN,

    /** * 加槓 (Added Kan)。
     * 日文：加槓 (Kakan) / 小明槓。
     * 說明：在已有的碰 (PON) 基礎上，增加第四張相同的牌構成的槓子。
     */
    ADDED_KAN
}