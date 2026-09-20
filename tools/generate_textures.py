#!/usr/bin/env python3
"""Generate the addon's original pixel textures. Uses only the Python standard library."""
from pathlib import Path
import struct
import zlib

ASSET_ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/ae2security/textures'
PART_ROOT = ASSET_ROOT / 'part'


def rect(pixels, box, color):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            pixels[y][x] = color


def png(pixels):
    def chunk(name, data):
        return struct.pack('>I', len(data)) + name + data + struct.pack('>I', zlib.crc32(name + data) & 0xFFFFFFFF)
    raw = b''.join(b'\0' + bytes(value for pixel in row for value in pixel) for row in pixels)
    height = len(pixels)
    width = len(pixels[0])
    color_type = 6 if len(pixels[0][0]) == 4 else 2
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, color_type, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')


def part_face():
    p = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    rect(p, (2, 2, 14, 14), (20, 25, 43, 255))
    rect(p, (3, 3, 13, 4), (55, 65, 98, 255))
    rect(p, (5, 5, 11, 7), (198, 166, 246, 255))
    rect(p, (4, 6, 6, 10), (161, 121, 219, 255))
    rect(p, (10, 6, 12, 10), (161, 121, 219, 255))
    rect(p, (4, 9, 12, 13), (148, 107, 205, 255))
    rect(p, (5, 9, 11, 10), (218, 189, 255, 255))
    rect(p, (7, 10, 9, 12), (42, 32, 66, 255))
    p[13][13] = (116, 232, 214, 255)
    return p


if __name__ == '__main__':
    # Clean resources from the earlier full-block prototype.
    legacy = ASSET_ROOT / 'block'
    for path in legacy.glob('security_terminal_*.png'):
        path.unlink()
    try:
        legacy.rmdir()
    except OSError:
        pass
    PART_ROOT.mkdir(parents=True, exist_ok=True)
    (PART_ROOT / 'security_terminal_face.png').write_bytes(png(part_face()))
    (PART_ROOT / 'blank.png').write_bytes(png([[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]))
