package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/**
 * 找不到參與遊戲的 AI 玩家時，已開局的對局要如何處理。
 *
 * 適用於擴充模組接入的既有實體；內建麻將機器人由本模組管理。
 */
enum class MissingAiPlayerPolicy {
    /** 保留同一名 AI 玩家與分數，讓對局繼續。 */
    CONTINUE_MATCH,

    /** 中止當前對局，讓參與遊戲的玩家返回房間。 */
    ABORT_MATCH,
}

/** 設定檔使用的穩定政策名稱。 */
val MissingAiPlayerPolicy.configValue: String get() = name.lowercase()
