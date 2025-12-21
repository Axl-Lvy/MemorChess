create or replace function memor_chess.fetch_book_moves(
    book_id_input bigint
)
    returns jsonb
    language plpgsql
    set search_path to memor_chess
as
$$
declare
    result jsonb := '[]'::jsonb;
begin
    select coalesce(jsonb_agg(jsonb_build_object(
            'origin', po.fen_representation,
            'destination', pd.fen_representation,
            'move', m.name,
            'isGood', mcb.is_good
                              )), '[]'::jsonb)
    into result
    from move_cross_book mcb
             join moves m on mcb.move_id = m.id
             join positions po on m.origin = po.id
             join positions pd on m.destination = pd.id
    where mcb.book_id = book_id_input;

    return result;
end;
$$;
