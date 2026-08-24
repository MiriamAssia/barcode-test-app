-- ============================================================================
-- ניהול מלאי מרפאה — סכמת בסיס נתונים ל-Supabase (Postgres)
-- שלב 1: items, batches, transactions + RLS
--
-- איך מריצים: Supabase Dashboard -> SQL Editor -> New query -> להדביק את כל
-- הקובץ -> Run. אפשר להריץ פעם אחת על בסיס נתונים ריק.
-- ============================================================================

-- הרחבה לחיפוש טקסט חופשי (ILIKE / trigram) בשם פריט
create extension if not exists pg_trgm;

-- ----------------------------------------------------------------------------
-- פונקציית עזר כללית: עדכון אוטומטי של updated_at בכל UPDATE
-- ----------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ============================================================================
-- profiles — תפקיד לכל משתמש/ת: staff (צוות קליני) או manager (מנהל/ת מלאי)
-- ============================================================================
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  role text not null default 'staff' check (role in ('staff', 'manager')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.profiles is 'תפקיד (staff/manager) לכל משתמש/ת מחוברת. נוצר אוטומטית בהרשמה עם role=staff.';

create trigger profiles_set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

-- יצירת שורת profile אוטומטית לכל משתמש/ת חדשה שנרשמת ב-Supabase Auth
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, full_name, role)
  values (new.id, new.raw_user_meta_data ->> 'full_name', 'staff');
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- בדיקת "האם המשתמש/ת הנוכחי/ת מנהל/ת" — security definer כדי למנוע
-- רקורסיה של RLS כשמדיניות על profiles בודקת את profiles עצמה
create or replace function public.is_manager()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'manager'
  );
$$;

-- ============================================================================
-- items — פריטי מלאי (בלי כמות! הכמות נגזרת מסכום ה-batches)
-- ============================================================================
create table public.items (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  category text,
  unit text not null default 'יחידה',            -- יחידת מידה: יחידה / אריזה / מ"ל וכו'
  min_quantity numeric(12, 3) not null default 0, -- כמות מינימלית להתראה
  is_active boolean not null default true,        -- "מחיקה" = כיבוי, לא מחיקה פיזית (שומר היסטוריה)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index items_name_trgm_idx on public.items using gin (name gin_trgm_ops);
create index items_category_idx on public.items (category);
create index items_is_active_idx on public.items (is_active);

create trigger items_set_updated_at
  before update on public.items
  for each row execute function public.set_updated_at();

-- ============================================================================
-- batches — אצוות מלאי, שייכות לפריט
-- quantity הוא "מטמון" שמתעדכן אוטומטית ע"י טריגר על transactions בלבד —
-- אף אחד לא כותב לעמודה הזו ישירות (ראו revoke בהמשך).
-- ============================================================================
create table public.batches (
  id uuid primary key default gen_random_uuid(),
  item_id uuid not null references public.items(id) on delete restrict,
  batch_number text,
  quantity numeric(12, 3) not null default 0 check (quantity >= 0),
  expiry_date date,
  supplier text,
  received_date date not null default current_date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index batches_item_id_idx on public.batches (item_id);
create index batches_expiry_date_idx on public.batches (expiry_date);
create index batches_item_expiry_idx on public.batches (item_id, expiry_date);
create index batches_qty_positive_idx on public.batches (item_id) where quantity > 0;

-- מונע כפילות מספר אצווה לאותו פריט (כשיש מספר אצווה בכלל)
create unique index batches_item_batchnumber_uidx
  on public.batches (item_id, batch_number)
  where batch_number is not null;

create trigger batches_set_updated_at
  before update on public.batches
  for each row execute function public.set_updated_at();

-- חוסם עדכון ישיר של הכמות מהאפליקציה — היא מתעדכנת רק דרך תנועות (transactions)
revoke update (quantity) on public.batches from authenticated;

-- ============================================================================
-- transactions — יומן תנועות (append-only, לא ניתן לעריכה/מחיקה)
-- ============================================================================
create table public.transactions (
  id uuid primary key default gen_random_uuid(),
  type text not null check (type in ('in', 'out', 'adjustment')),
  item_id uuid not null references public.items(id) on delete restrict,
  batch_id uuid references public.batches(id) on delete restrict,
  quantity numeric(12, 3) not null,
  reason text,      -- למשל: 'טיפול', 'פג תוקף', 'ספירה', 'קליטה'
  notes text,
  performed_by uuid not null default auth.uid() references auth.users(id),
  performed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  constraint transactions_quantity_sign check (
    (type in ('in', 'out') and quantity > 0) or
    (type = 'adjustment' and quantity <> 0)
  )
);

comment on column public.transactions.quantity is
  'תמיד חיובי עבור in/out. עבור adjustment יכול להיות חיובי או שלילי (הפרש לספירה).';

create index transactions_item_id_idx on public.transactions (item_id);
create index transactions_batch_id_idx on public.transactions (batch_id);
create index transactions_performed_at_idx on public.transactions (performed_at desc);
create index transactions_item_performed_at_idx on public.transactions (item_id, performed_at desc);
create index transactions_type_idx on public.transactions (type);

-- ----------------------------------------------------------------------------
-- טריגר: כל תנועה חדשה מעדכנת אוטומטית את quantity באצווה המשויכת
-- ----------------------------------------------------------------------------
create or replace function public.apply_transaction_to_batch()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  delta numeric(12, 3);
begin
  if new.batch_id is null then
    return new;
  end if;

  delta := case new.type
    when 'in' then new.quantity
    when 'out' then -new.quantity
    when 'adjustment' then new.quantity
  end;

  update public.batches
  set quantity = quantity + delta
  where id = new.batch_id;

  if not found then
    raise exception 'Batch % not found', new.batch_id;
  end if;

  return new;
end;
$$;

create trigger transactions_apply_to_batch
  after insert on public.transactions
  for each row execute function public.apply_transaction_to_batch();

-- ----------------------------------------------------------------------------
-- RPC: הוצאת מלאי מהירה לצוות הקליני (מסך שלב 3)
-- בוחר אצווה/ות אוטומטית לפי FEFO (תפוגה קרובה קודם), נועל שורות (for update)
-- כדי למנוע race condition כששתי אחיות לוחצות על אותו פריט באותו רגע,
-- ורושם תנועת 'out' לכל אצווה שנוגעת בה. staff לא כותב/ת ל-transactions
-- ישירות בכלל — רק דרך הפונקציה הזו (security definer עוקף את מדיניות ה-RLS
-- שמאפשרת INSERT ישיר רק למנהל/ת).
-- ----------------------------------------------------------------------------
create or replace function public.checkout_item(
  p_item_id uuid,
  p_quantity numeric,
  p_reason text default 'טיפול'
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  remaining numeric(12, 3) := p_quantity;
  b record;
  take numeric(12, 3);
begin
  if p_quantity <= 0 then
    raise exception 'הכמות חייבת להיות חיובית';
  end if;

  for b in
    select id, quantity
    from public.batches
    where item_id = p_item_id and quantity > 0
    order by expiry_date asc nulls last, received_date asc
    for update
  loop
    exit when remaining <= 0;
    take := least(b.quantity, remaining);

    insert into public.transactions (type, item_id, batch_id, quantity, reason, performed_by)
    values ('out', p_item_id, b.id, take, p_reason, auth.uid());

    remaining := remaining - take;
  end loop;

  if remaining > 0 then
    raise exception 'אין מספיק מלאי זמין (חסרות % יחידות)', remaining;
  end if;
end;
$$;

grant execute on function public.checkout_item(uuid, numeric, text) to authenticated;

-- ============================================================================
-- Views לתצוגה: סטטוס מלאי נוכחי, ואצוות קרובות לתפוגה
-- security_invoker=true חשוב! בלעדיו ה-view "יבריח" RLS כי הוא נוצר ע"י
-- postgres (superuser) — עם security_invoker, ה-RLS נבדק לפי מי שקורא ל-view.
-- ============================================================================
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
  end as status
from public.items i
left join public.batches b on b.item_id = i.id
group by i.id;

create or replace view public.expiring_batches
with (security_invoker = true) as
select
  b.id as batch_id,
  b.item_id,
  i.name as item_name,
  b.batch_number,
  b.quantity,
  b.expiry_date,
  (b.expiry_date - current_date) as days_until_expiry
from public.batches b
join public.items i on i.id = b.item_id
where b.quantity > 0 and b.expiry_date is not null
order by b.expiry_date asc;

grant select on public.item_stock_status to authenticated;
grant select on public.expiring_batches to authenticated;

-- ============================================================================
-- Row Level Security
-- ============================================================================
alter table public.profiles enable row level security;
alter table public.items enable row level security;
alter table public.batches enable row level security;
alter table public.transactions enable row level security;

-- --- profiles ---
-- כל משתמש/ת רואה את עצמו/ה; מנהל/ת רואה את כולם (למסך הרשאות בשלב 7)
create policy "profiles_select_own_or_manager"
  on public.profiles for select
  to authenticated
  using (id = auth.uid() or public.is_manager());

-- רק מנהל/ת יכול/ה לשנות תפקיד של משתמש/ת (כולל את עצמו/ה)
create policy "profiles_update_manager_only"
  on public.profiles for update
  to authenticated
  using (public.is_manager())
  with check (public.is_manager());

-- --- items ---
-- כולם (צוות + מנהל) רואים את רשימת הפריטים
create policy "items_select_all"
  on public.items for select
  to authenticated
  using (true);

-- רק מנהל/ת מוסיף/ה ומעדכנ/ת פריטים (מסך הגדרות, שלב 7)
create policy "items_insert_manager"
  on public.items for insert
  to authenticated
  with check (public.is_manager());

create policy "items_update_manager"
  on public.items for update
  to authenticated
  using (public.is_manager())
  with check (public.is_manager());

-- אין מדיניות DELETE בכוונה: "מחיקת" פריט = כיבוי is_active, כדי לשמר היסטוריה

-- --- batches ---
-- כולם רואים אצוות (כדי לדעת מה יש במלאי)
create policy "batches_select_all"
  on public.batches for select
  to authenticated
  using (true);

-- רק מנהל/ת קולט/ת מלאי חדש / מתקנ/ת פרטי אצווה (מסך קליטה, שלב 4)
create policy "batches_insert_manager"
  on public.batches for insert
  to authenticated
  with check (public.is_manager());

create policy "batches_update_manager"
  on public.batches for update
  to authenticated
  using (public.is_manager())
  with check (public.is_manager());

-- --- transactions ---
-- כולם רואים היסטוריית תנועות (למסך כרטיס פריט / דוחות)
create policy "transactions_select_all"
  on public.transactions for select
  to authenticated
  using (true);

-- INSERT ישיר מותר רק למנהל/ת (למשל קליטת מלאי -> תנועת 'in').
-- צוות קליני מוציא מלאי אך ורק דרך checkout_item(), שהיא security definer
-- ולכן עוקפת את המדיניות הזו — כלומר צוות לא יכול לכתוב ל-transactions ישירות בכלל.
create policy "transactions_insert_manager"
  on public.transactions for insert
  to authenticated
  with check (public.is_manager());

-- אין מדיניות UPDATE/DELETE בכוונה: יומן תנועות הוא append-only.
-- טעות מתוקנת ע"י רישום תנועת adjustment חדשה, לא ע"י עריכת ההיסטוריה.
