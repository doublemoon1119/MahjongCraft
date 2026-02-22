package com.doublemoon1119.mahjongcraft.model

/**
 * 代表規則特有的額外桌況狀態介面。
 *
 * 由於不同麻將規則（如日麻與台麻）在全局狀態上有巨大的差異（例如立直棒、連莊計算方式等），
 * 透過此介面將非通用的狀態抽離，以保持 [TableState] 的簡潔與泛用性。
 */
interface RuleExtraState