package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.ResolvedPlayerPortraitSource
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** 驗證頭像呈現邊界只收斂一般例外。 */
class PlayerPortraitRendererTest {
    /** 一般例外會回傳失敗結果，供實際 renderer 選擇 fallback。 */
    @Test
    fun `returns recoverable presentation failures`() {
        val result = runPresentationAttempt<String> { throw IllegalStateException("provider failed") }

        val failure = assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("provider failed", failure.message)
    }

    /** 第三方來源繪製失敗時只重試一次內建來源。 */
    @Test
    fun `falls back after an ordinary source drawing failure`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()
        val reportedProviderIds = mutableListOf<String>()
        val result = renderPortraitWithFallback(
            resolved = Result.success(ResolvedPlayerPortraitSource("example:provider", PlayerPortraitSource.TileFace("custom"))),
            fallbackSource = PlayerPortraitSource.TileFace("fallback"),
            onResolutionFailure = { _, providerId -> reportedProviderIds += providerId },
            onDrawFailure = { _, providerId -> reportedProviderIds += providerId },
            draw = { source ->
                attemptedSources += source
                if (source == PlayerPortraitSource.TileFace("custom")) {
                    error("custom source failed")
                }
                "fallback drawn"
            },
        )

        assertEquals("fallback drawn", result)
        assertEquals(
            listOf<PlayerPortraitSource>(PlayerPortraitSource.TileFace("custom"), PlayerPortraitSource.TileFace("fallback")),
            attemptedSources,
        )
        assertEquals(listOf("example:provider"), reportedProviderIds)
    }

    /** 解析失敗時直接使用內建來源，不會重試同一個內建來源。 */
    @Test
    fun `uses fallback after an ordinary resolution failure`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()
        val reportedProviderIds = mutableListOf<String>()
        val result = renderPortraitWithFallback(
            resolved = Result.failure(IllegalStateException("resolution failed")),
            fallbackSource = PlayerPortraitSource.TileFace("fallback"),
            onResolutionFailure = { _, providerId -> reportedProviderIds += providerId },
            onDrawFailure = { _, providerId -> reportedProviderIds += providerId },
            draw = { source ->
                attemptedSources += source
                "fallback drawn"
            },
        )

        assertEquals("fallback drawn", result)
        assertEquals(listOf<PlayerPortraitSource>(PlayerPortraitSource.TileFace("fallback")), attemptedSources)
        assertEquals(listOf("registry"), reportedProviderIds)
    }

    /** 解析器包裝提供者例外時仍保留原本的 provider ID。 */
    @Test
    fun `reports the provider id from a wrapped resolution failure`() {
        val reportedProviderIds = mutableListOf<String>()

        renderPortraitWithFallback(
            resolved = Result.failure(
                PlayerPortraitSourceProviderException("example:provider", IllegalStateException("resolution failed")),
            ),
            fallbackSource = PlayerPortraitSource.TileFace("fallback"),
            onResolutionFailure = { _, providerId -> reportedProviderIds += providerId },
            onDrawFailure = { _, providerId -> reportedProviderIds += providerId },
            draw = { "fallback drawn" },
        )

        assertEquals(listOf("example:provider"), reportedProviderIds)
    }

    /** 內建來源繪製失敗時不會再次嘗試同一個來源。 */
    @Test
    fun `does not retry a failed fallback drawing`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()

        assertFailsWith<IllegalStateException> {
            renderPortraitWithFallback(
                resolved = Result.success(ResolvedPlayerPortraitSource("example:provider", PlayerPortraitSource.TileFace("custom"))),
                fallbackSource = PlayerPortraitSource.TileFace("fallback"),
                onResolutionFailure = { _, _ -> error("Unexpected resolution failure") },
                onDrawFailure = { _, _ -> },
                draw = { source ->
                    attemptedSources += source
                    throw IllegalStateException("draw failed")
                },
            )
        }

        assertEquals(
            listOf<PlayerPortraitSource>(PlayerPortraitSource.TileFace("custom"), PlayerPortraitSource.TileFace("fallback")),
            attemptedSources,
        )
    }

    /** 外觀來源解析被取消時不應繪製任何 fallback。 */
    @Test
    fun `propagates cancellation from source resolution without fallback`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()

        assertFailsWith<CancellationException> {
            renderPortraitWithFallback(
                resolved = runPresentationAttempt { throw CancellationException("cancelled") },
                fallbackSource = PlayerPortraitSource.TileFace("fallback"),
                onResolutionFailure = { _, _ -> error("Unexpected resolution failure") },
                onDrawFailure = { _, _ -> error("Unexpected draw failure") },
                draw = { source ->
                    attemptedSources += source
                    "unexpected"
                },
            )
        }

        assertEquals(emptyList<PlayerPortraitSource>(), attemptedSources)
    }

    /** 外觀來源解析發生致命錯誤時不應繪製任何 fallback。 */
    @Test
    fun `propagates fatal errors from source resolution without fallback`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()

        assertFailsWith<AssertionError> {
            renderPortraitWithFallback(
                resolved = runPresentationAttempt { throw AssertionError("fatal") },
                fallbackSource = PlayerPortraitSource.TileFace("fallback"),
                onResolutionFailure = { _, _ -> error("Unexpected resolution failure") },
                onDrawFailure = { _, _ -> error("Unexpected draw failure") },
                draw = { source ->
                    attemptedSources += source
                    "unexpected"
                },
            )
        }

        assertEquals(emptyList<PlayerPortraitSource>(), attemptedSources)
    }

    /** 第三方來源繪製被取消時不應改用內建來源。 */
    @Test
    fun `propagates cancellation from source drawing without fallback`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()

        assertFailsWith<CancellationException> {
            renderPortraitWithFallback(
                resolved = Result.success(ResolvedPlayerPortraitSource("example:provider", PlayerPortraitSource.TileFace("custom"))),
                fallbackSource = PlayerPortraitSource.TileFace("fallback"),
                onResolutionFailure = { _, _ -> error("Unexpected resolution failure") },
                onDrawFailure = { _, _ -> error("Unexpected draw failure") },
                draw = { source ->
                    attemptedSources += source
                    throw CancellationException("cancelled")
                },
            )
        }

        assertEquals(listOf<PlayerPortraitSource>(PlayerPortraitSource.TileFace("custom")), attemptedSources)
    }

    /** 第三方來源繪製發生致命錯誤時不應改用內建來源。 */
    @Test
    fun `propagates fatal errors from source drawing without fallback`() {
        val attemptedSources = mutableListOf<PlayerPortraitSource>()

        assertFailsWith<AssertionError> {
            renderPortraitWithFallback(
                resolved = Result.success(ResolvedPlayerPortraitSource("example:provider", PlayerPortraitSource.TileFace("custom"))),
                fallbackSource = PlayerPortraitSource.TileFace("fallback"),
                onResolutionFailure = { _, _ -> error("Unexpected resolution failure") },
                onDrawFailure = { _, _ -> error("Unexpected draw failure") },
                draw = { source ->
                    attemptedSources += source
                    throw AssertionError("fatal")
                },
            )
        }

        assertEquals(listOf<PlayerPortraitSource>(PlayerPortraitSource.TileFace("custom")), attemptedSources)
    }

    /** 取消例外會穿透 renderer 使用的呈現邊界。 */
    @Test
    fun `propagates cancellation from presentation`() {
        assertFailsWith<CancellationException> {
            runPresentationAttempt<String> { throw CancellationException("cancelled") }
        }
    }

    /** 致命錯誤會穿透 renderer 使用的呈現邊界。 */
    @Test
    fun `propagates fatal errors from presentation`() {
        assertFailsWith<AssertionError> {
            runPresentationAttempt<String> { throw AssertionError("fatal") }
        }
    }
}
