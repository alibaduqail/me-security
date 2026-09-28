#!/usr/bin/env python3
"""Validate maintained resource files without modifying artwork or build outputs."""

from __future__ import annotations

import json
from pathlib import Path
import re
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
PART = RESOURCES / "assets/ae2security/textures/part"
LAYERS = ("security_terminal_bright", "security_terminal_medium", "security_terminal_dark")


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result = {}
    for key, value in pairs:
        assert key not in result, f"Duplicate JSON key: {key}"
        result[key] = value
    return result


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def rgba_pixels(path: Path) -> list[tuple[int, int, int, int]]:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"{path}: not a PNG"
    offset = 8
    compressed = bytearray()
    width = height = None
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        name = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        checksum = struct.unpack_from(">I", data, offset + 8 + length)[0]
        assert zlib.crc32(name + payload) & 0xFFFFFFFF == checksum, f"{path}: bad PNG CRC"
        offset += length + 12
        if name == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            assert (width, height, depth, color, compression, filtering, interlace) == (16, 16, 8, 6, 0, 0, 0), (
                f"{path}: expected a 16x16 RGBA PNG")
        elif name == b"IDAT":
            compressed.extend(payload)
        elif name == b"IEND":
            break
    assert width == height == 16 and compressed, f"{path}: incomplete PNG"
    raw = zlib.decompress(compressed)
    stride = width * 4
    previous = bytearray(stride)
    pixels = []
    cursor = 0
    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        row = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        for i in range(stride):
            left = row[i - 4] if i >= 4 else 0
            above = previous[i]
            upper_left = previous[i - 4] if i >= 4 else 0
            if filter_type == 1:
                row[i] = (row[i] + left) & 255
            elif filter_type == 2:
                row[i] = (row[i] + above) & 255
            elif filter_type == 3:
                row[i] = (row[i] + (left + above) // 2) & 255
            elif filter_type == 4:
                estimate = left + above - upper_left
                distances = (abs(estimate - left), abs(estimate - above), abs(estimate - upper_left))
                predictor = (left, above, upper_left)[distances.index(min(distances))]
                row[i] = (row[i] + predictor) & 255
            else:
                assert filter_type == 0, f"{path}: unknown PNG filter {filter_type}"
        pixels.extend(tuple(row[i:i + 4]) for i in range(0, stride, 4))
        previous = row
    assert cursor == len(raw), f"{path}: unexpected PNG data"
    return pixels


def main() -> None:
    json_files = list(RESOURCES.rglob("*.json"))
    for path in json_files:
        read_json(path)

    for state in ("on", "off"):
        model = read_json(RESOURCES / f"assets/ae2security/models/part/security_terminal_{state}.json")
        for layer in LAYERS:
            assert f"ae2security:part/{layer}" in model["textures"].values(), (
                f"{state} model missing {layer}")
    for layer in LAYERS:
        pixels = rgba_pixels(PART / f"{layer}.png")
        assert any(pixel[3] > 0 for pixel in pixels), f"{layer}: empty layer"
        assert any(pixel[3] == 0 for pixel in pixels), f"{layer}: expected transparency"
    assert all(pixel[3] == 0 for pixel in rgba_pixels(PART / "blank.png")), "blank texture is not transparent"

    language = read_json(RESOURCES / "assets/ae2security/lang/en_us.json")
    referenced = set()
    pattern = re.compile(r'"((?:message|screen|gui|permission|itemGroup|item|block)\.ae2security\.[A-Za-z0-9_.]+)"')
    for source in (ROOT / "src/main/java").rglob("*.java"):
        referenced.update(key for key in pattern.findall(source.read_text(encoding="utf-8"))
                          if not key.endswith("."))
    missing = referenced - language.keys()
    assert not missing, f"Missing English translations: {sorted(missing)}"

    for model in (RESOURCES / "assets/ae2security/models").rglob("*.json"):
        for value in read_json(model).get("textures", {}).values():
            if value.startswith("ae2security:"):
                texture = RESOURCES / "assets/ae2security/textures" / (value.split(":", 1)[1] + ".png")
                assert texture.is_file(), f"{model}: missing {texture}"
    for guide in (RESOURCES / "assets/ae2security/ae2guide").glob("*.md"):
        for target in re.findall(r'\]\(ae2security:([^)]*\.md)\)', guide.read_text(encoding="utf-8")):
            assert (guide.parent / target).is_file(), f"{guide}: missing linked page {target}"
    print(f"Validated {len(json_files)} JSON resources and terminal layers")


if __name__ == "__main__":
    main()
