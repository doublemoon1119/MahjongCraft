package com.doublemoon1119.mahjongcraft.buildlogic.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證多行呼叫參數名稱規則：哪些呼叫要寫名稱、回報訊息帶出的參數名稱。 */
class MultilineCallArgumentNameRuleTest {
    /** 型別解析使用的測試環境。 */
    private val environment = createEnvironment()

    /** 多行呼叫中每個沒寫名稱的參數各回報一次，訊息帶出對應的參數名稱。 */
    @Test
    fun `reports each unnamed argument of a multi-line call with its parameter name`() {
        val findings = MultilineCallArgumentNameRule().lintWithContext(
            environment,
            """
            fun place(x: Int, y: Int, label: String) = Unit

            fun use() {
                place(
                    1,
                    y = 2,
                    "a",
                )
            }
            """.trimIndent(),
        )

        assertEquals(listOf("`x`.", "`label`."), findings.map { it.message.substringAfterLast(' ') })
    }

    /** 寫在同一行的呼叫與已經全部寫上名稱的多行呼叫都不回報。 */
    @Test
    fun `ignores single-line calls and fully named calls`() {
        val findings = MultilineCallArgumentNameRule().lintWithContext(
            environment,
            """
            fun place(x: Int, y: Int) = Unit

            fun use() {
                place(1, 2)
                place(
                    x = 1,
                    y = 2,
                )
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), findings)
    }

    /** 可變數量參數、括號外的 lambda、Java 函式與函式型別的呼叫都不能或不需要寫名稱，不回報。 */
    @Test
    fun `ignores arguments that cannot be named`() {
        val findings = MultilineCallArgumentNameRule().lintWithContext(
            environment,
            """
            fun repeatTimes(times: Int, action: () -> Unit) = Unit

            fun use(callback: (Int, Int) -> Unit) {
                val values = listOf(
                    1,
                    2,
                )
                repeatTimes(times = 2) {
                    println(values)
                }
                val builder = StringBuilder().insert(
                    0,
                    "a",
                )
                callback(
                    1,
                    2,
                )
                println(builder)
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), findings)
    }

    /** 條件檢查函式的條件與 `error` 的訊息不回報；其他標準函式照常回報。 */
    @Test
    fun `ignores condition checks but reports other standard functions`() {
        val findings = MultilineCallArgumentNameRule().lintWithContext(
            environment,
            """
            fun use(value: Int?) {
                require(
                    value != null,
                ) { "value is required" }
                check(
                    value > 0,
                ) { "value must be positive" }
                requireNotNull(
                    value,
                )
                if (value > 10) {
                    error(
                        "value is too large",
                    )
                }
                maxOf(
                    value,
                    1,
                )
            }
            """.trimIndent(),
        )

        assertEquals(listOf("`a`.", "`b`."), findings.map { it.message.substringAfterLast(' ') })
    }

    /** 類別標頭中對父類別建構子的多行呼叫也要寫名稱。 */
    @Test
    fun `reports unnamed arguments of a multi-line superclass constructor call`() {
        val findings = MultilineCallArgumentNameRule().lintWithContext(
            environment,
            """
            open class Base(val first: Int, val second: Int)

            class Derived : Base(
                1,
                second = 2,
            )
            """.trimIndent(),
        )

        assertEquals(listOf("`first`."), findings.map { it.message.substringAfterLast(' ') })
    }
}
