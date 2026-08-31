package com.miriam.barcodetest.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * תחום ריצה שחי כל עוד האפליקציה חיה, ולא נקטע כשמסך נסגר.
 *
 * למה זה נחוץ: במסך ההוצאה המהירה יש חלון ביטול של כמה שניות, ורק אחריו
 * ההוצאה נשלחת לשרת. אם המשתמש/ת עוברים לטאב אחר בדיוק באותו רגע, השליחה
 * חייבת להסתיים גם אחרי שהמסך כבר נהרס - אחרת הוצאת מלאי אמיתית הייתה
 * נעלמת בלי שאיש ישים לב.
 *
 * SupervisorJob מבטיח שכישלון של שליחה אחת לא יבטל שליחות אחרות שממתינות.
 */
object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
