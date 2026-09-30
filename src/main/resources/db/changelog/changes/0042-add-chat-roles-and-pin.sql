-- Adds group roles and one pinned message per room.

-- Member role. OWNER (creator), ADMIN (manages members / rename / pin), MEMBER (default).
ALTER TABLE chat_room_members ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'MEMBER';

-- Backfill: whoever created a room is its OWNER. EXISTS form (no UPDATE ... FROM / table alias)
-- so it stays portable.
UPDATE chat_room_members
SET role = 'OWNER'
WHERE EXISTS (
    SELECT 1 FROM chat_rooms r
    WHERE r.chat_room_id = chat_room_members.chat_room_id
      AND r.created_by_user_id = chat_room_members.user_id
);

-- One pinned message per room.
ALTER TABLE chat_rooms ADD COLUMN pinned_message_id UUID;
ALTER TABLE chat_rooms ADD CONSTRAINT fk_chat_rooms_pinned_message_id
    FOREIGN KEY (pinned_message_id) REFERENCES chat_messages(message_id);
