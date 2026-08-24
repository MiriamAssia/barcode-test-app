package com.miriam.barcodetest.data

/**
 * עטיפה אחידה לתוצאה של פעולה מול השרת: טעינה / הצלחה עם נתונים / שגיאה עם
 * הודעה בעברית. כל מסך שמשתמש ב-repository מקבל את זה כדי להציג מצב
 * טעינה/שגיאה בצורה אחידה, בלי שכל מסך יטפל בזה בעצמו מחדש.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()
}

/**
 * ממפה חריגות נפוצות (רשת, הרשאה, לא נמצא) להודעת שגיאה קריאה בעברית,
 * כדי לא לחזור על אותה לוגיקה בכל repository.
 */
fun mapErrorToHebrewMessage(e: Throwable): String {
    val msg = e.message ?: ""
    return when {
        msg.contains("Unable to resolve host", true) ||
            msg.contains("timeout", true) ||
            msg.contains("Failed to connect", true) ||
            msg.contains("ConnectException", true) ->
            "אין חיבור לאינטרנט. בדקי את החיבור ונסי שוב."
        msg.contains("JWT", true) || msg.contains("401", true) || msg.contains("permission denied", true) ->
            "אין לך הרשאה לבצע פעולה זו."
        msg.contains("PGRST116", true) ->
            "הפריט המבוקש לא נמצא."
        else -> "משהו השתבש: ${e.message ?: "שגיאה לא ידועה"}"
    }
}
