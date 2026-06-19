create extension if not exists "pg_cron" with schema "pg_catalog";

drop extension if exists "pg_net";


  create table "public"."chat_history" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "role" text not null,
    "content" text not null,
    "created_at" timestamp with time zone default now(),
    "session_id" uuid
      );


alter table "public"."chat_history" enable row level security;


  create table "public"."deleted_accounts" (
    "id" uuid not null default gen_random_uuid(),
    "email" text not null,
    "deleted_at" timestamp with time zone default now()
      );


alter table "public"."deleted_accounts" enable row level security;


  create table "public"."lesson_completions" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "lesson_id" text not null,
    "completed_at" timestamp with time zone not null default now()
      );


alter table "public"."lesson_completions" enable row level security;


  create table "public"."lesson_content_cache" (
    "lesson_id" text not null,
    "content" text not null,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
      );


alter table "public"."lesson_content_cache" enable row level security;


  create table "public"."lesson_list_cache" (
    "id" integer not null default 1,
    "lessons" jsonb not null,
    "total_count" integer not null,
    "updated_at" timestamp with time zone default now()
      );


alter table "public"."lesson_list_cache" enable row level security;


  create table "public"."lessons" (
    "id" text not null,
    "title" text not null,
    "description" text not null,
    "content" text not null default ''::text,
    "category" text not null,
    "difficulty" text not null,
    "duration_minutes" integer not null,
    "is_pro" boolean not null default false,
    "order" integer not null,
    "tags" text[] not null default '{}'::text[],
    "search_vector" tsvector,
    "created_at" timestamp with time zone default now(),
    "updated_at" timestamp with time zone default now()
      );


alter table "public"."lessons" enable row level security;


  create table "public"."photo_analyses" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "blob_url" text,
    "composition_score" integer,
    "vision_tags" text[],
    "created_at" timestamp with time zone default now(),
    "feedback" jsonb,
    "edit_suggestions" jsonb,
    "blob_path" text
      );


alter table "public"."photo_analyses" enable row level security;


  create table "public"."quiz_attempts" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "topic" text not null,
    "created_at" timestamp with time zone not null default now()
      );


alter table "public"."quiz_attempts" enable row level security;


  create table "public"."quiz_cache" (
    "id" uuid not null default gen_random_uuid(),
    "topic" text not null,
    "difficulty" text not null,
    "questions" jsonb not null,
    "hit_count" integer not null default 0,
    "created_at" timestamp with time zone not null default now(),
    "refreshed_at" timestamp with time zone not null default now()
      );


alter table "public"."quiz_cache" enable row level security;


  create table "public"."quiz_completions" (
    "id" uuid not null default gen_random_uuid(),
    "user_id" uuid not null,
    "created_at" timestamp with time zone default now()
      );


alter table "public"."quiz_completions" enable row level security;


  create table "public"."skill_profiles" (
    "user_id" uuid not null,
    "level" text default 'beginner'::text,
    "strengths" text[],
    "areas_to_improve" text[],
    "updated_at" timestamp with time zone default now()
      );


alter table "public"."skill_profiles" enable row level security;


  create table "public"."user_profiles" (
    "user_id" uuid not null,
    "display_name" text,
    "skill_level" text default 'beginner'::text,
    "photos_analyzed" integer default 0,
    "lessons_completed" integer default 0,
    "streak_days" integer default 0,
    "plan" text default 'free'::text,
    "updated_at" timestamp with time zone default now(),
    "last_active_date" date
      );


alter table "public"."user_profiles" enable row level security;

CREATE UNIQUE INDEX chat_history_pkey ON public.chat_history USING btree (id);

CREATE UNIQUE INDEX deleted_accounts_email_key ON public.deleted_accounts USING btree (email);

CREATE UNIQUE INDEX deleted_accounts_pkey ON public.deleted_accounts USING btree (id);

CREATE INDEX idx_chat_history_created_at ON public.chat_history USING btree (created_at);

CREATE INDEX idx_chat_history_session ON public.chat_history USING btree (user_id, session_id);

CREATE INDEX idx_chat_history_user_id ON public.chat_history USING btree (user_id);

CREATE INDEX idx_chat_history_user_session ON public.chat_history USING btree (user_id, session_id, created_at);

CREATE INDEX idx_lesson_completions_user ON public.lesson_completions USING btree (user_id);

CREATE INDEX idx_lesson_content_cache_lesson_id ON public.lesson_content_cache USING btree (lesson_id);

CREATE INDEX idx_lessons_category ON public.lessons USING btree (category);

CREATE INDEX idx_lessons_difficulty ON public.lessons USING btree (difficulty);

CREATE INDEX idx_lessons_is_pro ON public.lessons USING btree (is_pro);

CREATE INDEX idx_lessons_order ON public.lessons USING btree ("order");

CREATE INDEX idx_lessons_search ON public.lessons USING gin (search_vector);

CREATE INDEX idx_photo_analyses_created_at ON public.photo_analyses USING btree (created_at);

CREATE INDEX idx_photo_analyses_user_created ON public.photo_analyses USING btree (user_id, created_at);

CREATE INDEX idx_photo_analyses_user_id ON public.photo_analyses USING btree (user_id);

CREATE INDEX idx_quiz_attempts_created_at ON public.quiz_attempts USING btree (created_at);

CREATE INDEX idx_quiz_attempts_user_created ON public.quiz_attempts USING btree (user_id, created_at);

CREATE INDEX idx_quiz_cache_lookup ON public.quiz_cache USING btree (topic, difficulty);

CREATE INDEX idx_quiz_completions_created_at ON public.quiz_completions USING btree (created_at);

CREATE UNIQUE INDEX lesson_completions_pkey ON public.lesson_completions USING btree (id);

CREATE UNIQUE INDEX lesson_completions_user_id_lesson_id_key ON public.lesson_completions USING btree (user_id, lesson_id);

CREATE UNIQUE INDEX lesson_content_cache_pkey ON public.lesson_content_cache USING btree (lesson_id);

CREATE UNIQUE INDEX lesson_list_cache_pkey ON public.lesson_list_cache USING btree (id);

CREATE UNIQUE INDEX lessons_pkey ON public.lessons USING btree (id);

CREATE UNIQUE INDEX photo_analyses_pkey ON public.photo_analyses USING btree (id);

CREATE UNIQUE INDEX quiz_attempts_pkey ON public.quiz_attempts USING btree (id);

CREATE UNIQUE INDEX quiz_cache_pkey ON public.quiz_cache USING btree (id);

CREATE UNIQUE INDEX quiz_cache_topic_difficulty_key ON public.quiz_cache USING btree (topic, difficulty);

CREATE UNIQUE INDEX quiz_completions_pkey ON public.quiz_completions USING btree (id);

CREATE UNIQUE INDEX skill_profiles_pkey ON public.skill_profiles USING btree (user_id);

CREATE UNIQUE INDEX user_profiles_pkey ON public.user_profiles USING btree (user_id);

alter table "public"."chat_history" add constraint "chat_history_pkey" PRIMARY KEY using index "chat_history_pkey";

alter table "public"."deleted_accounts" add constraint "deleted_accounts_pkey" PRIMARY KEY using index "deleted_accounts_pkey";

alter table "public"."lesson_completions" add constraint "lesson_completions_pkey" PRIMARY KEY using index "lesson_completions_pkey";

alter table "public"."lesson_content_cache" add constraint "lesson_content_cache_pkey" PRIMARY KEY using index "lesson_content_cache_pkey";

alter table "public"."lesson_list_cache" add constraint "lesson_list_cache_pkey" PRIMARY KEY using index "lesson_list_cache_pkey";

alter table "public"."lessons" add constraint "lessons_pkey" PRIMARY KEY using index "lessons_pkey";

alter table "public"."photo_analyses" add constraint "photo_analyses_pkey" PRIMARY KEY using index "photo_analyses_pkey";

alter table "public"."quiz_attempts" add constraint "quiz_attempts_pkey" PRIMARY KEY using index "quiz_attempts_pkey";

alter table "public"."quiz_cache" add constraint "quiz_cache_pkey" PRIMARY KEY using index "quiz_cache_pkey";

alter table "public"."quiz_completions" add constraint "quiz_completions_pkey" PRIMARY KEY using index "quiz_completions_pkey";

alter table "public"."skill_profiles" add constraint "skill_profiles_pkey" PRIMARY KEY using index "skill_profiles_pkey";

alter table "public"."user_profiles" add constraint "user_profiles_pkey" PRIMARY KEY using index "user_profiles_pkey";

alter table "public"."chat_history" add constraint "chat_history_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."chat_history" validate constraint "chat_history_user_id_fkey";

alter table "public"."deleted_accounts" add constraint "deleted_accounts_email_key" UNIQUE using index "deleted_accounts_email_key";

alter table "public"."lesson_completions" add constraint "lesson_completions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."lesson_completions" validate constraint "lesson_completions_user_id_fkey";

alter table "public"."lesson_completions" add constraint "lesson_completions_user_id_lesson_id_key" UNIQUE using index "lesson_completions_user_id_lesson_id_key";

alter table "public"."lesson_list_cache" add constraint "single_row" CHECK ((id = 1)) not valid;

alter table "public"."lesson_list_cache" validate constraint "single_row";

alter table "public"."lessons" add constraint "lessons_difficulty_check" CHECK ((difficulty = ANY (ARRAY['beginner'::text, 'intermediate'::text, 'advanced'::text]))) not valid;

alter table "public"."lessons" validate constraint "lessons_difficulty_check";

alter table "public"."photo_analyses" add constraint "photo_analyses_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."photo_analyses" validate constraint "photo_analyses_user_id_fkey";

alter table "public"."quiz_attempts" add constraint "quiz_attempts_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."quiz_attempts" validate constraint "quiz_attempts_user_id_fkey";

alter table "public"."quiz_cache" add constraint "quiz_cache_topic_difficulty_key" UNIQUE using index "quiz_cache_topic_difficulty_key";

alter table "public"."quiz_completions" add constraint "quiz_completions_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."quiz_completions" validate constraint "quiz_completions_user_id_fkey";

alter table "public"."skill_profiles" add constraint "skill_profiles_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."skill_profiles" validate constraint "skill_profiles_user_id_fkey";

alter table "public"."user_profiles" add constraint "user_profiles_user_id_fkey" FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE not valid;

alter table "public"."user_profiles" validate constraint "user_profiles_user_id_fkey";

set check_function_bodies = off;

CREATE OR REPLACE FUNCTION public.increment_photos_analyzed(p_user_id text)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
BEGIN
    INSERT INTO user_profiles (user_id, photos_analyzed)
    VALUES (p_user_id, 1)
    ON CONFLICT (user_id)
    DO UPDATE SET photos_analyzed = user_profiles.photos_analyzed + 1;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.increment_quiz_cache_hits(p_topic text, p_difficulty text)
 RETURNS void
 LANGUAGE plpgsql
AS $function$
BEGIN
    UPDATE quiz_cache
    SET hit_count = hit_count + 1
    WHERE topic = p_topic AND difficulty = p_difficulty;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.lessons_search_vector_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(array_to_string(NEW.tags, ' '), '')), 'C');
    RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.mark_lesson_complete(p_user_id uuid, p_lesson_id text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    INSERT INTO lesson_completions (user_id, lesson_id)
    VALUES (p_user_id, p_lesson_id)
    ON CONFLICT (user_id, lesson_id) DO NOTHING;

    IF FOUND THEN
        UPDATE user_profiles
        SET lessons_completed = lessons_completed + 1
        WHERE user_id = p_user_id;
    END IF;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.purge_old_analyses()
 RETURNS void
 LANGUAGE plpgsql
AS $function$
BEGIN
  DELETE FROM photo_analyses
  WHERE created_at < NOW() - INTERVAL '30 days';
END;
$function$
;

CREATE OR REPLACE FUNCTION public.purge_old_chat_history()
 RETURNS void
 LANGUAGE plpgsql
AS $function$
BEGIN
  DELETE FROM chat_history
  WHERE created_at < NOW() - INTERVAL '10 days';
END;
$function$
;

grant delete on table "public"."chat_history" to "anon";

grant insert on table "public"."chat_history" to "anon";

grant references on table "public"."chat_history" to "anon";

grant select on table "public"."chat_history" to "anon";

grant trigger on table "public"."chat_history" to "anon";

grant truncate on table "public"."chat_history" to "anon";

grant update on table "public"."chat_history" to "anon";

grant delete on table "public"."chat_history" to "authenticated";

grant insert on table "public"."chat_history" to "authenticated";

grant references on table "public"."chat_history" to "authenticated";

grant select on table "public"."chat_history" to "authenticated";

grant trigger on table "public"."chat_history" to "authenticated";

grant truncate on table "public"."chat_history" to "authenticated";

grant update on table "public"."chat_history" to "authenticated";

grant delete on table "public"."chat_history" to "service_role";

grant insert on table "public"."chat_history" to "service_role";

grant references on table "public"."chat_history" to "service_role";

grant select on table "public"."chat_history" to "service_role";

grant trigger on table "public"."chat_history" to "service_role";

grant truncate on table "public"."chat_history" to "service_role";

grant update on table "public"."chat_history" to "service_role";

grant delete on table "public"."deleted_accounts" to "anon";

grant insert on table "public"."deleted_accounts" to "anon";

grant references on table "public"."deleted_accounts" to "anon";

grant select on table "public"."deleted_accounts" to "anon";

grant trigger on table "public"."deleted_accounts" to "anon";

grant truncate on table "public"."deleted_accounts" to "anon";

grant update on table "public"."deleted_accounts" to "anon";

grant delete on table "public"."deleted_accounts" to "authenticated";

grant insert on table "public"."deleted_accounts" to "authenticated";

grant references on table "public"."deleted_accounts" to "authenticated";

grant select on table "public"."deleted_accounts" to "authenticated";

grant trigger on table "public"."deleted_accounts" to "authenticated";

grant truncate on table "public"."deleted_accounts" to "authenticated";

grant update on table "public"."deleted_accounts" to "authenticated";

grant delete on table "public"."deleted_accounts" to "service_role";

grant insert on table "public"."deleted_accounts" to "service_role";

grant references on table "public"."deleted_accounts" to "service_role";

grant select on table "public"."deleted_accounts" to "service_role";

grant trigger on table "public"."deleted_accounts" to "service_role";

grant truncate on table "public"."deleted_accounts" to "service_role";

grant update on table "public"."deleted_accounts" to "service_role";

grant delete on table "public"."lesson_completions" to "anon";

grant insert on table "public"."lesson_completions" to "anon";

grant references on table "public"."lesson_completions" to "anon";

grant select on table "public"."lesson_completions" to "anon";

grant trigger on table "public"."lesson_completions" to "anon";

grant truncate on table "public"."lesson_completions" to "anon";

grant update on table "public"."lesson_completions" to "anon";

grant delete on table "public"."lesson_completions" to "authenticated";

grant insert on table "public"."lesson_completions" to "authenticated";

grant references on table "public"."lesson_completions" to "authenticated";

grant select on table "public"."lesson_completions" to "authenticated";

grant trigger on table "public"."lesson_completions" to "authenticated";

grant truncate on table "public"."lesson_completions" to "authenticated";

grant update on table "public"."lesson_completions" to "authenticated";

grant delete on table "public"."lesson_completions" to "service_role";

grant insert on table "public"."lesson_completions" to "service_role";

grant references on table "public"."lesson_completions" to "service_role";

grant select on table "public"."lesson_completions" to "service_role";

grant trigger on table "public"."lesson_completions" to "service_role";

grant truncate on table "public"."lesson_completions" to "service_role";

grant update on table "public"."lesson_completions" to "service_role";

grant delete on table "public"."lesson_content_cache" to "anon";

grant insert on table "public"."lesson_content_cache" to "anon";

grant references on table "public"."lesson_content_cache" to "anon";

grant select on table "public"."lesson_content_cache" to "anon";

grant trigger on table "public"."lesson_content_cache" to "anon";

grant truncate on table "public"."lesson_content_cache" to "anon";

grant update on table "public"."lesson_content_cache" to "anon";

grant delete on table "public"."lesson_content_cache" to "authenticated";

grant insert on table "public"."lesson_content_cache" to "authenticated";

grant references on table "public"."lesson_content_cache" to "authenticated";

grant select on table "public"."lesson_content_cache" to "authenticated";

grant trigger on table "public"."lesson_content_cache" to "authenticated";

grant truncate on table "public"."lesson_content_cache" to "authenticated";

grant update on table "public"."lesson_content_cache" to "authenticated";

grant delete on table "public"."lesson_content_cache" to "service_role";

grant insert on table "public"."lesson_content_cache" to "service_role";

grant references on table "public"."lesson_content_cache" to "service_role";

grant select on table "public"."lesson_content_cache" to "service_role";

grant trigger on table "public"."lesson_content_cache" to "service_role";

grant truncate on table "public"."lesson_content_cache" to "service_role";

grant update on table "public"."lesson_content_cache" to "service_role";

grant delete on table "public"."lesson_list_cache" to "anon";

grant insert on table "public"."lesson_list_cache" to "anon";

grant references on table "public"."lesson_list_cache" to "anon";

grant select on table "public"."lesson_list_cache" to "anon";

grant trigger on table "public"."lesson_list_cache" to "anon";

grant truncate on table "public"."lesson_list_cache" to "anon";

grant update on table "public"."lesson_list_cache" to "anon";

grant delete on table "public"."lesson_list_cache" to "authenticated";

grant insert on table "public"."lesson_list_cache" to "authenticated";

grant references on table "public"."lesson_list_cache" to "authenticated";

grant select on table "public"."lesson_list_cache" to "authenticated";

grant trigger on table "public"."lesson_list_cache" to "authenticated";

grant truncate on table "public"."lesson_list_cache" to "authenticated";

grant update on table "public"."lesson_list_cache" to "authenticated";

grant delete on table "public"."lesson_list_cache" to "service_role";

grant insert on table "public"."lesson_list_cache" to "service_role";

grant references on table "public"."lesson_list_cache" to "service_role";

grant select on table "public"."lesson_list_cache" to "service_role";

grant trigger on table "public"."lesson_list_cache" to "service_role";

grant truncate on table "public"."lesson_list_cache" to "service_role";

grant update on table "public"."lesson_list_cache" to "service_role";

grant delete on table "public"."lessons" to "anon";

grant insert on table "public"."lessons" to "anon";

grant references on table "public"."lessons" to "anon";

grant select on table "public"."lessons" to "anon";

grant trigger on table "public"."lessons" to "anon";

grant truncate on table "public"."lessons" to "anon";

grant update on table "public"."lessons" to "anon";

grant delete on table "public"."lessons" to "authenticated";

grant insert on table "public"."lessons" to "authenticated";

grant references on table "public"."lessons" to "authenticated";

grant select on table "public"."lessons" to "authenticated";

grant trigger on table "public"."lessons" to "authenticated";

grant truncate on table "public"."lessons" to "authenticated";

grant update on table "public"."lessons" to "authenticated";

grant delete on table "public"."lessons" to "service_role";

grant insert on table "public"."lessons" to "service_role";

grant references on table "public"."lessons" to "service_role";

grant select on table "public"."lessons" to "service_role";

grant trigger on table "public"."lessons" to "service_role";

grant truncate on table "public"."lessons" to "service_role";

grant update on table "public"."lessons" to "service_role";

grant delete on table "public"."photo_analyses" to "anon";

grant insert on table "public"."photo_analyses" to "anon";

grant references on table "public"."photo_analyses" to "anon";

grant select on table "public"."photo_analyses" to "anon";

grant trigger on table "public"."photo_analyses" to "anon";

grant truncate on table "public"."photo_analyses" to "anon";

grant update on table "public"."photo_analyses" to "anon";

grant delete on table "public"."photo_analyses" to "authenticated";

grant insert on table "public"."photo_analyses" to "authenticated";

grant references on table "public"."photo_analyses" to "authenticated";

grant select on table "public"."photo_analyses" to "authenticated";

grant trigger on table "public"."photo_analyses" to "authenticated";

grant truncate on table "public"."photo_analyses" to "authenticated";

grant update on table "public"."photo_analyses" to "authenticated";

grant delete on table "public"."photo_analyses" to "service_role";

grant insert on table "public"."photo_analyses" to "service_role";

grant references on table "public"."photo_analyses" to "service_role";

grant select on table "public"."photo_analyses" to "service_role";

grant trigger on table "public"."photo_analyses" to "service_role";

grant truncate on table "public"."photo_analyses" to "service_role";

grant update on table "public"."photo_analyses" to "service_role";

grant delete on table "public"."quiz_attempts" to "anon";

grant insert on table "public"."quiz_attempts" to "anon";

grant references on table "public"."quiz_attempts" to "anon";

grant select on table "public"."quiz_attempts" to "anon";

grant trigger on table "public"."quiz_attempts" to "anon";

grant truncate on table "public"."quiz_attempts" to "anon";

grant update on table "public"."quiz_attempts" to "anon";

grant delete on table "public"."quiz_attempts" to "authenticated";

grant insert on table "public"."quiz_attempts" to "authenticated";

grant references on table "public"."quiz_attempts" to "authenticated";

grant select on table "public"."quiz_attempts" to "authenticated";

grant trigger on table "public"."quiz_attempts" to "authenticated";

grant truncate on table "public"."quiz_attempts" to "authenticated";

grant update on table "public"."quiz_attempts" to "authenticated";

grant delete on table "public"."quiz_attempts" to "service_role";

grant insert on table "public"."quiz_attempts" to "service_role";

grant references on table "public"."quiz_attempts" to "service_role";

grant select on table "public"."quiz_attempts" to "service_role";

grant trigger on table "public"."quiz_attempts" to "service_role";

grant truncate on table "public"."quiz_attempts" to "service_role";

grant update on table "public"."quiz_attempts" to "service_role";

grant delete on table "public"."quiz_cache" to "anon";

grant insert on table "public"."quiz_cache" to "anon";

grant references on table "public"."quiz_cache" to "anon";

grant select on table "public"."quiz_cache" to "anon";

grant trigger on table "public"."quiz_cache" to "anon";

grant truncate on table "public"."quiz_cache" to "anon";

grant update on table "public"."quiz_cache" to "anon";

grant delete on table "public"."quiz_cache" to "authenticated";

grant insert on table "public"."quiz_cache" to "authenticated";

grant references on table "public"."quiz_cache" to "authenticated";

grant select on table "public"."quiz_cache" to "authenticated";

grant trigger on table "public"."quiz_cache" to "authenticated";

grant truncate on table "public"."quiz_cache" to "authenticated";

grant update on table "public"."quiz_cache" to "authenticated";

grant delete on table "public"."quiz_cache" to "service_role";

grant insert on table "public"."quiz_cache" to "service_role";

grant references on table "public"."quiz_cache" to "service_role";

grant select on table "public"."quiz_cache" to "service_role";

grant trigger on table "public"."quiz_cache" to "service_role";

grant truncate on table "public"."quiz_cache" to "service_role";

grant update on table "public"."quiz_cache" to "service_role";

grant delete on table "public"."quiz_completions" to "anon";

grant insert on table "public"."quiz_completions" to "anon";

grant references on table "public"."quiz_completions" to "anon";

grant select on table "public"."quiz_completions" to "anon";

grant trigger on table "public"."quiz_completions" to "anon";

grant truncate on table "public"."quiz_completions" to "anon";

grant update on table "public"."quiz_completions" to "anon";

grant delete on table "public"."quiz_completions" to "authenticated";

grant insert on table "public"."quiz_completions" to "authenticated";

grant references on table "public"."quiz_completions" to "authenticated";

grant select on table "public"."quiz_completions" to "authenticated";

grant trigger on table "public"."quiz_completions" to "authenticated";

grant truncate on table "public"."quiz_completions" to "authenticated";

grant update on table "public"."quiz_completions" to "authenticated";

grant delete on table "public"."quiz_completions" to "service_role";

grant insert on table "public"."quiz_completions" to "service_role";

grant references on table "public"."quiz_completions" to "service_role";

grant select on table "public"."quiz_completions" to "service_role";

grant trigger on table "public"."quiz_completions" to "service_role";

grant truncate on table "public"."quiz_completions" to "service_role";

grant update on table "public"."quiz_completions" to "service_role";

grant delete on table "public"."skill_profiles" to "anon";

grant insert on table "public"."skill_profiles" to "anon";

grant references on table "public"."skill_profiles" to "anon";

grant select on table "public"."skill_profiles" to "anon";

grant trigger on table "public"."skill_profiles" to "anon";

grant truncate on table "public"."skill_profiles" to "anon";

grant update on table "public"."skill_profiles" to "anon";

grant delete on table "public"."skill_profiles" to "authenticated";

grant insert on table "public"."skill_profiles" to "authenticated";

grant references on table "public"."skill_profiles" to "authenticated";

grant select on table "public"."skill_profiles" to "authenticated";

grant trigger on table "public"."skill_profiles" to "authenticated";

grant truncate on table "public"."skill_profiles" to "authenticated";

grant update on table "public"."skill_profiles" to "authenticated";

grant delete on table "public"."skill_profiles" to "service_role";

grant insert on table "public"."skill_profiles" to "service_role";

grant references on table "public"."skill_profiles" to "service_role";

grant select on table "public"."skill_profiles" to "service_role";

grant trigger on table "public"."skill_profiles" to "service_role";

grant truncate on table "public"."skill_profiles" to "service_role";

grant update on table "public"."skill_profiles" to "service_role";

grant delete on table "public"."user_profiles" to "anon";

grant insert on table "public"."user_profiles" to "anon";

grant references on table "public"."user_profiles" to "anon";

grant select on table "public"."user_profiles" to "anon";

grant trigger on table "public"."user_profiles" to "anon";

grant truncate on table "public"."user_profiles" to "anon";

grant update on table "public"."user_profiles" to "anon";

grant delete on table "public"."user_profiles" to "authenticated";

grant insert on table "public"."user_profiles" to "authenticated";

grant references on table "public"."user_profiles" to "authenticated";

grant select on table "public"."user_profiles" to "authenticated";

grant trigger on table "public"."user_profiles" to "authenticated";

grant truncate on table "public"."user_profiles" to "authenticated";

grant update on table "public"."user_profiles" to "authenticated";

grant delete on table "public"."user_profiles" to "service_role";

grant insert on table "public"."user_profiles" to "service_role";

grant references on table "public"."user_profiles" to "service_role";

grant select on table "public"."user_profiles" to "service_role";

grant trigger on table "public"."user_profiles" to "service_role";

grant truncate on table "public"."user_profiles" to "service_role";

grant update on table "public"."user_profiles" to "service_role";


  create policy "Users can access own chat history"
  on "public"."chat_history"
  as permissive
  for all
  to public
using ((auth.uid() = user_id));



  create policy "Service role only"
  on "public"."deleted_accounts"
  as permissive
  for all
  to public
using (false);



  create policy "Users can insert own completions"
  on "public"."lesson_completions"
  as permissive
  for insert
  to public
with check ((auth.uid() = user_id));



  create policy "Users read own completions"
  on "public"."lesson_completions"
  as permissive
  for select
  to public
using ((auth.uid() = user_id));



  create policy "Users can access own analyses"
  on "public"."photo_analyses"
  as permissive
  for all
  to public
using ((auth.uid() = user_id));



  create policy "Service role can insert quiz attempts"
  on "public"."quiz_attempts"
  as permissive
  for insert
  to public
with check (true);



  create policy "Users can view own quiz attempts"
  on "public"."quiz_attempts"
  as permissive
  for select
  to public
using ((auth.uid() = user_id));



  create policy "Anyone can read quiz cache"
  on "public"."quiz_cache"
  as permissive
  for select
  to public
using (true);



  create policy "Service role can manage quiz cache"
  on "public"."quiz_cache"
  as permissive
  for all
  to public
with check (true);



  create policy "Service role can insert quiz completions"
  on "public"."quiz_completions"
  as permissive
  for insert
  to public
with check (true);



  create policy "Users can view own quiz completions"
  on "public"."quiz_completions"
  as permissive
  for select
  to public
using ((auth.uid() = user_id));



  create policy "Users can access own skill profile"
  on "public"."skill_profiles"
  as permissive
  for all
  to public
using ((auth.uid() = user_id));



  create policy "Users can access own profile"
  on "public"."user_profiles"
  as permissive
  for all
  to public
using ((auth.uid() = user_id));



  create policy "Users can insert own profile"
  on "public"."user_profiles"
  as permissive
  for insert
  to public
with check ((auth.uid() = user_id));



  create policy "Users can update own profile"
  on "public"."user_profiles"
  as permissive
  for update
  to public
using ((auth.uid() = user_id));


CREATE TRIGGER lessons_search_vector_trigger BEFORE INSERT OR UPDATE ON public.lessons FOR EACH ROW EXECUTE FUNCTION public.lessons_search_vector_update();


create or replace function public.check_deleted_account(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  signup_email text;
begin
  signup_email := lower(trim(event->'user'->>'email'));

  if signup_email is null or signup_email = '' then
    return '{}'::jsonb;  -- no email to check, allow
  end if;

  if exists (
    select 1 from public.deleted_accounts
    where lower(trim(email)) = signup_email
  ) then
    return jsonb_build_object(
      'error', jsonb_build_object(
        'http_code', 422,
        'message', 'This email address is not eligible for registration.'
      )
    );
  end if;

  return '{}'::jsonb;
exception
  when others then
    -- fail open, matching the original endpoint's behaviour:
    -- don't block legitimate signups due to our own errors
    return '{}'::jsonb;
end;
$$;

grant execute on function public.check_deleted_account to supabase_auth_admin;
revoke execute on function public.check_deleted_account from authenticated, anon, public;

drop policy "Service role can insert quiz attempts" on public.quiz_attempts;
drop policy "Service role can insert quiz completions" on public.quiz_completions;
drop policy "Service role can manage quiz cache" on public.quiz_cache;