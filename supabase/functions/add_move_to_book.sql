create or replace function memor_chess.add_move_to_book(
    user_id_input uuid,
    book_id_input bigint,
    origin_input text,
    destination_input text,
    move_input text,
    is_good_input boolean
)
    returns boolean
    language plpgsql
    set search_path to memor_chess
    SECURITY DEFINER
as
$$
declare
    has_permission      boolean;
    origin_position_id  bigint;
    dest_position_id    bigint;
    move_id_input       bigint;
begin
    -- Check if user has BOOK_CREATION permission
    select check_user_permission(user_id_input, 'BOOK_CREATION')
    into has_permission;

    if not has_permission then
        raise exception 'User does not have BOOK_CREATION permission';
    end if;

    -- Ensure origin position exists
    insert into positions(fen_representation)
    values (origin_input)
    on conflict(fen_representation) do nothing;

    select id into origin_position_id
    from positions
    where fen_representation = origin_input;

    -- Ensure destination position exists
    insert into positions(fen_representation)
    values (destination_input)
    on conflict(fen_representation) do nothing;

    select id into dest_position_id
    from positions
    where fen_representation = destination_input;

    -- Ensure move exists
    insert into moves(origin, destination, name)
    values (origin_position_id, dest_position_id, move_input)
    on conflict(origin, destination, name) do nothing;

    select id into move_id_input
    from moves
    where origin = origin_position_id
      and destination = dest_position_id;

    -- Link move to book
    insert into move_cross_book(move_id, book_id, is_good)
    values (move_id_input, book_id_input, is_good_input)
    on conflict(move_id, book_id)
        do update set is_good = is_good_input;

    return true;
end;
$$;
