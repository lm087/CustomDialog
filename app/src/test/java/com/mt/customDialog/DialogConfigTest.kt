package com.mt.customDialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DialogConfigTest {
    @Test
    fun delayAcceptsWholeSecondsAndTrimsSurroundingWhitespace() {
        mapOf(
            "" to 0L,
            " \t\n " to 0L,
            "0" to 0L,
            "12" to 12_000L,
            " 12 \t" to 12_000L,
            "00012" to 12_000L,
            "86400" to 86_400_000L,
        ).forEach { (text, expected) ->
            assertEquals(text, expected, DialogConfig(delayText = text).delayMillis)
        }
    }

    @Test
    fun delayRejectsFractionsNegativesOverflowsAndOutOfRangeValues() {
        listOf(
            "-1", "+1", "1.5", "1e3", "1 2", "1\n2", "abc",
            "86401", "9223372036854775807", "9223372036854775808",
            "9999999999999999999999999999999999999999",
        ).forEach { text ->
            assertNull(text, DialogConfig(delayText = text).delayMillis)
            assertNotNull(text, DialogConfig(delayText = text).validationError())
        }
    }

    @Test
    fun optionsPreserveOrderAndDuplicatesWhileRemovingEmptyLines() {
        val config = DialogConfig(optionsText = "  第一項 \r\n\r\n 第二項\n \t\n第一項 \r最後一項")

        assertEquals(listOf("第一項", "第二項", "第一項", "最後一項"), config.options)
    }

    @Test
    fun choiceDialogsRequireBetweenOneAndOneHundredNonblankOptions() {
        choiceKinds.forEach { kind ->
            assertNotNull(DialogConfig(kind = kind, optionsText = " \n\t ").validationError())
            assertNull(DialogConfig(kind = kind, optionsText = " 選項 ").validationError())
            val oneHundred = (1..100).joinToString("\n") { "選項 $it" }
            assertNull(DialogConfig(kind = kind, optionsText = "\n$oneHundred\n ").validationError())
            assertNotNull(DialogConfig(kind = kind, optionsText = "$oneHundred\n第 101 項").validationError())
        }
    }

    @Test
    fun optionValidationOnlyAppliesToChoiceDialogs() {
        (DialogKind.values().toList() - choiceKinds.toSet()).forEach { kind ->
            assertNull(DialogConfig(kind = kind, optionsText = "").validationError())
        }
    }

    @Test
    fun selectionAndInputDialogsRequireAConfirmationButton() {
        listOf(
            DialogKind.SINGLE_CHOICE, DialogKind.MULTI_CHOICE, DialogKind.TEXT_INPUT,
            DialogKind.DATE, DialogKind.TIME,
        ).forEach { kind ->
            assertNotNull(DialogConfig(kind = kind, positiveLabel = " \t").validationError())
            assertNull(DialogConfig(kind = kind, positiveLabel = "完成").validationError())
        }
    }

    @Test
    fun nonCancelableMessageAlwaysHasAWayToClose() {
        val config = DialogConfig(
            cancelable = false,
            positiveLabel = "",
            negativeLabel = " ",
            neutralLabel = "\t",
        )

        assertNotNull(config.validationError())
        assertNull(config.copy(positiveLabel = "好的").validationError())
        assertNull(config.copy(negativeLabel = "關閉").validationError())
        assertNull(config.copy(neutralLabel = "稍後").validationError())
        assertNull(config.copy(cancelable = true).validationError())
    }

    @Test
    fun immediateSelectionListDoesNotRequireAConfirmationButton() {
        assertNull(
            DialogConfig(
                kind = DialogKind.ITEMS,
                positiveLabel = "",
                negativeLabel = "",
                cancelable = false,
            ).validationError(),
        )
    }

    @Test
    fun invalidDelayIsReportedBeforeOtherValidationProblems() {
        val config = DialogConfig(
            kind = DialogKind.SINGLE_CHOICE,
            delayText = "-1",
            optionsText = "",
            positiveLabel = "",
        )

        assertEquals(DialogConfig(delayText = "-1").validationError(), config.validationError())
    }

    @Test
    fun titleAndMessageCanBeEmptyOrLong() {
        assertNull(DialogConfig(title = "", message = "").validationError())
        assertNull(DialogConfig(title = "標題".repeat(1_000), message = "內文".repeat(10_000)).validationError())
    }

    private val choiceKinds = listOf(
        DialogKind.ITEMS,
        DialogKind.SINGLE_CHOICE,
        DialogKind.MULTI_CHOICE,
    )
}
