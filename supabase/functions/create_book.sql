create or replace function memor_chess.create_book(
    user_id_input uuid,
    book_name_input text
)
    returns bigint
    language plpgsql
    SECURITY DEFINER
    set search_path to memor_chess
as
$$
declare
    has_permission boolean;
    new_book_id    bigint;
begin
    -- Check if user has BOOK_CREATION permission
    select check_user_permission(user_id_input, 'BOOK_CREATION')
    into has_permission;

    if not has_permission then
        raise exception 'User does not have BOOK_CREATION permission';
    end if;

    -- Create the book
    insert into book(name, created_at)
    values (book_name_input, now())
    returning id into new_book_id;

    return new_book_id;
end;
$$;
