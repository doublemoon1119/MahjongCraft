package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/** 解析選項時的錯誤。 */
sealed interface StressOptionError {
    /**
     * 不認得的選項名稱。
     *
     * @property token 寫錯的名稱。
     */
    data class UnknownOption(val token: String) : StressOptionError

    /**
     * 選項後面沒有值。
     *
     * @property option 缺少值的選項。
     */
    data class MissingValue(val option: StressOption) : StressOptionError

    /**
     * 同一個選項寫了兩次。
     *
     * @property option 重複的選項。
     */
    data class DuplicateOption(val option: StressOption) : StressOptionError

    /**
     * 選項的值不在可用的名稱或範圍內。
     *
     * @property option 值無效的選項。
     * @property value 寫錯的值。
     */
    data class InvalidValue(val option: StressOption, val value: String) : StressOptionError
}
