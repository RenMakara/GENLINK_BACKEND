--liquibase formatted sql

--changeset genlink:0081-secure-community-media-attachments
ALTER TABLE media_files
    ADD COLUMN attached_resource_type VARCHAR(20),
    ADD COLUMN attached_resource_id UUID,
    ADD COLUMN attachment_role VARCHAR(20),
    ADD COLUMN attachment_order INTEGER;

ALTER TABLE media_files
    ADD CONSTRAINT chk_media_attachment_fields
    CHECK (
        (attached_resource_type IS NULL AND attached_resource_id IS NULL
            AND attachment_role IS NULL AND attachment_order IS NULL)
        OR
        (attached_resource_type IS NOT NULL AND attached_resource_id IS NOT NULL
            AND attachment_role IS NOT NULL AND attachment_order IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_media_attachment_slot
    ON media_files (attached_resource_type, attached_resource_id, attachment_role, attachment_order)
    WHERE attached_resource_id IS NOT NULL;

CREATE INDEX idx_media_attachment_resource
    ON media_files (attached_resource_type, attached_resource_id, attachment_role, attachment_order)
    WHERE deleted_at IS NULL AND attached_resource_id IS NOT NULL;

-- Link unambiguous existing uploads to their owning content. Private content is
-- switched to PRIVATE in the metadata; the media startup reconciler removes any
-- provider ACL and clears the old reusable URL before the application serves it.
WITH attachment_candidates AS (
    SELECT m.id AS media_id, p.post_id AS resource_id, 'POST' AS resource_type,
           'IMAGE' AS role, (u.ordinality - 1)::INTEGER AS attachment_order,
           p.visibility AS content_visibility
    FROM media_files m
    JOIN posts p ON m.public_url IS NOT NULL
    CROSS JOIN LATERAL unnest(string_to_array(p.image_url, ',')) WITH ORDINALITY AS u(url, ordinality)
    WHERE m.purpose = 'POST' AND trim(u.url) = m.public_url
    UNION ALL
    SELECT m.id, p.post_id, 'POST', 'VIDEO', 0, p.visibility
    FROM media_files m
    JOIN posts p ON m.public_url IS NOT NULL AND trim(p.video_url) = m.public_url
    WHERE m.purpose = 'POST'
    UNION ALL
    SELECT m.id, s.story_id, 'STORY', 'STORY', 0, s.visibility
    FROM media_files m
    JOIN stories s ON m.public_url IS NOT NULL AND trim(s.media_url) = m.public_url
    WHERE m.purpose = 'STORY'
), unambiguous AS (
    SELECT media_id, min(resource_id::TEXT)::UUID AS resource_id,
           min(resource_type) AS resource_type,
           min(role) AS role,
           min(attachment_order) AS attachment_order,
           min(content_visibility) AS content_visibility
    FROM attachment_candidates
    GROUP BY media_id
    HAVING count(*) = 1
)
UPDATE media_files m
SET attached_resource_type = u.resource_type,
    attached_resource_id = u.resource_id,
    attachment_role = u.role,
    attachment_order = u.attachment_order,
    visibility = CASE WHEN u.content_visibility = 'PUBLIC' THEN 'PUBLIC' ELSE 'PRIVATE' END
FROM unambiguous u
WHERE m.id = u.media_id;

-- An upload reused by more than one content row cannot fit the one-resource
-- attachment model. Still revoke its public access if any reference is restricted;
-- restricted API responses will hide its legacy URL until it is re-uploaded.
WITH restricted_references AS (
    SELECT DISTINCT m.id AS media_id
    FROM media_files m
    JOIN posts p ON m.public_url IS NOT NULL AND p.visibility <> 'PUBLIC'
    CROSS JOIN LATERAL unnest(string_to_array(p.image_url, ',')) AS u(url)
    WHERE m.purpose = 'POST' AND trim(u.url) = m.public_url
    UNION
    SELECT DISTINCT m.id
    FROM media_files m
    JOIN posts p ON m.public_url IS NOT NULL AND p.visibility <> 'PUBLIC'
        AND trim(p.video_url) = m.public_url
    WHERE m.purpose = 'POST'
    UNION
    SELECT DISTINCT m.id
    FROM media_files m
    JOIN stories s ON m.public_url IS NOT NULL AND s.visibility <> 'PUBLIC'
        AND trim(s.media_url) = m.public_url
    WHERE m.purpose = 'STORY'
)
UPDATE media_files m
SET visibility = 'PRIVATE'
FROM restricted_references r
WHERE m.id = r.media_id;

--rollback DROP INDEX IF EXISTS idx_media_attachment_resource;
--rollback DROP INDEX IF EXISTS uq_media_attachment_slot;
--rollback ALTER TABLE media_files DROP CONSTRAINT IF EXISTS chk_media_attachment_fields;
--rollback ALTER TABLE media_files DROP COLUMN IF EXISTS attachment_order;
--rollback ALTER TABLE media_files DROP COLUMN IF EXISTS attachment_role;
--rollback ALTER TABLE media_files DROP COLUMN IF EXISTS attached_resource_id;
--rollback ALTER TABLE media_files DROP COLUMN IF EXISTS attached_resource_type;
