"""Generate launcher PNG icons (legacy fallback + adaptive-compatible look)."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

BG_TOP = (37, 99, 235)      # #2563EB
BG_BOTTOM = (29, 78, 216)   # #1D4ED8
WHITE = (255, 255, 255, 255)


def gradient(size, circular=False):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    if circular:
        md.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        md.rounded_rectangle((0, 0, size - 1, size - 1), radius=int(size * 0.22), fill=255)
    for y in range(size):
        t = y / max(1, size - 1)
        color = tuple(int(BG_TOP[i] + (BG_BOTTOM[i] - BG_TOP[i]) * t) for i in range(3)) + (255,)
        draw.line((0, y, size, y), fill=color)
    img.putalpha(mask)
    return img


def draw_symbol(img, circular=False):
    s = img.size[0]
    draw = ImageDraw.Draw(img)
    scale = s / 108.0
    w = max(2, int(8 * scale))
    # Arrow shaft
    draw.line((54 * scale, 30 * scale, 54 * scale, 66 * scale), fill=WHITE, width=w)
    # Arrow head
    draw.line((36 * scale, 50 * scale, 54 * scale, 68 * scale), fill=WHITE, width=w)
    draw.line((72 * scale, 50 * scale, 54 * scale, 68 * scale), fill=WHITE, width=w)
    # Tray / baseline
    draw.line((34 * scale, 78 * scale, 74 * scale, 78 * scale), fill=WHITE, width=w)
    return img


def make_icon(size, circular=False):
    ss = 4
    big = size * ss
    img = gradient(big, circular)
    img = draw_symbol(img, circular)
    return img.resize((size, size), Image.LANCZOS)


for density, size in DENSITIES.items():
    folder = RES / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    make_icon(size).save(folder / "ic_launcher.png")
    make_icon(size, circular=True).save(folder / "ic_launcher_round.png")

print("Generated icons for:", ", ".join(DENSITIES))
