"""Build step: confirm each Mixkit clip is downloadable and write assets/videos.json with working links
(720p preferred: smooth on a Fire TV Stick and light on storage). The clips themselves are NOT bundled;
the TV downloads each one the first time it's chosen."""
import json, os, urllib.request
root = os.path.dirname(os.path.abspath(__file__))
out = []
for v in json.load(open(os.path.join(root, 'videos.json'))):
    for q in ('720', '1080', '360'):
        url = f"https://assets.mixkit.co/videos/{v['id']}/{v['id']}-{q}.mp4"
        try:
            r = urllib.request.urlopen(urllib.request.Request(url, method='HEAD', headers={'User-Agent': 'Mozilla/5.0 GlassTV'}), timeout=30)
            size = int(r.headers.get('Content-Length') or 0)
            if r.status == 200 and size > 100000:
                out.append({'name': v['name'], 'url': url}); print('ok', q, f"{size/1e6:.1f}MB", v['name']); break
        except Exception as e:
            print('  no', q, v['name'], e)
    else:
        print('skip', v['name'])
dst = os.path.join(root, '..', 'app', 'src', 'main', 'assets', 'videos.json')
json.dump(out, open(dst, 'w'), indent=1)
print(len(out), 'videos')
