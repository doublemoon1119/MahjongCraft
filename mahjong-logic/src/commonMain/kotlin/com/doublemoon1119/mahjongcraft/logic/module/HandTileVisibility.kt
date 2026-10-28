package com.doublemoon1119.mahjongcraft.logic.module

/** 規則額外授予的手牌牌面讀取權，不收回本人或既有公開流程已授予的權限。 */
sealed interface HandTileVisibility {
    /** 不額外公開，沿用既有可見性政策。 */
    data object Default : HandTileVisibility

    /** 對所有已有資格觀察這局的讀取者公開。 */
    data object Public : HandTileVisibility
}
