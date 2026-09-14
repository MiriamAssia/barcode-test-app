-- ============================================================================
-- מיגרציה 04 - עריכת פרטי פריט קיים
--
-- למה זה נחוץ: עד עכשיו אפשר היה רק ליצור פריט (בדיאלוג שנפתח כשסורקים
-- ברקוד לא מוכר) ולמחוק או לכבות אותו. לא הייתה שום דרך לתקן טעות. זה כאב
-- במיוחד ב-min_quantity: הסף הזה מפעיל את כל מערכת התראות המלאי הנמוך,
-- ונקבע פעם אחת בלבד ברגע היצירה.
--
-- מדיניות items_update_manager (שלב 1) כבר מתירה UPDATE למנהל/ת, אז מבחינת
-- הרשאות אין כאן חידוש. הפונקציה קיימת כדי לקבל בדיקה מפורשת עם הודעה
-- בעברית, ולידציה של השם, ושגיאה ברורה כשהמזהה לא קיים.
--
-- תיקוני ספירת מלאי (transactions מסוג 'adjustment') לא דורשים כאן כלום -
-- הסכמה מאפשרת אותם מאז שלב 1, והאפליקציה רושמת אותם דרך INSERT רגיל.
--
-- איך מריצים: Supabase Dashboard -> SQL Editor -> New query -> להדביק את כל
-- הקובץ -> Run. בטוח להרצה חוזרת.
-- ============================================================================

create or replace function public.update_item(
  p_item_id      uuid,
  p_name         text,
  p_category     text,
  p_unit         text,
  p_min_quantity numeric,
  p_barcode      text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_manager() then
    raise exception 'רק מנהל/ת מלאי יכול/ה לערוך פריט';
  end if;

  if p_name is null or btrim(p_name) = '' then
    raise exception 'שם הפריט לא יכול להיות ריק';
  end if;

  if p_min_quantity is null or p_min_quantity < 0 then
    raise exception 'הכמות המינימלית לא יכולה להיות שלילית';
  end if;

  -- ערכים ריקים נשמרים כ-null ולא כמחרוזת ריקה, כדי שהאינדקס הייחודי החלקי
  -- על barcode ימשיך לעבוד (מחרוזת ריקה אינה null, ושני פריטים בלי ברקוד היו
  -- מתנגשים זה בזה).
  update public.items
  set name         = btrim(p_name),
      category     = nullif(btrim(coalesce(p_category, '')), ''),
      unit         = coalesce(nullif(btrim(coalesce(p_unit, '')), ''), 'יחידה'),
      min_quantity = p_min_quantity,
      barcode      = nullif(btrim(coalesce(p_barcode, '')), '')
  where id = p_item_id;

  if not found then
    raise exception 'הפריט לא נמצא';
  end if;
end;
$$;

grant execute on function public.update_item(uuid, text, text, text, numeric, text) to authenticated;

comment on function public.update_item(uuid, text, text, text, numeric, text) is
  'עריכת פרטי פריט קיים. לשינוי סטטוס פעיל/כבוי יש set_item_active נפרדת.';

-- ----------------------------------------------------------------------------
-- הוספת barcode ל-view של סטטוס המלאי
--
-- רשימת המלאי עובדת מול ה-view הזה, וטופס העריכה צריך להציג את הברקוד הקיים
-- כדי שאפשר יהיה לתקן אותו. בלי העמודה כאן היה צריך שאילתה נוספת לכל פריט
-- רק בשביל שדה אחד.
--
-- שימו לב: זו אותה הגדרה בדיוק כמו ב-schema.sql, עם עמודה אחת נוספת בסוף.
-- create or replace view מחייב שכל העמודות הקיימות יישארו באותו שם ובאותו
-- סדר, ומתיר רק הוספה בסוף.
-- ----------------------------------------------------------------------------
create or replace view public.item_stock_status
with (security_invoker = true) as
select
  i.id,
  i.name,
  i.category,
  i.unit,
  i.min_quantity,
  i.is_active,
  coalesce(sum(b.quantity) filter (where b.quantity > 0), 0) as current_quantity,
  case
    when coalesce(sum(b.quantity) filter (where b.quantity > 0), 0) <= 0 then 'out'
    when coalesce(sum(b.quantity) filter (where b.quantity > 0), 0) <= i.min_quantity then 'low'
    else 'ok'
  end as status,
  i.barcode
from public.items i
left join public.batches b on b.item_id = i.id
group by i.id;

grant select on public.item_stock_status to authenticated;

-- ============================================================================
-- בדיקה עצמית: אחרי ההרצה שתי השאילתות הבאות אמורות להחזיר תוצאה.
--
--   select routine_name from information_schema.routines
--   where routine_schema = 'public' and routine_name = 'update_item';
--
--   select column_name from information_schema.columns
--   where table_schema = 'public' and table_name = 'item_stock_status'
--     and column_name = 'barcode';
-- ============================================================================
