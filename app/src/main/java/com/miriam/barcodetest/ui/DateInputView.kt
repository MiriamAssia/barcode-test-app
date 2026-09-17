package com.miriam.barcodetest.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.miriam.barcodetest.R
import java.time.DateTimeException
import java.time.LocalDate

/**
 * שדה תאריך מפוצל לשלושה קטעים - יום/חודש/שנה, פורמט dd/mm/yyyy קבוע
 * שלא תלוי בלוקאל של המכשיר.
 *
 * למה לא MaterialDatePicker במצב הקלדה: הפורמט שלו נגזר מלוקאל המכשיר
 * (יכול לצאת MM/dd/yy במכשיר עם לוקאל אחר), ואין בו מעבר/סימון אוטומטי בין
 * קטעים. כאן כל קטע הוא EditText נפרד עם selectAllOnFocus: הקשה על קטע
 * מסמנת את מה שכתוב בו ומוכנה לדריסה, ומילוי קטע עד סופו מעביר את הפוקוס
 * לקטע הבא ומסמן אותו אוטומטית - בלי צורך בלוגיקת מיסוך של מחרוזת אחת.
 */
class DateInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dayInput: EditText
    private val monthInput: EditText
    private val yearInput: EditText

    /** נקרא בכל הקלדה בכל אחד מהקטעים, גם כשהתאריך עדיין חלקי או לא תקין */
    var onDateChanged: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        // התאריך תמיד dd/mm/yyyy משמאל לימין, גם באפליקציה RTL - בלי זה
        // סדר שלושת הקטעים היה מתהפך ל-yyyy/mm/dd.
        layoutDirection = LAYOUT_DIRECTION_LTR
        LayoutInflater.from(context).inflate(R.layout.view_date_input, this, true)

        dayInput = findViewById(R.id.dateDayInput)
        monthInput = findViewById(R.id.dateMonthInput)
        yearInput = findViewById(R.id.dateYearInput)

        watchSegment(dayInput, DAY_LENGTH, monthInput)
        watchSegment(monthInput, MONTH_LENGTH, yearInput)
        watchSegment(yearInput, YEAR_LENGTH, null)

        jumpBackOnDelete(monthInput, dayInput)
        jumpBackOnDelete(yearInput, monthInput)
    }

    /** ממלא את שלושת הקטעים מתאריך קיים, עם אפסים מובילים; null מנקה */
    fun setDate(date: LocalDate?) {
        dayInput.setText(date?.dayOfMonth?.let { "%02d".format(it) }.orEmpty())
        monthInput.setText(date?.monthValue?.let { "%02d".format(it) }.orEmpty())
        yearInput.setText(date?.year?.let { "%04d".format(it) }.orEmpty())
    }

    fun clear() = setDate(null)

    /** התאריך שהוזן, או null אם השדה ריק בכוונה או שהערכים לא מרכיבים תאריך תקין */
    fun getDate(): LocalDate? {
        val day = dayInput.text.toString().toIntOrNull() ?: return null
        val month = monthInput.text.toString().toIntOrNull() ?: return null
        val yearText = yearInput.text.toString()
        if (yearText.length < YEAR_LENGTH) return null
        val year = yearText.toIntOrNull() ?: return null
        if (year < MIN_YEAR || year > MAX_YEAR) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * true אם הוקלד משהו באחד הקטעים אבל זה לא מצטרף לתאריך תקין - כדי
     * להבדיל מ"השדה ריק בכוונה" (למשל תאריך תפוגה שהוא רשות).
     */
    fun hasInvalidPartialInput(): Boolean {
        val anyFilled = dayInput.text.isNotEmpty() ||
            monthInput.text.isNotEmpty() ||
            yearInput.text.isNotEmpty()
        return anyFilled && getDate() == null
    }

    private fun watchSegment(field: EditText, fullLength: Int, next: EditText?) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateValidityTint()
                onDateChanged?.invoke()
                if (next != null && (s?.length ?: 0) >= fullLength) {
                    next.requestFocus()
                }
            }
        })
    }

    /**
     * Backspace על קטע ריק עוברת לקטע הקודם ומוחקת את התו האחרון שלו, כדי
     * שאפשר יהיה למחוק תאריך שלם ברצף אחד של מקש מחיקה, בלי לצטרך להקיש
     * חזרה על הקטע הקודם.
     */
    private fun jumpBackOnDelete(field: EditText, previous: EditText) {
        field.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN &&
                field.text.isEmpty()
            ) {
                val previousLength = previous.text.length
                if (previousLength > 0) {
                    previous.text.delete(previousLength - 1, previousLength)
                }
                previous.requestFocus()
                previous.setSelection(previous.text.length)
                true
            } else {
                false
            }
        }
    }

    private fun updateValidityTint() {
        val color = ContextCompat.getColor(
            context,
            if (hasInvalidPartialInput()) R.color.md_error else R.color.md_on_surface
        )
        dayInput.setTextColor(color)
        monthInput.setTextColor(color)
        yearInput.setTextColor(color)
    }

    private companion object {
        const val DAY_LENGTH = 2
        const val MONTH_LENGTH = 2
        const val YEAR_LENGTH = 4
        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2100
    }
}
