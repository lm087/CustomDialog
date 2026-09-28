package com.mt.customDialog

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class NativeDialogsTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val results = mutableListOf<String>()
    private var currentDialog: Dialog? = null

    @Before
    fun launchActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { DialogState.finish(it, "") }
    }

    @After
    fun closeActivity() {
        scenario.onActivity { currentDialog?.dismiss() }
        scenario.close()
    }

    @Test
    fun singleAndMultilineFieldsShareTextAndUnderlineSpacing() {
        scenario.onActivity { activity ->
            activity.findViewById<Spinner>(R.id.kind).setSelection(DialogKind.ITEMS.ordinal)
            activity.findViewById<EditText>(R.id.title).setText("Title")
            activity.findViewById<EditText>(R.id.message).setText("First\nSecond")
            activity.findViewById<EditText>(R.id.options).setText("One\nTwo\nThree")
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val fields = listOf(R.id.title, R.id.message, R.id.options).map { activity.findViewById<EditText>(it) }
            val title = fields.first()
            fun bottomGap(field: EditText): Int {
                val layout = field.layout
                val lastBaseline = field.baseline + layout.getLineBaseline(layout.lineCount - 1) - layout.getLineBaseline(0)
                return field.height - lastBaseline
            }
            assertEquals(1, title.lineCount)
            assertEquals(2, fields[1].lineCount)
            assertEquals(3, fields[2].lineCount)
            fields.forEach { field ->
                assertEquals(title.baseline, field.baseline)
                assertEquals(bottomGap(title), bottomGap(field))
            }
        }
    }

    @Test
    fun frameworkResourcesStayEnglishOnNonEnglishDevices() {
        val base = instrumentation.targetContext
        listOf(Locale.TRADITIONAL_CHINESE, Locale.forLanguageTag("ar")).forEach { locale ->
            val deviceContext = base.createConfigurationContext(
                Configuration(base.resources.configuration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                    fontScale = 1.3f
                },
            )
            val english = deviceContext.englishContext()
            assertEquals("Cancel", english.getString(android.R.string.cancel))
            assertEquals(View.LAYOUT_DIRECTION_LTR, english.resources.configuration.layoutDirection)
            assertEquals(1.3f, english.resources.configuration.fontScale)
        }
    }

    @Test
    fun allSevenKindsShowAsFrameworkDialogs() {
        DialogKind.entries.forEach { kind ->
            show(DialogConfig(kind = kind))
            withDialog { dialog ->
                assertTrue("$kind must show", dialog.isShowing)
                when (kind) {
                    DialogKind.DATE -> assertTrue(dialog is DatePickerDialog)
                    DialogKind.TIME -> assertTrue(dialog is TimePickerDialog)
                    else -> assertTrue(dialog is AlertDialog)
                }
                assertEquals("OK", dialog.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
                dialog.dismiss()
            }
        }
        assertTrue("Dismissing for cleanup must not submit a result", results.isEmpty())
    }

    @Test
    fun choiceDialogsKeepTitleMessageAndNativeRowsVisible() {
        listOf(DialogKind.ITEMS, DialogKind.SINGLE_CHOICE, DialogKind.MULTI_CHOICE).forEach { kind ->
            show(choiceConfig(kind))
            withDialog { dialog ->
                val visibleText = textViews(dialog.window!!.decorView)
                    .filter { it.isShown }.map { it.text.toString() }
                assertTrue("Title missing for $kind", "Choose a color" in visibleText)
                assertTrue("Message missing for $kind", "Select items" in visibleText)
                assertTrue("First native row missing for $kind", "Red" in visibleText)
                assertEquals(3, dialog.listView.count)
                dialog.dismiss()
            }
        }
    }

    @Test
    fun simpleItemsSubmitImmediately() {
        show(choiceConfig(DialogKind.ITEMS))
        withDialog { tapRow(it.listView, 1) }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Selected: Green"), results)
        withDialog { assertFalse(it.isShowing) }
    }

    @Test
    fun singleChoiceSubmitsTheSelectedRow() {
        show(choiceConfig(DialogKind.SINGLE_CHOICE))
        withDialog {
            tapRow(it.listView, 1)
            assertEquals(1, it.listView.checkedItemPosition)
            assertTrue(results.isEmpty())
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Selected: Green"), results)
    }

    @Test
    fun multipleChoiceSubmitsOnlyCheckedRows() {
        show(choiceConfig(DialogKind.MULTI_CHOICE))
        withDialog {
            tapRow(it.listView, 0)
            tapRow(it.listView, 1)
            tapRow(it.listView, 2)
            tapRow(it.listView, 1)
            assertTrue(results.isEmpty())
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Selected: Red, Blue"), results)
    }

    @Test
    fun textInputSubmitsEditedTextAndUsesConfiguredHint() {
        show(DialogConfig(kind = DialogKind.TEXT_INPUT, inputHint = "Enter a note", inputDefault = "Default text"))
        withDialog {
            val input = it.findViewById<EditText>(android.R.id.edit)
            assertEquals("Enter a note", input.hint.toString())
            assertEquals("Default text", input.text.toString())
            input.setText("Edited text")
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Input: Edited text"), results)
    }

    @Test
    fun blankButtonLabelsHideButtonsAndNeutralActionReturnsItsLabel() {
        show(DialogConfig(positiveLabel = "", negativeLabel = "", neutralLabel = "Later"))
        withDialog {
            assertEquals(View.GONE, it.getButton(DialogInterface.BUTTON_POSITIVE).visibility)
            assertEquals(View.GONE, it.getButton(DialogInterface.BUTTON_NEGATIVE).visibility)
            assertEquals("Later", it.getButton(DialogInterface.BUTTON_NEUTRAL).text.toString())
            it.getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Pressed: Later"), results)
    }

    @Test
    fun nativePickerButtonsKeepCustomLabelsAndPickerCallbacks() {
        show(DialogConfig(kind = DialogKind.DATE, positiveLabel = "Select date", negativeLabel = "Back"))
        withDialog {
            assertEquals("Select date", it.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
            assertEquals("Back", it.getButton(DialogInterface.BUTTON_NEGATIVE).text.toString())
            (it as DatePickerDialog).updateDate(2030, 0, 2)
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Date: 2030/01/02"), results)

        show(DialogConfig(kind = DialogKind.TIME, positiveLabel = "Select time", negativeLabel = ""))
        withDialog {
            assertEquals("Select time", it.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
            assertEquals(View.GONE, it.getButton(DialogInterface.BUTTON_NEGATIVE).visibility)
            (it as TimePickerDialog).updateTime(13, 7)
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals("Time: 13:07", results.last())
        assertEquals(2, results.size)
    }

    @Test
    fun backCancellationReportsOnceAndRespectsCancelableSetting() {
        show(DialogConfig(cancelable = false))
        withDialog {
            it.onBackPressed()
            assertTrue(it.isShowing)
            assertTrue(results.isEmpty())
            it.dismiss()
        }

        show(DialogConfig(cancelable = true))
        withDialog { it.onBackPressed() }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("Dismissed"), results)
        withDialog { assertFalse(it.isShowing) }
    }

    @Test
    fun previewRecreationRestoresEditedTextAndCancelableSetting() {
        showPreview(DialogConfig(kind = DialogKind.TEXT_INPUT, inputDefault = "Original text", cancelable = false))
        withPreview { dialog ->
            dialog.findViewById<EditText>(android.R.id.edit).setText("Keep after rotation")
        }
        scenario.recreate()
        withPreview { dialog ->
            assertEquals("Keep after rotation", dialog.findViewById<EditText>(android.R.id.edit).text.toString())
            dialog.onBackPressed()
            assertTrue(dialog.isShowing)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { assertEquals("Input: Keep after rotation", DialogState.snapshot(it).result) }
    }

    @Test
    fun previewRecreationRestoresSingleChoice() {
        showPreview(choiceConfig(DialogKind.SINGLE_CHOICE))
        withPreview { tapRow(it.listView, 2) }
        scenario.recreate()
        withPreview {
            assertEquals(2, it.listView.checkedItemPosition)
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { assertEquals("Selected: Blue", DialogState.snapshot(it).result) }
    }

    @Test
    fun previewRecreationRestoresMultipleChoicesAndAllowsFurtherChanges() {
        showPreview(choiceConfig(DialogKind.MULTI_CHOICE))
        withPreview {
            tapRow(it.listView, 0)
            tapRow(it.listView, 2)
        }
        scenario.recreate()
        withPreview {
            assertTrue(it.listView.isItemChecked(0))
            assertFalse(it.listView.isItemChecked(1))
            assertTrue(it.listView.isItemChecked(2))
            tapRow(it.listView, 0)
            tapRow(it.listView, 1)
            it.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { assertEquals("Selected: Green, Blue", DialogState.snapshot(it).result) }
    }

    private fun show(config: DialogConfig) {
        scenario.onActivity { activity ->
            currentDialog?.dismiss()
            currentDialog = NativeDialogs.create(activity, config) { results.add(it) }.apply { show() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun withDialog(action: (AlertDialog) -> Unit) {
        scenario.onActivity { action(currentDialog as AlertDialog) }
    }

    private fun showPreview(config: DialogConfig) {
        scenario.onActivity {
            PreviewDialogFragment.newInstance(config).show(it.fragmentManager, PreviewDialogFragment.TAG)
            it.fragmentManager.executePendingTransactions()
        }
        instrumentation.waitForIdleSync()
    }

    private fun withPreview(action: (AlertDialog) -> Unit) {
        scenario.onActivity {
            val fragment = it.fragmentManager.findFragmentByTag(PreviewDialogFragment.TAG) as PreviewDialogFragment
            action(fragment.dialog as AlertDialog)
        }
    }

    private fun choiceConfig(kind: DialogKind) = DialogConfig(
        kind = kind,
        title = "Choose a color",
        message = "Select items",
        optionsText = "Red\nGreen\nBlue",
    )

    private fun tapRow(list: ListView, index: Int) {
        val row = list.getChildAt(index - list.firstVisiblePosition)
        assertNotNull("Option $index should be visible", row)
        list.performItemClick(row, index, list.adapter.getItemId(index))
    }

    private fun textViews(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(textViews(view.getChildAt(index)))
        }
    }
}
