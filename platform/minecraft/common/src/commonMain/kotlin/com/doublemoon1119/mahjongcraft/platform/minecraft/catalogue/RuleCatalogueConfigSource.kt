package com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue

/** 目錄設定的來源；一般說明不代表任何房間或歷史對局的設定。 */
enum class RuleCatalogueConfigSource {
    /** 規則來源提供的預設設定。 */
    GENERAL,

    /** 房間目前的設定。 */
    ROOM,

    /** 尚未套用的房間編輯草稿。 */
    ROOM_DRAFT,

    /** 歷史對局保存的設定。 */
    HISTORY,
}
