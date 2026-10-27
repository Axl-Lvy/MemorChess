-- One global sequence drives the pull cursor for every table. A user therefore sees large gaps
-- in their own revision numbers, which is harmless: the cursor only has to be monotonic and
-- comparable, never dense.
CREATE SEQUENCE IF NOT EXISTS sync_revision AS bigint;

-- Global, append only, never garbage collected. Deleting a line deletes the user's row, never
-- the shared position or edge: refcounting a shared row would put every write in contention on
-- a counter, and the rows are far too small to reclaim.
CREATE TABLE IF NOT EXISTS position (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  position_key text NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS move_edge (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  origin_id bigint NOT NULL REFERENCES position(id),
  destination_id bigint NOT NULL REFERENCES position(id),
  move text NOT NULL,
  UNIQUE (origin_id, destination_id)
);

-- Per user. Holds only what is genuinely personal: scheduling state on a node, isGood on an
-- edge. The cropped FEN lives once in position, referenced by an 8 byte id.
CREATE TABLE IF NOT EXISTS user_node (
  user_id text NOT NULL,
  position_id bigint NOT NULL REFERENCES position(id),
  due_date timestamptz NOT NULL,
  last_review timestamptz,
  first_review timestamptz,
  stability double precision NOT NULL,
  difficulty double precision NOT NULL,
  reps int NOT NULL,
  lapses int NOT NULL,
  phase text NOT NULL,
  step int NOT NULL,
  is_deleted boolean NOT NULL,
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL,
  origin_device text NOT NULL,
  device_seq bigint NOT NULL,
  revision bigint NOT NULL,
  PRIMARY KEY (user_id, position_id)
);

CREATE TABLE IF NOT EXISTS user_edge (
  user_id text NOT NULL,
  edge_id bigint NOT NULL REFERENCES move_edge(id),
  is_good boolean NOT NULL,
  is_deleted boolean NOT NULL,
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL,
  origin_device text NOT NULL,
  device_seq bigint NOT NULL,
  revision bigint NOT NULL,
  PRIMARY KEY (user_id, edge_id)
);

CREATE TABLE IF NOT EXISTS user_setting (
  user_id text NOT NULL,
  key text NOT NULL,
  value text NOT NULL,
  is_deleted boolean NOT NULL,
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL,
  origin_device text NOT NULL,
  device_seq bigint NOT NULL,
  revision bigint NOT NULL,
  PRIMARY KEY (user_id, key)
);

CREATE TABLE IF NOT EXISTS user_repertoire (
  user_id text NOT NULL,
  repertoire_id text NOT NULL,
  name text NOT NULL,
  color text,
  is_deleted boolean NOT NULL,
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL,
  origin_device text NOT NULL,
  device_seq bigint NOT NULL,
  revision bigint NOT NULL,
  PRIMARY KEY (user_id, repertoire_id)
);

-- The only multi row read path is "everything for this user above this revision".
CREATE INDEX IF NOT EXISTS user_node_cursor ON user_node (user_id, revision);
CREATE INDEX IF NOT EXISTS user_edge_cursor ON user_edge (user_id, revision);
CREATE INDEX IF NOT EXISTS user_setting_cursor ON user_setting (user_id, revision);
CREATE INDEX IF NOT EXISTS user_repertoire_cursor ON user_repertoire (user_id, revision);

CREATE TABLE IF NOT EXISTS user_edge_repertoire_tag (
  user_id text NOT NULL,
  edge_id bigint NOT NULL REFERENCES move_edge(id),
  repertoire_id text NOT NULL,
  is_deleted boolean NOT NULL,
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL,
  origin_device text NOT NULL,
  device_seq bigint NOT NULL,
  revision bigint NOT NULL,
  PRIMARY KEY (user_id, edge_id, repertoire_id)
);

CREATE INDEX IF NOT EXISTS user_edge_repertoire_tag_cursor
  ON user_edge_repertoire_tag (user_id, revision);

-- One row per published version. Publishing again inserts a new version rather than mutating
-- one, and a version's moves never change after insert; only its status does.
CREATE TABLE IF NOT EXISTS repertoire_version (
  id text NOT NULL,
  version int NOT NULL,
  author_id text NOT NULL,
  title text NOT NULL,
  description text NOT NULL,
  side text NOT NULL,
  move_count int NOT NULL,
  status text NOT NULL,
  published_at timestamptz NOT NULL,
  PRIMARY KEY (id, version)
);

-- The only multi row read paths are "the latest version per id with this status" and "every
-- non removed repertoire this author currently owns", for quota accounting.
CREATE INDEX IF NOT EXISTS repertoire_version_status ON repertoire_version (status, id);

-- The moves of one published version, each with its author's marking. Shares position and
-- move_edge with sync, so a move played by many repertoires and users is stored once. The move is
-- kept here too: move_edge.move is whatever spelling first interned those endpoints, while this
-- one is the canonical SAN the publish was validated with.
CREATE TABLE IF NOT EXISTS repertoire_edge (
  repertoire_id text NOT NULL,
  version int NOT NULL,
  edge_id bigint NOT NULL REFERENCES move_edge(id),
  move text NOT NULL,
  is_good boolean NOT NULL,
  PRIMARY KEY (repertoire_id, version, edge_id),
  FOREIGN KEY (repertoire_id, version) REFERENCES repertoire_version(id, version)
);

-- Anonymous install-popularity counter, keyed by id alone so it survives a republish under a new
-- version (repertoire_version's primary key includes version). A row here with no matching
-- repertoire_version is harmless: nothing joins to it except by id, and the catalog never lists an
-- id that was never published.
CREATE TABLE IF NOT EXISTS repertoire_install_count (
  id text PRIMARY KEY,
  count bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS repertoire_version_author ON repertoire_version (author_id, status);

-- One row per device the server has ever been told about. The position columns are what tombstone
-- GC reads: last_served_revision is what was handed over, last_acked_revision what the device
-- confirmed writing, and only the second one is safe to collect against.
--
-- Removal sets removed_at rather than deleting the row, because a surviving acknowledgement is the
-- only thing that tells a returning removed device apart from a brand new install.
CREATE TABLE IF NOT EXISTS sync_device (
  user_id text NOT NULL,
  device_id text NOT NULL,
  platform text NOT NULL,
  last_acked_revision bigint NOT NULL DEFAULT 0,
  last_served_revision bigint NOT NULL DEFAULT 0,
  last_page_token text,
  removed_at timestamptz,
  last_seen_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, device_id)
);

-- The highest revision below which a tombstone may already be gone, per user. Only a device that
-- was removed and has fallen below it needs to resync.
CREATE TABLE IF NOT EXISTS sync_gc_floor (
  user_id text PRIMARY KEY,
  floor_revision bigint NOT NULL
);

-- A user's last erase. A row stamped before erased_at is stale and must not resurrect what the
-- erase removed, unless its pusher has acked erased_revision (it pulled the erase) or erased_by
-- wrote it after erasing (device_seq above erase_device_seq).
CREATE TABLE IF NOT EXISTS sync_erase_cutoff (
  user_id text PRIMARY KEY,
  erased_at timestamptz NOT NULL,
  erased_revision bigint NOT NULL,
  erased_by text NOT NULL,
  erase_device_seq bigint NOT NULL
);
