package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** Minecraft 內建役滿 showcase 使用的 translation key 單一來源。 */
object MinecraftShowcaseKeys {
    /** 所有 MahjongCraft showcase translation key 的共用前綴。 */
    private const val PREFIX = MinecraftModMetadata.MOD_ID + ".showcase."

    const val CHIIHOU = PREFIX + "chiihou"
    const val CHINROUTOU = PREFIX + "chinroutou"
    const val CHUREN_POTO = PREFIX + "churen_poto"
    const val CHUREN_POTO_9 = PREFIX + "churen_poto_9"
    const val DAISANGEN = PREFIX + "daisangen"
    const val DAISUUSHII = PREFIX + "daisuushii"
    const val GENERIC = PREFIX + "generic"
    const val KOKUSHI_MUSOU = PREFIX + "kokushi_musou"
    const val KOKUSHI_MUSOU_13 = PREFIX + "kokushi_musou_13"
    const val RYUUUIISOU = PREFIX + "ryuuuiisou"
    const val SHOUSUUSHI = PREFIX + "shousuushi"
    const val SUKANTSU = PREFIX + "sukantsu"
    const val SUUANKOU = PREFIX + "suuankou"
    const val SUUANKOU_TANKI = PREFIX + "suuankou_tanki"
    const val TENHOU = PREFIX + "tenhou"
    const val TSUUIISOU = PREFIX + "tsuuiisou"

    /** 由內建 cue path 取得對應的 showcase 標題 key。 */
    fun fromCuePath(path: String): String {
        require(path.isNotBlank()) { "Showcase cue path must not be blank" }
        require(':' !in path && '.' !in path) { "Showcase cue path must not contain namespace separators: $path" }
        return PREFIX + path
    }

    /** Minecraft 語系資源必須提供的全部內建 showcase key。 */
    val ALL: Set<String> = setOf(
        CHIIHOU,
        CHINROUTOU,
        CHUREN_POTO,
        CHUREN_POTO_9,
        DAISANGEN,
        DAISUUSHII,
        GENERIC,
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
