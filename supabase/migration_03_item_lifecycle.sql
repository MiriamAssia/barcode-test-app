-- ============================================================================
-- מיגרציה 03 - ניהול מחזור החיים של פריט: כיבוי/הפעלה ומחיקה לצמיתות
--
-- למה דרך פונקציות ולא ישירות מהאפליקציה:
--
-- 1. מחיקה. הסכמה המקורית (schema.sql) חוסמת מחיקת פריטים בכוונה - אין
--    מדיניות DELETE, ו-batches.item_id ו-transactions.item_id מוגדרים
--    on delete restrict. זה נשאר כך: החסימה ממשיכה להגן מפני מחיקה מקרית
--    דרך הטבלאות. הפונקציה כאן היא הפתח היחיד והמכוון, והיא מוחקת את שלוש
--    השכבות בסדר הנכון ובתוך טרנזקציה אחת - או שהכל נמחק, או ששום דבר.
--
-- 2. יומן התנועות. מדיניות ה-RLS לא מאפשרת DELETE על transactions לאף אחד,
--    והיומן נשאר append-only בשימוש רגיל. security definer כאן הוא היוצא מן
--    הכלל היחיד, והוא מוגבל למנהל/ת בלבד בבדיקה מפורשת בתוך הפונקציה.
--
-- שימו לב: מחיקת פריט מוחקת גם את היסטוריית התנועות שלו, ולכן דוחות צריכה
-- של תקופות עבר ישתנו למפרע. פריט שרוצים להוציא משימוש בלי לאבד היסטוריה -
-- מכבים עם set_item_active, לא מוחקים.
--
-- איך מריצים: Supabase Dashboard -> SQL Editor -> New query -> להדביק את כל
-- הקובץ -> Run. בטוח להרצה חוזרת.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- כיבוי / הפעלה מחדש של פריט
--
-- אפשר היה לעשות זאת גם ב-UPDATE רגיל (items_update_manager מתיר זאת), אבל
-- דרך פונקציה מקבלים בדיקת הרשאה מפורשת עם הודעה בעברית, ושגיאה ברורה כשה-
-- מזהה לא קיים במקום "עודכנו 0 שורות" שקט.
-- ----------------------------------------------------------------------------
create or replace function public.set_item_active(
  p_item_id uuid,
  p_active boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_manager() then
    raise exception 'רק מנהל/ת מלאי יכול/ה לשנות סטטוס של פריט';
  end if;

  update public.items
  set is_active = p_active
  where id = p_item_id;

  if not found then
    raise exception 'הפריט לא נמצא';
  end if;
end;
$$;

grant execute on function public.set_item_active(uuid, boolean) to authenticated;

comment on function public.set_item_active(uuid, boolean) is
  'כיבוי או הפעלה של פריט. שומר את כל ההיסטוריה - זו הדרך המומלצת להוציא פריט משימוש.';

-- ----------------------------------------------------------------------------
-- מחיקה לצמיתות של פריט, כולל כל האצוות והתנועות שלו
--
-- הסדר חשוב: קודם התנועות (שמצביעות גם על אצוות וגם על הפריט), אחר כך
-- האצוות, ורק בסוף הפריט עצמו. הכל בקריאה אחת ולכן בטרנזקציה אחת.
-- ----------------------------------------------------------------------------
create or replace function public.delete_item(p_item_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_manager() then
    raise exception 'רק מנהל/ת מלאי יכול/ה למחוק פריט';
  end if;

  delete from public.transactions where item_id = p_item_id;
  delete from public.batches where item_id = p_item_id;
  delete from public.items where id = p_item_id;

  if not found then
    raise exception 'הפריט לא נמצא';
  end if;
end;
$$;

grant execute on function public.delete_item(uuid) to authenticated;

comment on function public.delete_item(uuid) is
  'מחיקה בלתי הפיכה של פריט על כל האצוות והתנועות שלו. דוחות של תקופות עבר ישתנו למפרע.';

-- ============================================================================
-- בדיקה עצמית: אחרי ההרצה שתי השורות הבאות אמורות לחזור.
--
--   select routine_name from information_schema.routines
--   where routine_schema = 'public'
--     and routine_name in ('set_item_active', 'delete_item');
-- ============================================================================
