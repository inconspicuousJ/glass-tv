"""Build step: download real scenery photos (Unsplash, free licence) into the app's assets.
Each is centre-cropped to 1920x1080, very slightly darkened so the glass and clock stay readable,
and listed in assets/photos/index.json. Failures are skipped, so a missing photo never breaks the build.
Also writes photo-sheet.jpg (numbered thumbnails) for review."""
import json, io, os, sys, urllib.request
from PIL import Image, ImageDraw, ImageEnhance

root = os.path.dirname(os.path.abspath(__file__))
out_dir = os.path.join(root, '..', 'app', 'src', 'main', 'assets', 'photos')
os.makedirs(out_dir, exist_ok=True)
items, thumbs = [], []
for i, p in enumerate(json.load(open(os.path.join(root, 'photos.json')))):
    url = f"https://images.unsplash.com/photo-{p['id']}?w=2400&q=90&fm=jpg"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'glass-tv-build'})
        data = urllib.request.urlopen(req, timeout=60).read()
        im = Image.open(io.BytesIO(data)).convert('RGB')
    except Exception as e:
        print('skip', p['name'], e); continue
    w, h = im.size; t = 16 / 9
    if w / h > t: nw = int(h * t); im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else: nh = int(w / t); im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    im = im.resize((1920, 1080), Image.LANCZOS)
    im = ImageEnhance.Brightness(im).enhance(0.9)
    fn = f"photo_{i:02d}.jpg"
    im.save(os.path.join(out_dir, fn), quality=90, optimize=True, progressive=False)
    items.append({'name': p['name'], 'file': 'photos/' + fn})
    th = im.resize((384, 216)); ImageDraw.Draw(th).text((8, 6), f"{i:02d} {p['name']}", fill=(255, 255, 0))
    thumbs.append(th); print('ok', fn, p['name'])
json.dump(items, open(os.path.join(out_dir, 'index.json'), 'w'), indent=1)
cols = 5; rows = (len(thumbs) + cols - 1) // cols
sheet = Image.new('RGB', (cols * 384, max(1, rows) * 216))
for k, th in enumerate(thumbs): sheet.paste(th, ((k % cols) * 384, (k // cols) * 216))
sheet.save(os.path.join(root, '..', 'photo-sheet.jpg'), quality=85)
print(len(items), 'photos')
