#!/usr/bin/env python3
"""Extract transparent lossless WebP assets from the two attached Street Art sheets.

Usage: python3 scripts/extract_graffiti_assets.py months.jpg digits.jpg
Requires Pillow with WebP support. Use the full uncropped reference images.
"""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


# Interior polygons of the paper strips on the 956 × 1280 reference.
# Excluding the exterior also removes the cast shadows around the black lettering.
MONTHS = (
    ((50, 319), (339, 306), (342, 383), (57, 408)),
    ((354, 319), (581, 311), (582, 372), (354, 376)),
    ((615, 302), (870, 303), (872, 389), (611, 396)),
    ((94, 537), (319, 542), (316, 611), (89, 604)),
    ((365, 529), (562, 520), (564, 605), (363, 610)),
    ((623, 523), (843, 518), (843, 601), (621, 601)),
    ((103, 742), (294, 749), (294, 816), (99, 816)),
    ((349, 755), (566, 743), (570, 809), (350, 818)),
    ((605, 754), (842, 749), (843, 807), (605, 809)),
    ((42, 972), (318, 975), (318, 1046), (43, 1036)),
    ((370, 988), (554, 984), (553, 1044), (369, 1042)),
    ((612, 975), (877, 979), (874, 1053), (611, 1049)),
)

# Sampled ink hues approximating the reference inks. Used only to distinguish
# ink coverage from gray paper grain; output RGB is recovered from the photo.
INK = (
    (37, 10, 69), (13, 15, 10), (230, 133, 41), (61, 143, 122),
    (159, 33, 86), (158, 180, 74), (191, 105, 111), (43, 20, 88),
    (12, 14, 10), (206, 121, 25), (17, 62, 64), (139, 44, 99),
)

# The two rows on the 1280 × 847 reference read 12345 and 67890.
DIGIT_ROWS = (
    ("12345", (70, 236, 483, 736, 969, 1208), 95, 425),
    ("67890", (45, 300, 521, 752, 990, 1240), 430, 756),
)


def source(path, reference):
    image = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    actual_ratio = image.width / image.height
    if abs(actual_ratio / (reference[0] / reference[1]) - 1) > 0.025:
        raise ValueError(f"{path}: expected aspect ratio {reference[0]}:{reference[1]}")
    return image


def month_art(image, polygon, ink):
    points = [(round(x * image.width / 956), round(y * image.height / 1280)) for x, y in polygon]
    mask = Image.new("L", image.size)
    ImageDraw.Draw(mask).polygon(points, fill=255)
    box = mask.getbbox()
    crop, mask = image.crop(box), mask.crop(box)
    # Estimate paper brightness from the lightest pixels inside the strip.
    samples = [rgb for rgb, inside in zip(crop.getdata(), mask.getdata()) if inside]
    samples.sort(key=sum)
    paper = samples[len(samples) * 4 // 5:len(samples) * 9 // 10]
    background = tuple(sum(rgb[c] for rgb in paper) / len(paper) for c in range(3))
    direction = [background[c] - ink[c] for c in range(3)]
    length = sum(v * v for v in direction)
    black = max(ink) < 30

    def pixel(rgb, inside):
        if not inside:
            return (0, 0, 0, 0)
        delta = [background[c] - rgb[c] for c in range(3)]
        coverage = sum(a * b for a, b in zip(delta, direction)) / length
        if not black:
            # Neutral shadows change brightness, but do not follow the ink hue.
            channels = [(a, b) for a, b in zip(delta, direction)]
            hi = max(range(3), key=lambda c: direction[c])
            lo = min(range(3), key=lambda c: direction[c])
            hue = (channels[hi][0] - channels[lo][0]) / (direction[hi] - direction[lo])
            coverage = min(coverage, hue * 1.18)
        floor = 0.24 if black else 0.14
        alpha = max(0.0, min(1.0, (coverage - floor) / (1 - floor)))
        if alpha < 1 / 255:
            return (0, 0, 0, 0)
        color = [round(max(0, min(255, background[c] - delta[c] / max(coverage, alpha)))) for c in range(3)]
        return (*color, round(alpha * 255))

    result = Image.new("RGBA", crop.size)
    result.putdata([pixel(rgb, inside) for rgb, inside in zip(crop.getdata(), mask.getdata())])
    return result


def digit_art(image, bounds):
    x1, y1, x2, y2 = bounds
    crop = image.crop((round(x1 * image.width / 1280), round(y1 * image.height / 847),
                       round(x2 * image.width / 1280), round(y2 * image.height / 847)))
    alpha = ImageOps.invert(ImageOps.grayscale(crop)).point(lambda p: 0 if p < 12 else p)
    result = Image.new("RGBA", crop.size)
    result.putalpha(alpha)
    return result


def trim(image, name):
    alpha = image.getchannel("A")
    if alpha.getextrema()[1] < 100:
        raise ValueError(f"{name}: no lettering detected. Use the uncropped reference.")
    return image.crop(alpha.getbbox())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("months", type=Path)
    parser.add_argument("digits", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] /
                        "workcalendar/app/src/main/res/drawable-nodpi")
    args = parser.parse_args()
    try:
        months = source(args.months, (956, 1280))
        digits = source(args.digits, (1280, 847))
        assets = {
            f"graffiti_month_{i:02d}": month_art(months, polygon, ink)
            for i, (polygon, ink) in enumerate(zip(MONTHS, INK), 1)
        }
        for labels, edges, top, bottom in DIGIT_ROWS:
            for label, left, right in zip(labels, edges, edges[1:]):
                assets[f"graffiti_digit_{label}"] = digit_art(digits, (left, top, right, bottom))
        assets = {name: trim(image, name) for name, image in assets.items()}
        args.output.mkdir(parents=True, exist_ok=True)
        for name, image in assets.items():
            image.save(args.output / f"{name}.webp", lossless=True, method=6)
        print(f"Saved {len(assets)} transparent assets to {args.output}")
    except (ValueError, OSError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
