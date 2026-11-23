"""Builds the offline app copy of the 3D garage from design/garage3d.html.

The design page loads three.js and fonts from CDNs; the app copy uses the files
bundled in app/src/main/assets/garage3d/ so it works without internet in the car.
Run: python3 tools/build_garage3d.py
"""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
src = (root / "design/garage3d.html").read_text(encoding="utf-8")

src = src.replace(
    '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Sora:wght@200;300;400;600&display=swap">',
    "<style>\n"
    "@font-face{font-family:Sora;font-weight:200;src:url(fonts/sora_extralight.ttf)}\n"
    "@font-face{font-family:Sora;font-weight:300 400;src:url(fonts/sora_light.ttf)}\n"
    "@font-face{font-family:Sora;font-weight:600;src:url(fonts/sora_semibold.ttf)}\n"
    "</style>",
)
src = src.replace("https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js", "./three.module.min.js")
src = src.replace("https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/", "./addons/")
assert "cdn.jsdelivr" not in src and "googleapis" not in src

page = (
    '<!doctype html>\n<html lang="tr">\n<head>\n<meta charset="utf-8">\n'
    '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
    "<style>*{box-sizing:border-box}body{margin:0}</style>\n"
    "</head>\n<body>\n" + src + "\n</body>\n</html>\n"
)
(root / "app/src/main/assets/garage3d/index.html").write_text(page, encoding="utf-8")
print("wrote app/src/main/assets/garage3d/index.html")
