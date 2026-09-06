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

-- One row per published version, content addressed by the payload's sha256. Publishing again
-- inserts a new version rather than mutating one. The row for a given (id, version) never
-- changes after insert. There is no membership table: nothing needs "which repertoires contain
-- this position".
CREATE TABLE IF NOT EXISTS repertoire_version (
  id text NOT NULL,
  version int NOT NULL,
  author_id text NOT NULL,
  title text NOT NULL,
  description text NOT NULL,
  side text NOT NULL,
  payload_sha256 text NOT NULL,
  payload_bytes int NOT NULL,
  move_count int NOT NULL,
  status text NOT NULL,
  published_at timestamptz NOT NULL,
  PRIMARY KEY (id, version)
);

-- The only multi row read paths are "the latest version per id with this status" and "every
-- non removed repertoire this author currently owns", for quota accounting.
CREATE INDEX IF NOT EXISTS repertoire_version_status ON repertoire_version (status, id);

-- Anonymous install-popularity counter, keyed by id alone so it survives a republish under a new
-- version (repertoire_version's primary key includes version). A row here with no matching
-- repertoire_version is harmless: nothing joins to it except by id, and the catalog never lists an
-- id that was never published.
CREATE TABLE IF NOT EXISTS repertoire_install_count (
  id text PRIMARY KEY,
  count bigint NOT NULL
);
CREATE INDEX IF NOT EXISTS repertoire_version_author ON repertoire_version (author_id, status);
