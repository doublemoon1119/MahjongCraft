package com.doublemoon1119.mahjongcraft.domain.table

/**
 * 代表規則特有的玩家狀態介面。
 *
 * 與靜態的規則配置不同，此介面用於存放隨遊戲進程改變的玩家相關規則數據。
 * 例如：日本麻將的玩家立直狀態、振聽狀態等。
 *
 * @see com.doublemoon1119.mahjongcraft.domain.config.DynamicRuleState 用於牌桌級的規則狀態
 */
interface PlayerRuleState
