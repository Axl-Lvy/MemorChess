ALTER TABLE user_moves ADD CONSTRAINT fk_user_moves_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE downloaded_books ADD CONSTRAINT fk_downloaded_books_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE user_permissions ADD CONSTRAINT fk_user_permissions_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
