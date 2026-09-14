package com.miriam.barcodetest.ui

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * בורר תאריכים אחיד לכל המערכת.
 *
 * למה MaterialDatePicker ולא DatePickerDialog של אנדרואיד: הראשון נפתח במצב
 * הקלדה - אפשר פשוט להקליד את התאריך - ויש בו מתג ללוח שנה למי שמעדיפה
 * לבחור. ב-DatePickerDialog הישן אין הקלדה בכלל, רק גלגלים, וזה היה איטי
 * במיוחד לתאריך תפוגה שנמצא שנתיים קדימה.
 *
 * שימו לב לאזור הזמן: MaterialDatePicker עובד בחצות UTC. ההמרה לשני הכיוונים
 * נעשית כאן ב-ZoneOffset.UTC במפורש - עם אזור הזמן המקומי התאריך היה יכול
 * לזוז ביום אחד.
 */
object DatePickers {

    fun show(
        fragment: Fragment,
        @StringRes titleRes: Int,
        initial: LocalDate,
        onPicked: (LocalDate) -> Unit
    ) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .setSelection(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setInputMode(MaterialDatePicker.INPUT_MODE_TEXT)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }

        picker.show(fragment.childFragmentManager, "date_picker")
    }
}
