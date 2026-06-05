"""
Checks that every translation locale is a complete, in-order mirror of the base
locale.

The base locale is `composeApp/src/commonMain/composeResources/values`. Every
`values-<lang>` directory must contain, for each base `*.xml` resource file, a
file with the exact same `<string>`/`<plurals>` keys, in the same order. This
catches missing translations, stray extra keys, and drift between the base and a
locale.
"""

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Set

COMPOSE_RESOURCES_DIR = Path("composeApp/src/commonMain/composeResources")
BASE_LOCALE_DIR = COMPOSE_RESOURCES_DIR / "values"

# Tolerant of optional whitespace around '=' (e.g. `name ="x"` in
# components_description.xml).
KEY_PATTERN = re.compile(r'<(string|plurals)\s+name\s*=\s*"([^"]+)"')


class Color:
    """ANSI escape codes for colored terminal output."""

    RED = "\033[0;31m"
    GREEN = "\033[0;32m"
    YELLOW = "\033[1;33m"
    NC = "\033[0m"


@dataclass(frozen=True)
class TranslationKey:
    """A single resource key with its kind ('string' or 'plurals')."""

    name: str
    key_type: str

    def __str__(self) -> str:
        return f"{self.name} ({self.key_type})"


@dataclass(frozen=True)
class KeyDifference:
    """Keys present in one set but not the other."""

    missing: Set[TranslationKey]
    extra: Set[TranslationKey]


@dataclass(frozen=True)
class ValidationResult:
    """Outcome of validating one translation file."""

    label: str
    is_valid: bool
    error_message: Optional[str] = None


def extract_keys_from_file(file_path: Path) -> List[TranslationKey]:
    """Returns the string/plurals keys in [file_path], in document order."""
    try:
        content = file_path.read_text(encoding="utf-8")
    except OSError as error:
        print(f"{Color.RED}Error reading {file_path}: {error}{Color.NC}")
        return []
    return [
        TranslationKey(name=match.group(2), key_type=match.group(1))
        for match in KEY_PATTERN.finditer(content)
    ]


def get_key_differences(
    base_keys: List[TranslationKey],
    translation_keys: List[TranslationKey],
) -> KeyDifference:
    """Returns the keys missing from / extra in the translation."""
    base_set = set(base_keys)
    translation_set = set(translation_keys)
    return KeyDifference(
        missing=base_set - translation_set,
        extra=translation_set - base_set,
    )


def print_key_count_mismatch(
    label: str,
    expected_count: int,
    actual_count: int,
    differences: KeyDifference,
) -> None:
    """Prints which keys are missing/extra when counts differ."""
    print(
        f"{Color.RED}✗ {label}: key count mismatch "
        f"(expected {expected_count}, got {actual_count}){Color.NC}"
    )
    if differences.missing:
        missing = ", ".join(
            str(key) for key in sorted(differences.missing, key=lambda k: k.name)
        )
        print(f"{Color.YELLOW}  Missing keys: {missing}{Color.NC}")
    if differences.extra:
        extra = ", ".join(
            str(key) for key in sorted(differences.extra, key=lambda k: k.name)
        )
        print(f"{Color.YELLOW}  Extra keys: {extra}{Color.NC}")


def print_key_order_mismatch(
    label: str,
    base_keys: List[TranslationKey],
    translation_keys: List[TranslationKey],
) -> None:
    """Prints the first positions where the key order diverges."""
    print(f"{Color.RED}✗ {label}: keys do not match or are in a different order{Color.NC}")
    for index, (base_key, trans_key) in enumerate(zip(base_keys, translation_keys)):
        if base_key != trans_key:
            print(f"  Position {index + 1}: expected '{base_key}', got '{trans_key}'")


def validate_translation_file(
    translation_file: Path,
    label: str,
    base_keys: List[TranslationKey],
) -> ValidationResult:
    """Validates one translation file against the base keys."""
    if not translation_file.exists():
        print(f"{Color.RED}✗ {label}: file not found{Color.NC}")
        return ValidationResult(label=label, is_valid=False, error_message="File not found")

    print(f"Checking {label}...")
    translation_keys = extract_keys_from_file(translation_file)

    if len(base_keys) != len(translation_keys):
        differences = get_key_differences(base_keys, translation_keys)
        print_key_count_mismatch(label, len(base_keys), len(translation_keys), differences)
        return ValidationResult(label=label, is_valid=False, error_message="Key count mismatch")

    if base_keys != translation_keys:
        print_key_order_mismatch(label, base_keys, translation_keys)
        return ValidationResult(label=label, is_valid=False, error_message="Keys out of order")

    print(f"{Color.GREEN}✓ {label}: all keys match and are in order{Color.NC}")
    return ValidationResult(label=label, is_valid=True)


def get_translation_directories() -> List[Path]:
    """Returns the sorted `values-*` locale directories."""
    return sorted(
        directory
        for directory in COMPOSE_RESOURCES_DIR.iterdir()
        if directory.is_dir() and directory.name.startswith("values-")
    )


def check_translations() -> int:
    """Validates every locale against the base locale. Returns a process code."""
    if not BASE_LOCALE_DIR.is_dir():
        print(f"{Color.RED}Error: base locale not found at {BASE_LOCALE_DIR}{Color.NC}")
        return 1

    base_files = sorted(BASE_LOCALE_DIR.glob("*.xml"))
    if not base_files:
        print(f"{Color.RED}Error: no resource files found in {BASE_LOCALE_DIR}{Color.NC}")
        return 1

    print(f"{Color.GREEN}Checking translation files...{Color.NC}")
    print(f"Base locale: {BASE_LOCALE_DIR}")
    for base_file in base_files:
        keys = extract_keys_from_file(base_file)
        strings = sum(1 for key in keys if key.key_type == "string")
        plurals = sum(1 for key in keys if key.key_type == "plurals")
        print(
            f"{Color.YELLOW}  {base_file.name}: {len(keys)} keys "
            f"({strings} strings, {plurals} plurals){Color.NC}"
        )
    print()

    translation_dirs = get_translation_directories()
    if not translation_dirs:
        print(f"{Color.YELLOW}No translation directories found.{Color.NC}")
        return 0

    results: List[ValidationResult] = []
    for locale_dir in translation_dirs:
        for base_file in base_files:
            base_keys = extract_keys_from_file(base_file)
            label = f"{locale_dir.name}/{base_file.name}"
            results.append(
                validate_translation_file(locale_dir / base_file.name, label, base_keys)
            )
        print()

    if all(result.is_valid for result in results):
        print(f"{Color.GREEN}✓ All translation files are valid!{Color.NC}")
        return 0

    print(f"{Color.RED}✗ Translation validation failed!{Color.NC}")
    return 1


if __name__ == "__main__":
    sys.exit(check_translations())
