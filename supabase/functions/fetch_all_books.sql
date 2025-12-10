create or replace function memor_chess.fetch_all_books()
    returns jsonb
    language plpgsql
    set search_path to memor_chess
as
$$
declare
    result jsonb := '[]'::jsonb;
begin
    select coalesce(jsonb_agg(jsonb_build_object(
            'id', b.id,
            'name', b.name,
            'created_at', b.created_at
                              )), '[]'::jsonb)
    into result
    from book b;

    return result;
end;
$$;
