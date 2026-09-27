#!/usr/bin/env python3
"""
Erzeugt die Test-Fixtures für die Referenztestsuite:
  - deterministische Testbilder (Endlos + zwei Die-Cut-Formate)
  - davon abgeleitete Raster-Rohdaten (so wie sie der Rasterizer liefert)
  - Referenz-Druckdatenströme, erzeugt mit brother_ql (GPLv3)

Die Java-Tests erzeugen aus denselben Rohdaten den Datenstrom mit dem
Java-Encoder und vergleichen byteweise.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageOps

OUT = sys.argv[1] if len(sys.argv) > 1 else "tests/fixtures"


def make_raw(img, device_px, dots, margin, threshold_pct=70):
    """Bitmap -> gepackte Rasterzeilen (exakt die Pipeline des Rasterizers)."""
    canvas = Image.new("L", (device_px, img.size[1]), 255)
    canvas.paste(img, (device_px - dots - margin, 0))
    th = int((100 - threshold_pct) / 100.0 * 255)
    im = ImageOps.invert(canvas.convert("L"))
    im = im.point(lambda x: 0 if x < th else 255, mode="1")
    im = im.transpose(Image.FLIP_LEFT_RIGHT)
    return im.convert("1").tobytes()


def pattern(w, h, text):
    img = Image.new("L", (w, h), 255)
    d = ImageDraw.Draw(img)
    d.rectangle([10, 10, w - 11, h - 11], outline=0, width=4)
    for y in range(120, h - 100, 300):
        d.rectangle([40, y, w - 41, y + 150], outline=0, width=2)
    d.text((60, h // 2 - 10), text, fill=0)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)

    from brother_ql.raster import BrotherQLRaster
    from brother_ql.conversion import convert

    def ref(pages, label, path):
        qlr = BrotherQLRaster("QL-1110NWB")
        convert(qlr, pages, label, cut=True, hq=True)
        data = b"".join(bytes((x,)) if isinstance(x, int) else x for x in qlr.data)
        with open(path, "wb") as f:
            f.write(data)
        return data

    # --- Endlos 62x145 (696 x 1713 px) ---
    img = pattern(696, 1713, "Endlos 62x145mm")
    with open(f"{OUT}/raw_endless.bin", "wb") as f:
        f.write(make_raw(img, 1296, 696, 56))
    ref([img], "62", f"{OUT}/ref_endless_1p.bin")
    ref([img, img], "62", f"{OUT}/ref_endless_2p.bin")

    # --- Die-Cut 62x100 (696 x 1109 px) ---
    img = pattern(696, 1109, "DieCut 62x100")
    with open(f"{OUT}/raw_62x100.bin", "wb") as f:
        f.write(make_raw(img, 1296, 696, 56))
    ref([img], "62x100", f"{OUT}/ref_62x100.bin")

    # --- Die-Cut 103x164 (1200 x 1822 px, Medienbreite 104!) ---
    img = pattern(1200, 1822, "DieCut 103x164")
    with open(f"{OUT}/raw_103x164.bin", "wb") as f:
        f.write(make_raw(img, 1296, 1200, 56))
    ref([img], "103x164", f"{OUT}/ref_103x164.bin")

    # --- Synthetisches Zeilen-Tintenprofil (Block-Erkennung, v1.9) ---
    rows = [0] * 3508
    for y in range(100, 401):      # Block A: klein, aber sehr dicht
        rows[y] = 1000
    for y in range(700, 3401):     # Block B: gross, insgesamt mehr Tinte
        rows[y] = 400
    with open(f"{OUT}/rowink_synthetic.csv", "w") as f:
        f.write("\n".join(str(v) for v in rows))
    # Erwartung: 2 Bloecke [100..401] und [700..3401];
    # Tinte A = 301*1000 = 301.000 < B = 2701*400 = 1.080.400
    # => dichtester Block = B (Index 1). Deckt den Real-Fall ab, dass ein
    #    grosser "AGB-Block" mehr Tinte haben kann als das Etikett.

    print("Fixtures erzeugt in", OUT)


if __name__ == "__main__":
    main()
