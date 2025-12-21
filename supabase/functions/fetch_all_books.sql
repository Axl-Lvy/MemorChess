create or replace function memor_chess.fetch_all_books(offset_input int default 0, limit_input int default 50)
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
    from (
        select b.id, b.name, b.created_at
        from book b
        order by b.created_at desc, b.id desc
        limit limit_input
        offset offset_input
    ) b;

    return result;
end;
$$;
