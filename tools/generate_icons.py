#!/usr/bin/env python3
"""Generate the app icon set from a single vector-like drawing."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "web" / "assets"
MACOS = ROOT / "macos" / "build"
ANDROID_RES = ROOT / "android" / "app" / "src" / "main" / "res"
for path in (ASSETS, MACOS, ANDROID_RES):
    path.mkdir(parents=True, exist_ok=True)

SIZE = 4096
S = SIZE / 1024.0


def sc(v):
    return int(round(v * S))


def linear_gradient(size, start, end):
    """Diagonal two-stop gradient."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    sx, sy = start
    ex, ey = end
    for y in range(size):
        for x in range(size):
            t = (x / size * sx + y / size * sy) / (sx + sy)
            # Smoothstep for a richer falloff.
            t = t * t * (3 - 2 * t)
            px[x, y] = tuple(int(a + (b - a) * t) for a, b in zip((58, 150, 255), (74, 70, 214)))
    return img


def rounded_mask(size, box, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(box, radius=radius, fill=255)
    return mask


def font(size, bold=True):
    candidates = [
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf",
    ]
    for path in candidates:
        if not Path(path).exists():
            continue
        f = ImageFont.truetype(path, size)
        if bold:
            try:
                names = f.get_variation_names()
                for wanted in (b"Bold", b"Semibold", b"Heavy"):
                    if wanted in names:
                        f.set_variation_by_name(wanted)
                        break
            except Exception:
                pass
        return f
    return ImageFont.load_default()


def build_icon(scale=1.0, transparent_margin=True):
    """Draw at SIZE (or scaled) with optional macOS-style outer margin."""
    size = int(SIZE * scale)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    margin = 74 if transparent_margin else 0
    box = (sc(margin), sc(margin), sc(1024 - margin), sc(1024 - margin))
    radius = sc(208 if transparent_margin else 220)

    # Soft drop shadow behind the squircle.
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (box[0], box[1] + sc(18), box[2], box[3] + sc(18)),
        radius=radius,
        fill=(20, 30, 80, 110),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(sc(26)))
    img.alpha_composite(shadow)

    # Gradient body clipped to the squircle.
    grad = linear_gradient(size, (1.0, 0.2), (0.1, 1.0)).convert("RGBA")
    mask = rounded_mask(size, box, radius)
    img.paste(grad, (0, 0), mask)

    # Glossy highlight in the upper-left.
    gloss = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gloss)
    gd.ellipse((sc(-120), sc(-320), sc(760), sc(430)), fill=(255, 255, 255, 70))
    gloss = gloss.filter(ImageFilter.GaussianBlur(sc(80)))
    gloss.putalpha(Image.composite(gloss.getchannel("A"), Image.new("L", (size, size), 0), mask))
    img.alpha_composite(gloss)

    # Fine inner rim.
    rim = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(rim).rounded_rectangle(
        (box[0] + sc(3), box[1] + sc(3), box[2] - sc(3), box[3] - sc(3)),
        radius=radius - sc(3),
        outline=(255, 255, 255, 52),
        width=sc(4),
    )
    img.alpha_composite(rim)

    # Binder tabs peeking over the page.
    for cx in (372, 652):
        tab = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(tab).rounded_rectangle(
            (sc(cx - 25), sc(214), sc(cx + 25), sc(342)),
            radius=sc(25),
            fill=(225, 229, 240, 255),
        )
        tab = tab.filter(ImageFilter.GaussianBlur(sc(1)))
        img.alpha_composite(tab)

    # Calendar page with a soft shadow.
    page = (sc(224), sc(250), sc(800), sc(808))
    page_radius = sc(92)
    page_shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(page_shadow).rounded_rectangle(
        (page[0], page[1] + sc(20), page[2], page[3] + sc(26)),
        radius=page_radius,
        fill=(25, 35, 82, 100),
    )
    page_shadow = page_shadow.filter(ImageFilter.GaussianBlur(sc(30)))
    img.alpha_composite(page_shadow)

    page_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pd = ImageDraw.Draw(page_layer)
    pd.rounded_rectangle(page, radius=page_radius, fill=(255, 255, 255, 255))

    # Four shift dots.
    colors = [(255, 159, 10), (10, 132, 255), (94, 92, 230), (48, 209, 88)]
    for i, color in enumerate(colors):
        cx = 324 + i * 126
        cy = 353
        pd.ellipse((sc(cx - 23), sc(cy - 23), sc(cx + 23), sc(cy + 23)), fill=color + (255,))
    img.alpha_composite(page_layer)

    # Date.
    date_font = font(sc(250), bold=True)
    text = "03"
    dtext = ImageDraw.Draw(img)
    bbox = dtext.textbbox((0, 0), text, font=date_font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    tx = (sc(512) - tw / 2) - bbox[0]
    ty = sc(610) - th / 2 - bbox[1]
    dtext.text((tx, ty), text, font=date_font, fill=(29, 29, 31, 255))

    # Tiny month label.
    label_font = font(sc(43), bold=True)
    label = "NOV"
    lb = dtext.textbbox((0, 0), label, font=label_font)
    lw = lb[2] - lb[0]
    lx = sc(512) - lw / 2 - lb[0]
    ly = sc(745)
    dtext.text((lx, ly), label, font=label_font, fill=(142, 142, 147, 255), spacing=0)

    return img


def save_png(img, path, size=None):
    if size:
        img = img.resize((size, size), Image.LANCZOS)
    img.save(path, "PNG", optimize=True)
    print(f"wrote {path} ({img.width}x{img.height})")


# Master icon (1024) with macOS margin.
master = build_icon()
save_png(master, ASSETS / "icon-1024.png", 1024)
save_png(master, ASSETS / "icon-512.png", 512)
save_png(master, ASSETS / "icon-192.png", 192)
save_png(master, ASSETS / "icon-64.png", 64)

# Full-bleed icon for Android legacy launcher icons.
android_master = build_icon(transparent_margin=False)
for density, px in {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}.items():
    out_dir = ANDROID_RES / f"mipmap-{density}"
    out_dir.mkdir(parents=True, exist_ok=True)
    save_png(android_master, out_dir / "ic_launcher.png", px)

# Adaptive foreground: transparent, glyph kept inside the 66% safe zone.
fg = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
fg_page = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
fd = ImageDraw.Draw(fg_page)
page = (sc(264), sc(286), sc(760), sc(760))
fd.rounded_rectangle(page, radius=sc(86), fill=(255, 255, 255, 255))
for i, color in enumerate([(255, 159, 10), (10, 132, 255), (94, 92, 230), (48, 209, 88)]):
    cx = 348 + i * 110
    cy = 382
    fd.ellipse((sc(cx - 20), sc(cy - 20), sc(cx + 20), sc(cy + 20)), fill=color + (255,))
date_font = font(sc(200), bold=True)
fd2 = ImageDraw.Draw(fg_page)
bbox = fd2.textbbox((0, 0), "03", font=date_font)
tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
fd2.text(
    (sc(512) - tw / 2 - bbox[0], sc(606) - th / 2 - bbox[1]),
    "03",
    font=date_font,
    fill=(29, 29, 31, 255),
)
fg.alpha_composite(fg_page)
# Crop to the 66% adaptive safe zone target, scaled into a 432 canvas.
safe = fg.crop((sc(200), sc(200), sc(824), sc(824))).resize((432, 432), Image.LANCZOS)
for density, px in {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}.items():
    out_dir = ANDROID_RES / f"mipmap-{density}"
    save_png(safe, out_dir / "ic_launcher_foreground.png", px)

# macOS icns via iconset.
iconset = MACOS / "AppIcon.iconset"
iconset.mkdir(parents=True, exist_ok=True)
for px, name in [
    (16, "icon_16x16.png"),
    (32, "icon_16x16@2x.png"),
    (32, "icon_32x32.png"),
    (64, "icon_32x32@2x.png"),
    (128, "icon_128x128.png"),
    (256, "icon_128x128@2x.png"),
    (256, "icon_256x256.png"),
    (512, "icon_256x256@2x.png"),
    (512, "icon_512x512.png"),
    (1024, "icon_512x512@2x.png"),
]:
    save_png(master, iconset / name, px)
print("icon generation complete")
