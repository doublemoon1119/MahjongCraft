package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** Minecraft 內建 showcase 使用的 translation key 單一來源；規則專屬的標題依規則分開命名。 */
object MinecraftShowcaseKeys {
    /** 所有 MahjongCraft showcase translation key 的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".showcase."

    /** 日麻役滿 showcase 標題 key 的共用前綴。 */
    private const val RIICHI_PREFIX = PREFIX + "riichi."

    const val CHIIHOU = RIICHI_PREFIX + "chiihou"
    const val CHINROUTOU = RIICHI_PREFIX + "chinroutou"
    const val CHUREN_POTO = RIICHI_PREFIX + "churen_poto"
    const val CHUREN_POTO_9 = RIICHI_PREFIX + "churen_poto_9"
    const val DAISANGEN = RIICHI_PREFIX + "daisangen"
    const val DAISUUSHII = RIICHI_PREFIX + "daisuushii"
    const val KOKUSHI_MUSOU = RIICHI_PREFIX + "kokushi_musou"
    const val KOKUSHI_MUSOU_13 = RIICHI_PREFIX + "kokushi_musou_13"
    const val RYUUUIISOU = RIICHI_PREFIX + "ryuuuiisou"
    const val SHOUSUUSHI = RIICHI_PREFIX + "shousuushi"
    const val SUKANTSU = RIICHI_PREFIX + "sukantsu"
    const val SUUANKOU = RIICHI_PREFIX + "suuankou"
    const val SUUANKOU_TANKI = RIICHI_PREFIX + "suuankou_tanki"
    const val TENHOU = RIICHI_PREFIX + "tenhou"
    const val TSUUIISOU = RIICHI_PREFIX + "tsuuiisou"

    /** 由日麻役滿的役種名稱取得對應的 showcase 標題 key。 */
    fun riichiYakuman(path: String): String {
        require(path.isNotBlank()) { "Showcase cue path must not be blank" }
        require(':' !in path && '.' !in path && '/' !in path) { "Showcase cue path must not contain namespace separators: $path" }
        return RIICHI_PREFIX + path
    }

    /** Minecraft 語系資源必須提供的全部內建 showcase key。 */
    val ALL: Set<String> = setOf(
        CHIIHOU,
        CHINROUTOU,
        CHUREN_POTO,
        CHUREN_POTO_9,
        DAISANGEN,
        DAISUUSHII,
        KOKUSHI_MUSOU,
        KOKUSHI_MUSOU_13,
        RYUUUIISOU,
        SHOUSUUSHI,
        SUKANTSU,
        SUUANKOU,
        SUUANKOU_TANKI,
        TENHOU,
        TSUUIISOU,
    )
}
