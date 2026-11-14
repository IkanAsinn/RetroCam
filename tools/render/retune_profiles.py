#!/usr/bin/env python3
"""One-off record of the v0.3 'less is more' retune of per-profile magnitudes (run once on the v0.2.0 JSON).
Scales: chromaticAberration x0.45, edgeSoftness x0.5, vignette.strength x0.6. Rounded to 3 decimals. plastic_120 (toy camera) vignette.strength was afterwards restored to 0.9."""
import glob, json, os, sys
d = sys.argv[1]
for f in sorted(glob.glob(os.path.join(d, '*.json'))):
    if f.endswith('index.json'): continue
    s = open(f).read(); p = json.loads(s)
    p['chromaticAberration'] = round(p['chromaticAberration'] * 0.45, 3)
    p['edgeSoftness'] = round(p['edgeSoftness'] * 0.5, 3)
    p['vignette']['strength'] = round(p['vignette']['strength'] * 0.6, 3)
    open(f, 'w').write(json.dumps(p, indent=2) + "\n")
