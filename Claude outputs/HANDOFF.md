# סיכום מצב טכני — אפליקציית ניהול מלאי מרפאה

> מסמך העברה לשיחה חדשה. נכון ל-10/09/2026.

---

## 1. מטרה וארכיטקטורה

אפליקציית אנדרואיד נייטיב לניהול מלאי במרפאה. שני סוגי משתמשים: **צוות קליני** שצריך להוציא מלאי במהירות מקסימלית, ו**מנהלת מלאי** שצריכה תמונת מצב, התראות ודוחות. ממשק בעברית, RTL, ערכת נושא כהה בלבד.

**ריפו:** `C:\Users\miria\Documents\GitHub\barcode-test-app` (מחובר דרך גשר המכשיר)

**מחסנית טכנולוגית**

| רכיב | בחירה |
|---|---|
| שפה | Kotlin, ViewBinding |
| ארכיטקטורה | Activity אחד מארח (`MainActivity`) + Fragments + `BottomNavigationView` בן 5 טאבים |
| ניווט | `supportFragmentManager` ידני — **בכוונה בלי Navigation Component**, כדי לא להוסיף תלויות |
| ללא | Compose, MVVM, Room, Navigation Component |
| Gradle | Groovy DSL. AGP `8.9.1`, Kotlin `2.4.0`, Gradle `8.11.1` (ב-CI) |
| SDK | compileSdk 34, minSdk 24, targetSdk 34 |
| Backend | Supabase (Postgres + PostgREST + Auth), SDK `supabase-kt` bom 3.7.0 |
| ערכת נושא | `Theme.Material3.Dark.NoActionBar` — dark-only מכוון |

**תלויות מרכזיות** (`app/build.gradle`): appcompat 1.6.1 · material 1.11.0 · constraintlayout 2.1.4 · zxing-android-embedded 4.3.0 · recyclerview 1.3.2 · supabase-bom 3.7.0 (postgrest-kt, auth-kt) · ktor-client-android 3.5.2 · kotlinx-serialization-json 1.11.0 · kotlinx-coroutines-android 1.11.0 · desugar_jdk_libs 2.1.5

**שתי הגדרות שחייבות להישאר:**
- `coreLibraryDesugaringEnabled true` — בלעדיה `java.time` לא עובד ב-minSdk 24, וכל התאריכים בקוד נשענים עליו.
- `resolutionStrategy { force 'androidx.browser:browser:1.8.0' }` — `auth-kt` מושך גרסה שדורשת compileSdk 36. הצימוד הזה מונע שדרוג ענק של כל שרשרת הכלים.

`SUPABASE_URL` ו-`SUPABASE_ANON_KEY` מוגדרים כ-`buildConfigField` ב-`app/build.gradle`.

---

## 2. מסד הנתונים (Supabase)

**קבצים:** `supabase/schema.sql` (שלב 1) · `supabase/migration_02_barcode.sql` (שלב 3). שניהם כבר הורצו בהצלחה.

**טבלאות:** `profiles` (role: staff/manager) · `items` · `batches` · `transactions`
**Views:** `item_stock_status` · `expiring_batches`
**RPC:** `checkout_item(p_item_id, p_quantity, p_reason)` — security definer, בוחר אצווה לפי FEFO עם נעילה
**טריגר:** `apply_transaction_to_batch()` — מעדכן את כמות האצווה מתנועות

### כללי ברזל של המודל — קריטי לא לשבור

1. **`transactions.quantity` תמיד חיובית** עבור `in` ו-`out`. הכיוון נשמר ב-`type`, **לא** בסימן המספר. רק `adjustment` יכול להיות שלילי. *(זו הייתה טעות אמיתית שנתפסה ותוקנה — ראו `StockDisplay.signedQuantity`.)*
2. **`batches.quantity` הוא מטמון** שמתעדכן רק ע"י הטריגר. אף פעם לא כותבים אליו ישירות (יש `revoke` ב-DB).
3. **אינדקס ייחודי חלקי** על `(item_id, batch_number)` כשה-batch_number לא null — לכן קליטה משתמשת ב-`findOrCreateBatch` ולא ב-insert עיוור.
4. **`items.barcode`** — אינדקס ייחודי חלקי, nullable.
5. **RLS:** `profiles` select = עצמי או מנהל/ת · `items`/`batches` insert+update = מנהל/ת בלבד · `transactions` insert = מנהל/ת בלבד → **צוות קליני מוציא מלאי רק דרך ה-RPC**.

---

## 3. מה פותח ועובד

### שלבים שהושלמו

| שלב | תוכן | מצב |
|---|---|---|
| 1 | סכמת DB + RLS | ✅ מוזג ל-main, נבדק |
| 2 | שכבת נתונים + התחברות | ✅ מוזג ל-main, נבדק במכשיר |
| 3 | מסך קליטת מלאי + ברקוד | ✅ מוזג ל-main, נבדק במכשיר |
| 4 | הוצאת מלאי מהירה + ביטול | ✅ מוזג ל-main, נבדק במכשיר |
| 5 | רשימת מלאי + כרטיס פריט | ✅ מוזג ל-main, נבדק במכשיר |
| 6 | מסך התראות + מונה | ⚠️ בבראנץ' בלבד, **לא נבדק** |
| 7 | דוח צריכה | ⚠️ בבראנץ' בלבד, **לא נבדק** |
| — | עדכון עיצוב מסך התחברות | ⚠️ בבראנץ' בלבד, **לא נבדק** |

### קבצי מקור (24 קבצי Kotlin)

```
LoginActivity.kt            מסך התחברות (Activity נפרד, אין לו סרגל ניווט)
MainActivity.kt             מארח: סרגל תחתון, החלפת Fragments, מונה התראות, הגדרות/התנתקות

data/
  AppScope.kt               CoroutineScope ברמת אפליקציה — לשליחות שחייבות לשרוד הריסת מסך
  Resource.kt               Loading/Success/Error + mapErrorToHebrewMessage
  SupabaseClientProvider.kt  singleton של הלקוח
  model/Models.kt           Item, NewItem, Batch, NewBatch, InventoryTransaction,
                            NewTransaction, Profile, ItemStockStatus, ExpiringBatch

data/repository/
  AuthRepository.kt         signIn, signOut, currentUserEmail/Id, sessionStatus
  ProfileRepository.kt      getCurrentProfile, getProfileNames
  ItemsRepository.kt        getActiveItems, getAllItems, getStockStatuses, addItem,
                            findItemByBarcode
  BatchesRepository.kt      getBatchesForItem, addBatch, findOrCreateBatch, getExpiringBatches
  TransactionsRepository.kt checkoutItem (RPC), recordTransaction, getTransactionsForItem,
                            getOutTransactionsBetween
  AlertsRepository.kt       getAlerts(expiryWarningDays) → Alerts(lowStock, expiring, allStatuses)

ui/
  StockDisplay.kt           פורמט משותף: כמויות, סימנים, תאריכים, צבעי סטטוס, EXPIRY_WARNING_DAYS
  CheckoutFragment.kt + CheckoutAdapter.kt      הוצאה מהירה
  IntakeFragment.kt                            קליטה
  InventoryFragment.kt + InventoryAdapter.kt   רשימת מלאי
  ItemDetailFragment.kt                        כרטיס פריט
  AlertsFragment.kt + AlertsAdapter.kt         התראות
  ReportsFragment.kt + ReportsAdapter.kt       דוח צריכה
  PlaceholderFragment.kt                       ← כבר לא בשימוש, אפשר למחוק
```

### משאבים

**19 layouts** · **31 drawables** (כולם vector/shape שנכתבו ידנית) · `values/`: colors, dimens, strings, themes · `menu/bottom_nav_menu.xml` · `color/bottom_nav_item.xml`

### מערכת העיצוב

תורגמה מ-`DESIGN.md` שיוצא מ-Stitch. הקבצים `colors.xml` (30 צבעים בקידומת `md_`), `dimens.xml` (בסיס 8dp, יעד מגע מינימלי 56dp), `themes.xml` (סגנונות `TextAppearance.MedTrack.*` ו-`Widget.MedTrack.*`).

> **גופן Rubik לא נכלל** — לא הייתה גישה לרשת להורדת הקובץ. האפליקציה משתמשת בגופן ברירת המחדל. אם רוצים את Rubik: להוריד מ-Google Fonts, לשים ב-`res/font/`, ולהצביע עליו מ-`themes.xml`.

---

## 4. מצב נוכחי ומשימה פתוחה

**בראנץ' נוכחי:** `stage-6-alerts` — מכיל את שלבים 6, 7 ואת עדכון מסך ההתחברות.
**קומיט אחרון:** `d5bb7a9` — "Polish login screen to match the app design system". מקומי ו-origin מסונכרנים.
**main:** `15fb39d` — מכיל עד שלב 5 כולל.

### ⏭️ המשימה הפתוחה היחידה

**בדיקה ידנית של שלבים 6, 7 ומסך ההתחברות במכשיר.** מיריam דחתה את הבדיקה ובחרה לבדוק את שלושתם יחד.

מה לבדוק:
- **התראות:** צריך שיהיו התראות אמיתיות. להוציא פריט עד מתחת לסף המינימום, ולקלוט פריט עם תפוגה קרובה. לוודא שהמונה האדום על אייקון ההתראות עולה, ושהקשה על התראה פותחת את כרטיס הפריט.
- **דוחות:** לבדוק את ארבעת טווחי הזמן, מעבר בין פילוח לפי פריט/קטגוריה, ושהסיכומים מסתדרים מול הנתונים בסופאבייס.
- **כרטיס פריט:** לוודא שהוצאה מוצגת עכשיו כ-**"3−"** ולא "3+" (זה התיקון שנעשה בשלב 7).
- **מסך התחברות:** מראה חדש, פתיחת מקלדת לא חותכת את הטופס, "אישור" במקלדת מתחבר.

**אחרי שהבדיקה עוברת:** למזג את `stage-6-alerts` ל-`main`.

### מה נשאר בתוכנית המקורית

1. **מסך הגדרות והרשאות** — ניהול פריטים (עריכה/כיבוי), קידום משתמשים ל-manager. כרגע יש רק דיאלוג זמני עם התנתקות ב-`MainActivity.showSettingsDialog()`. הסכמה כבר תומכת: `profiles_update_manager_only`.
2. **בדיקות ובנייה סופית.**
3. **תמיכה במצב לא מקוון** — תור מבוסס Room. **הוסכם: להסביר את הגישה ולקבל אישור לפני שכותבים קוד.**

---

## 5. קוד קריטי לרצף

### מנגנון הביטול בהוצאת מלאי (`CheckoutFragment`)

ההחלטה שהתקבלה: **לעכב את הכתיבה ב-4 שניות**, ולא לרשום-ואז-לבטל. היומן נשאר נקי — מה שבוטל לא נרשם מעולם.

```kotlin
private var pendingItem: ItemStockStatus? = null
private var pendingQuantity: Double = 0.0
private val handler = Handler(Looper.getMainLooper())
private val flushRunnable = Runnable { flushPending() }
private companion object { const val UNDO_WINDOW_MS = 4000L }
```

שלוש נקודות שאסור לשבור:
- הקשות חוזרות על **אותו** פריט **מצטברות** לרישום אחד ומאתחלות את הטיימר.
- מעבר לפריט **אחר** שולח קודם את הקודם.
- `onPause()` קורא ל-`flushPending()` — והשליחה רצה ב-`AppScope.io` ולא ב-`viewLifecycleOwner.lifecycleScope`, **כדי שתסתיים גם אם המסך נהרס באותו רגע**. זו ההגנה מפני איבוד רישום מלאי אמיתי.

### סימן הכמות (`StockDisplay`)

```kotlin
const val EXPIRY_WARNING_DAYS = 30L   // ← שינוי כאן משנה גם התראות וגם כרטיס פריט

fun signedQuantity(type: String, value: Double): String = when (type) {
    "in"  -> "+" + quantity(value)
    "out" -> "-" + quantity(kotlin.math.abs(value))
    else  -> if (value >= 0) "+" + quantity(value) else "-" + quantity(kotlin.math.abs(value))
}
```

### תבנית ה-Fragment החוזרת בכל המסכים

```kotlin
private var _binding: FragmentXBinding? = null
private val binding get() = _binding!!
// onCreateView: _binding = FragmentXBinding.inflate(inflater, container, false)
// onDestroyView: binding.list.adapter = null; super.onDestroyView(); _binding = null
// בקורוטינות: val views = _binding ?: return  — לפני נגיעה ב-UI
```

### תבנית ה-Repository

```kotlin
suspend fun something(): Resource<T> = try {
    val rows = postgrest.from("table").select { filter { eq("col", value) } }.decodeList<T>()
    Resource.Success(rows)
} catch (e: Exception) {
    Resource.Error(mapErrorToHebrewMessage(e), e)
}
```

---

## 6. הנחיות מיוחדות — כללי עבודה שסוכמו

### תהליך Git (מיריam קובעת)

1. **לעולם לא לעבוד ישירות על `main`.**
2. לפני כל שלב חדש: **לבקש ממנה ליצור בראנץ'** בשם ברור, ו**להמתין לאישור** שהיא עליו לפני שנוגעים בקבצים.
3. אני **כותב את הקבצים ישירות לתיקיית הפרויקט** דרך גשר המכשיר. **לעולם לא שולח קבצים להעלאה ידנית.**
4. בסוף שלב: לפרט אילו קבצים נוצרו/שונו, ולבקש ממנה Commit + Push.
5. **הודעות קומיט באנגלית, קצרות.** שורה אחת.
6. היא מבצעת את המיזוגים ל-main בעצמה, רק אחרי שבדקה.
7. לבנות שלב-אחר-שלב, בלי לדלג קדימה לפני ששלב מאומת.

### מגבלה טכנית קריטית

**אני לא יכול לבנות מקומית** — Maven Central, Google Maven ו-Gradle Plugin Portal חסומים בסביבה שלי (מאומת: `curl` מחזיר 000). **GitHub Actions הוא אמצעי האימות היחיד.**

**כשבנייה נכשלת:** לבקש מיד את **הלוג המלא** — לא לנחש מקטעים חלקיים. הדרך: בדף ההרצה → "..." → "Download log archive" → הקובץ נוחת ב-`C:\Users\miria\Downloads` → אני שולף ומנתח אותו משם. *(בעבר בוזבזו שלושה סבבים על ניחושים מלוגים קטועים.)*

### בדיקה עצמית לפני כל מסירה — חובה

מכיוון שאין בנייה מקומית, לפני שליחת קבצים אני מריץ:
1. תקינות XML של כל קבצי `res/`
2. שכל הפניה למשאב (`@drawable`, `@color`, `@string`, `@dimen`, `@style`) מוגדרת בפועל — משני הכיוונים (XML ו-`R.*` מהקוד)
3. הצלבת כל `binding.<שדה>` מול ה-IDs בקובץ הפריסה
4. איזון סוגריים בכל קבצי ה-Kotlin

השיטה הזו תפסה בפועל: הערות XML עם רצף מקפים (לא חוקי), `paddingHorizontal` שלא נתמך ב-minSdk 24, סגנון בלי `parent` מפורש, ו-`minHeight="match_parent"` — כולם היו מפילים את הבנייה.

### מלכודות שכבר נתקלנו בהן

- **`.github/workflows/*.yml` הוא קובץ מוגן** — אני לא יכול לכתוב אליו. שינויים שם צריכים להיעשות ידנית.
- קבצים בעומק של 8+ תיקיות **לא ניתנים ל-staging** (קריאה), אבל **כן ניתנים לכתיבה**. יש עותק עבודה מלא ב-`/tmp/repo-work` בסביבה שלי.
- **הערות XML לא יכולות להכיל `--`.**
- **`paddingHorizontal`/`paddingVertical` דורשים API 26** — להשתמש ב-Start/End/Top/Bottom.
- **סגנון עם נקודה בשם חייב `parent` מפורש** (או `parent=""`), אחרת aapt מחפש הורה שלא קיים.
- הגשר למחשב **מתנתק מדי פעם**. אם קרה: לא לשלוח קבצים להעלאה ידנית — להמתין ולכתוב אותם כשהחיבור חוזר.

### העדפות מוצר שהובעו

- כותרת עליונה **קבועה** ("ניהול מלאי רפואי") — **בלי** כותרת נפרדת לכל מסך. הסרגל התחתון כבר אומר איפה נמצאים.
- שדה "מיקום במחסן" **הוסר במכוון**. אם יידרש בעתיד — להוסיף מחדש.
- הדוחות: **בעיקר "כמה השתמשנו"**. ייצוא/שיתוף — לא נדרש בשלב זה.
- ברקוד לא מוכר → **לפתוח טופס יצירת פריט חדש** ולהמשיך את הקליטה.
