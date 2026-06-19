drop function if exists public.increment_photos_analyzed(text);

create or replace function public.increment_photos_analyzed(p_user_id uuid)
returns void language plpgsql as $$
begin
  insert into user_profiles (user_id, photos_analyzed)
  values (p_user_id, 1)
  on conflict (user_id)
  do update set photos_analyzed = user_profiles.photos_analyzed + 1;
end;
$$;