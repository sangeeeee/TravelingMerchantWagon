"""Validate locale coverage and Minecraft translation placeholders without launching the game.

Optionally pass a Touhou Little Maid JAR to verify the supported locale set against it.
"""
from collections import Counter
from pathlib import Path
import json
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "versions/mc-1.21.1/common/src/main/resources/assets/tm_wagon/lang"
# Language resources shipped by the compile-time Touhou Little Maid 1.5.3 dependency.
LOCALES = set("de_de en_us es_es fr_fr id_id it_it ja_jp ko_kr la_la lzh pt_br pt_pt ru_ru tr_tr vi_vn zh_cn zh_tw".split())
TOKEN = re.compile(r"%(?:(\d+)\$)?s|%%")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate translation key: {key}")
        result[key] = value
    return result


def arguments(text):
    result = []
    sequential = 0
    end = 0
    for match in TOKEN.finditer(text):
        assert "%" not in text[end:match.start()], f"Invalid format: {text}"
        end = match.end()
        if match.group() == "%%":
            continue
        if match.group(1):
            result.append(int(match.group(1)))
        else:
            sequential += 1
            result.append(sequential)
    assert "%" not in text[end:], f"Invalid format: {text}"
    return Counter(result)


def main():
    files = {p.stem: p for p in LANG.glob("*.json")}
    assert set(files) == LOCALES, "Unexpected or missing locale files"
    if len(sys.argv) > 1:
        with zipfile.ZipFile(sys.argv[1]) as jar:
            maid = {Path(n).stem for n in jar.namelist()
                    if n.startswith("assets/touhou_little_maid/lang/") and n.endswith(".json")}
        assert set(files) == maid, "Locale set differs from Touhou Little Maid"
    source = json.loads(files["en_us"].read_text(encoding="utf-8"), object_pairs_hook=unique_object)
    for locale, path in sorted(files.items()):
        data = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)
        assert data.keys() == source.keys(), f"Translation keys differ: {locale}"
        for key, value in data.items():
            assert isinstance(value, str) and value.strip(), f"Empty translation: {locale}/{key}"
            assert "\ufffd" not in value and not any(ord(c) < 32 for c in value), f"Invalid characters: {locale}/{key}"
            assert arguments(value) == arguments(source[key]), f"Placeholder mismatch: {locale}/{key}"
        print(f"{locale}: {len(data)} translations, keys and placeholders valid")
    print(f"Validated {len(files)} locales and {len(files) * len(source)} translations.")


if __name__ == "__main__":
    main()
