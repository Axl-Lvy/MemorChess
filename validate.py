#!/usr/bin/env python3
"""Validate the repertoire manifest and PGN files of the repertoire-data branch.

Checks performed:
  * manifest.json parses, declares a schemaVersion, has unique kebab-case ids,
    and uses only "white" or "black" as color.
  * Every manifest entry points to an existing file, and every .pgn file under
    pgn/ is listed in the manifest.
  * Every PGN parses without errors and every move in the mainline and in all
    nested variations is legal.
  * The declared moveCount matches the actual number of moves in the file.

Exits with a nonzero status and a clear message on the first failure group.
Run with --print-counts to print the actual move count per PGN file, which is
useful when filling in the manifest.
"""

import json
import re
import sys
from pathlib import Path

import chess
import chess.pgn

ROOT = Path(__file__).resolve().parent
MANIFEST_PATH = ROOT / "manifest.json"
PGN_DIR = ROOT / "pgn"
KEBAB_CASE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
VALID_COLORS = {"white", "black"}


def fail(message):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


def load_manifest():
    if not MANIFEST_PATH.is_file():
        fail(f"manifest.json not found at {MANIFEST_PATH}")
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        fail(f"manifest.json is not valid JSON: {error}")
    if not isinstance(manifest.get("schemaVersion"), int):
        fail("manifest.json must declare an integer schemaVersion at the top level")
    repertoires = manifest.get("repertoires")
    if not isinstance(repertoires, list) or not repertoires:
        fail("manifest.json must declare a non-empty 'repertoires' list")
    return repertoires


def check_manifest_entries(repertoires):
    seen_ids = set()
    for entry in repertoires:
        for field in ("id", "name", "color", "description", "moveCount", "file"):
            if field not in entry:
                fail(f"manifest entry {entry!r} is missing the '{field}' field")
        rep_id = entry["id"]
        if not KEBAB_CASE.match(rep_id):
            fail(f"repertoire id '{rep_id}' is not kebab-case")
        if rep_id in seen_ids:
            fail(f"duplicate repertoire id '{rep_id}'")
        seen_ids.add(rep_id)
        if entry["color"] not in VALID_COLORS:
            fail(f"repertoire '{rep_id}' has invalid color '{entry['color']}'")
        if not isinstance(entry["moveCount"], int) or entry["moveCount"] <= 0:
            fail(f"repertoire '{rep_id}' must declare a positive integer moveCount")


def check_file_listing(repertoires):
    listed = set()
    for entry in repertoires:
        path = ROOT / entry["file"]
        if not path.is_file():
            fail(f"repertoire '{entry['id']}' points to missing file '{entry['file']}'")
        listed.add(path.resolve())
    on_disk = {path.resolve() for path in PGN_DIR.glob("*.pgn")}
    unlisted = on_disk - listed
    if unlisted:
        names = ", ".join(sorted(str(path.relative_to(ROOT)) for path in unlisted))
        fail(f"PGN files not listed in the manifest: {names}")


def walk_and_count(node, board, path):
    """Recursively validate legality of every variation and count the moves."""
    count = 0
    for child in node.variations:
        move = child.move
        if move not in board.legal_moves:
            fail(
                f"{path}: illegal move {board.san(move) if board.is_pseudo_legal(move) else move.uci()} "
                f"in position {board.fen()}"
            )
        board.push(move)
        count += 1 + walk_and_count(child, board, path)
        board.pop()
    return count


def validate_pgn(entry):
    path = ROOT / entry["file"]
    with open(path, encoding="utf-8") as handle:
        game = chess.pgn.read_game(handle)
        if game is None:
            fail(f"{entry['file']}: no game found")
        if chess.pgn.read_game(handle) is not None:
            fail(f"{entry['file']}: must contain exactly one game")
    if game.errors:
        details = "; ".join(str(error) for error in game.errors)
        fail(f"{entry['file']}: PGN parser reported errors: {details}")
    if game.headers.get("Result") != "*":
        fail(f"{entry['file']}: Result header must be '*'")
    actual = walk_and_count(game, game.board(), entry["file"])
    if actual != entry["moveCount"]:
        fail(
            f"{entry['file']}: manifest declares moveCount {entry['moveCount']} "
            f"but the file contains {actual} moves"
        )
    return actual


def print_counts(repertoires):
    for entry in repertoires:
        path = ROOT / entry["file"]
        with open(path, encoding="utf-8") as handle:
            game = chess.pgn.read_game(handle)
        if game is None:
            fail(f"{entry['file']}: no game found")
        if game.errors:
            details = "; ".join(str(error) for error in game.errors)
            fail(f"{entry['file']}: PGN parser reported errors: {details}")
        count = walk_and_count(game, game.board(), entry["file"])
        print(f"{entry['file']}: {count}")


def main():
    repertoires = load_manifest()
    check_manifest_entries(repertoires)
    check_file_listing(repertoires)
    if "--print-counts" in sys.argv[1:]:
        print_counts(repertoires)
        return
    for entry in repertoires:
        count = validate_pgn(entry)
        print(f"OK {entry['id']} ({entry['color']}): {count} moves in {entry['file']}")
    print(f"All {len(repertoires)} repertoires are valid.")


if __name__ == "__main__":
    main()
