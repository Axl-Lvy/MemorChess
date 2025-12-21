create or replace function memor_chess.register_book_download(
    book_id_input bigint
)
    returns boolean
    language plpgsql
    SECURITY DEFINER
    set search_path to memor_chess
as
$$
declare
    current_user_id uuid;
    download_exists boolean;
begin
    -- Get current user ID
    current_user_id := auth.uid();

    if current_user_id is null then
        raise exception 'User must be authenticated';
    end if;

    -- Check if a download record already exists
    select exists(
        select 1
        from downloaded_books
        where user_id = current_user_id
          and book_id = book_id_input
    ) into download_exists;

    -- If already downloaded, do nothing
    if download_exists then
        return false;
    end if;

    -- Create the download record
    insert into downloaded_books(user_id, book_id)
    values (current_user_id, book_id_input)
    ON CONFLICT DO NOTHING;

    -- Increment the downloaded counter on the book
    update book
    set downloads = downloads + 1
    where id = book_id_input;

    return true;
end;
$$;
