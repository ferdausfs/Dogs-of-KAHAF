#!/usr/bin/env python3
"""WCAG contrast calculator for the Phase 0 color revision (Guardian Shield One UI 8)."""
def srgb_to_lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 * 255 / 255 else ((c + 0.055) / 1.055) ** 2.4

def lum(hexstr):
    h = hexstr.lstrip('#')
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    def f(v):
        v /= 255.0
        return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)

def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

def grade(r):
    if r >= 7: return 'AAA'
    if r >= 4.5: return 'AA'
    if r >= 3: return 'AA-large'
    return 'FAIL'

def composite_over(fg_hex, alpha, bg_hex='#000000'):
    f = fg_hex.lstrip('#'); b = bg_hex.lstrip('#')
    out = ''.join(f'{round(alpha*int(f[i:i+2],16) + (1-alpha)*int(b[i:i+2],16)):02X}' for i in (0,2,4))
    return '#'+out

# ---------- REVISED TOKENS (Phase 0) ----------
T = {
 'bg':'#000000','bg_elev':'#0D0E12','inset':'#17181D',
 'surface':'#26272C','surface_high':'#303138','surface_highest':'#3A3C44',
 'text':'#F5F5F5','text_sec':'#A8A8A8','text_ter':'#7A7A7A',
 'accent':'#1A55CC','accent_bright':'#8FBDFF','on_accent':'#FFFFFF',
 'accent_container':'#0B2547','on_accent_container':'#D8E8FF','accent_soft':'#243B63',
 'error':'#C0353B','error_bright':'#FF9285','error_container':'#2E1315','on_error_container':'#FFD0CC',
 'warning':'#D6992A','warning_bright':'#FFC14D','warning_container':'#2F230C','on_warning_container':'#FFE2A8',
 'success':'#2E9B64','success_bright':'#6BCF97',
}
# ---------- OLD TOKENS (approved v3.1.2 baseline) ----------
O = {
 'bg':'#000000','bg_elev':'#121212','inset':'#1A1A1A',
 'surface':'#2A2A2A','surface_high':'#333333','surface_highest':'#3C3C3C',
 'text':'#F5F5F5','text_sec':'#A8A8A8','text_ter':'#7A7A7A',
 'accent':'#1E64D8','accent_bright':'#82B1FF','on_accent':'#FFFFFF',
 'accent_container':'#0D2A52','on_accent_container':'#D4E5FF',
 'error':'#C44747','error_bright':'#FF8A80','error_container':'#3A1A1A','on_error_container':'#FFD0CC',
 'warning':'#E0A12E','warning_bright':'#FFC14D','warning_container':'#3A2C10','on_warning_container':'#FFE2A8',
}

pairs = [
 ('text on bg',               'text','bg'),
 ('text on surface',          'text','surface'),
 ('text on surface_high',     'text','surface_high'),
 ('text on surface_highest',  'text','surface_highest'),
 ('text on inset',            'text','inset'),
 ('text_sec on bg',           'text_sec','bg'),
 ('text_sec on surface',      'text_sec','surface'),
 ('text_sec on surface_high', 'text_sec','surface_high'),
 ('text_sec on surface_highest','text_sec','surface_highest'),
 ('text_sec on inset',        'text_sec','inset'),
 ('text_ter on surface (microcopy)', 'text_ter','surface'),
 ('on_accent on accent (CTA fill)',   'on_accent','accent'),
 ('accent_bright on surface',         'accent_bright','surface'),
 ('accent_bright on surface_highest', 'accent_bright','surface_highest'),
 ('accent_bright on inset',           'accent_bright','inset'),
 ('accent_bright on accent_soft',     'accent_bright','accent_soft'),
 ('accent_bright on bg',              'accent_bright','bg'),
 ('on_accent_container on accent_container', 'on_accent_container','accent_container'),
 ('on_error on error (danger fill)',  'on_accent','error'),
 ('error_bright on error_container',  'error_bright','error_container'),
 ('error_bright on surface_highest',  'error_bright','surface_highest'),
 ('warning_bright on warning_container','warning_bright','warning_container'),
 ('warning_bright on surface',        'warning_bright','surface'),
 ('on_warning_container on warning_container','on_warning_container','warning_container'),
 ('success_bright on surface',        'success_bright','surface'),
]

print(f"{'pair':44} {'OLD':>7} {'NEW':>7}  grade")
print('-'*72)
for name, f, b in pairs:
    old = ratio(O[f], O[b]) if f in O and b in O else float('nan')
    new = ratio(T[f], T[b])
    print(f"{name:44} {old:7.2f} {new:7.2f}  {grade(new)}")

print()
print('--- surface ladder perceptual steps (relative luminance) ---')
for k in ['bg','bg_elev','inset','surface','surface_high','surface_highest']:
    print(f"  {k:18} old {lum(O[k]):.4f}  new {lum(T[k]):.4f}")
print()
print('--- CIE L* ladder (r1 -> r2) ---')
def Lstar(y):
    def g(t): return 116*(t**(1/3))-16 if t > 0.008856 else 903.3*t
    return g(y)
names=['bg','bg_elev','inset','surface','surface_high','surface_highest']
for k in names:
    print(f"  {k:16} L* {Lstar(lum(O[k])):5.1f} -> {Lstar(lum(T[k])):5.1f}")
print()
print('--- solid accent_soft (#243B63) badge: accent_bright on it: %.2f ---' % ratio(T['accent_bright'], T['accent_soft']))
