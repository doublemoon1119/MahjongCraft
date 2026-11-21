package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * 開啟碰撞箱除錯顯示（F3 + B）時不畫碰撞箱的實體。
 *
 * 只給玩家看、不能被準心選取的面板使用：它們的碰撞箱只決定繪製範圍，不代表任何可以點擊的位置，
 * 畫出來只會擋住面板內容。會被點擊或互動的實體不使用，讓除錯顯示仍能看出它們的點擊範圍。
 */
interface HiddenDebugHitboxEntity
