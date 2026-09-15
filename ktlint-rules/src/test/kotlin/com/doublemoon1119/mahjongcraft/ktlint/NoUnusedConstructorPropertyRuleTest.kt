package com.doublemoon1119.mahjongcraft.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證未使用 constructor property 規則的判定範圍。 */
class NoUnusedConstructorPropertyRuleTest {
    /** 完全沒有使用點的 private 屬性應被攔截。 */
    @Test
    fun `reports a private constructor property that is never used`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            class Command(private val unused: String)
            """.trimIndent(),
            ::NoUnusedConstructorPropertyRule,
        )

        assertEquals(1, errors.size)
        assertTrue(errors.messages().single().contains("'unused'"))
    }

    /** 類別內有使用點時不回報。 */
    @Test
    fun `accepts a property used in the class body`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            class Command(private val name: String) {
                fun describe(): String = name
            }
            """.trimIndent(),
            ::NoUnusedConstructorPropertyRule,
        )

        assertEquals(emptyList(), errors.messages())
    }

    /** 只在 KDoc 的 `@property` 出現不算使用。 */
    @Test
    fun `does not count a kdoc property mention as usage`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            /**
             * 指令。
             *
             * @property unused 只有說明，沒有使用。
             */
            class Command(private val unused: String)
            """.trimIndent(),
            ::NoUnusedConstructorPropertyRule,
        )

        assertEquals(1, errors.size)
    }

    /** 非 private 的建構子屬性屬於公開 API，不在本規則範圍。 */
    @Test
    fun `ignores non private constructor properties`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            data class Config(val unused: String)
            """.trimIndent(),
            ::NoUnusedConstructorPropertyRule,
        )

        assertEquals(emptyList(), errors.messages())
    }

    /** 沒有 `val`／`var` 的一般建構子參數不是屬性，不在本規則範圍。 */
    @Test
    fun `ignores plain constructor parameters`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            class Command(seed: Int) {
                private val doubled = seed * 2

                fun value(): Int = doubled
            }
            """.trimIndent(),
            ::NoUnusedConstructorPropertyRule,
        )

        assertEquals(emptyList(), errors.messages())
    }

    /** 多個屬性時只回報未使用的那些。 */
    @Test
    fun `reports only the unused properties`() {
        val errors = lint(
            """
            package com.doublemoon1119.mahjongcraft.example

            class Command(
                private val used: String,
                private val unused: Int,
            ) {
                fun describe(): String = used
            }
            """.trimIndent(),
            ::NoUnusedConstructorPropertyRule,
        )

        assertEquals(1, errors.size)
        assertTrue(errors.messages().single().contains("'unused'"))
    }
}
