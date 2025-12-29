ALTER TABLE user_positions ADD CONSTRAINT fk_user_positions_user_id__id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
