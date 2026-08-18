#!/usr/bin/env python3
"""Generate self-contained One UI 8 screen mocks. Each HTML inlines its own CSS."""
from pathlib import Path

OUT = Path("/home/user/Dogs-of-KAHAF/guardian-redesign/mocks/oneui8")
OUT.mkdir(parents=True, exist_ok=True)

CSS = r"""
@import url("https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css");
@import url("https://fonts.googleapis.com/css2?family=Noto+Sans+Bengali:wght@400;500;600;700&display=swap");
:root{
  --bg:#000;--bg-elev:#121212;--surface:#2A2A2A;--surface-high:#333;--surface-highest:#3C3C3C;--inset:#1A1A1A;
  --text:#F5F5F5;--text-sec:#A8A8A8;--text-ter:#7A7A7A;
  --accent:#1E64D8;--accent-press:#1854B8;--accent-bright:#82B1FF;--on-accent:#fff;
  --accent-container:#0D2A52;--on-accent-container:#D4E5FF;--accent-soft:rgba(30,100,216,.22);
  --error:#C44747;--error-bright:#FF8A80;--error-container:#3A1A1A;--on-error-container:#FFD0CC;
  --warning:#E0A12E;--warning-bright:#FFC14D;--warning-container:#3A2C10;--on-warning-container:#FFE2A8;
  --success:#2E9B64;--success-bright:#6BCF97;
  --hairline:rgba(255,255,255,.08);--r-lg:28px;--r-xl:32px;--r-pill:999px;
  --font:"Pretendard","Segoe UI",Roboto,"Noto Sans Bengali",ui-sans-serif,system-ui,sans-serif;
  --mono:ui-monospace,"SF Mono",Menlo,Consolas,monospace;
  --ease:cubic-bezier(.22,1,.36,1);--spring:cubic-bezier(.34,1.3,.64,1);
}
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:#0a0a0a;color:var(--text);font-family:var(--font);-webkit-font-smoothing:antialiased}
body{padding:28px 18px 80px}
a{color:var(--accent-bright);text-decoration:none}
.page-h{max-width:1100px;margin:0 auto 22px}
.kicker{font-size:11px;font-weight:700;letter-spacing:.14em;text-transform:uppercase;color:var(--accent-bright);margin-bottom:8px}
.page-h h1{font-size:32px;font-weight:800;letter-spacing:-.03em;line-height:1.15}
.page-h p{color:var(--text-sec);font-size:15px;margin-top:8px;max-width:720px;line-height:1.5}
.note{margin-top:10px;padding:12px 14px;background:#161616;border-radius:16px;color:var(--text-sec);font-size:13px;line-height:1.5;max-width:720px}
.note b{color:var(--text)}
.gallery{display:flex;flex-wrap:wrap;gap:28px;justify-content:center;max-width:1400px;margin:0 auto}
.block{display:flex;flex-direction:column;align-items:center;gap:10px}
.cap{font-size:12px;font-weight:700;color:var(--text-sec);letter-spacing:.04em}
.cap em{font-style:normal;color:var(--text-ter);font-weight:500}

.phone{width:360px;height:780px;background:var(--bg);border-radius:38px;border:10px solid #1c1c1c;box-shadow:0 24px 60px rgba(0,0,0,.55);overflow:hidden;position:relative;display:flex;flex-direction:column}
.sb{height:28px;display:flex;align-items:flex-end;justify-content:space-between;padding:0 22px 3px;font-size:11px;font-weight:700;flex:0 0 auto}
.sb .ic{display:flex;gap:5px;align-items:center;font-size:10px}
.screen{flex:1;overflow:auto;padding:0 0 8px;position:relative}
.screen::-webkit-scrollbar{display:none}
.pad{padding:0 20px}
.title{font-size:40px;font-weight:800;letter-spacing:-.03em;line-height:1.08;padding:6px 20px 14px}
.title.sm{font-size:34px}
.subhead{font-size:13px;font-weight:700;color:var(--text-sec);padding:4px 4px 8px}
.bn{font-family:"Noto Sans Bengali",var(--font)}
.tnum{font-variant-numeric:tabular-nums}

.sheet{background:var(--surface);border-radius:var(--r-lg);overflow:hidden;margin-bottom:16px}
.row{display:flex;align-items:center;gap:12px;min-height:64px;padding:10px 16px;border-bottom:1px solid var(--hairline)}
.row:last-child{border-bottom:0}
.row .t{font-size:16px;font-weight:600}
.row .s{font-size:13px;color:var(--text-sec);margin-top:2px;line-height:1.35}
.chev{color:var(--text-ter);font-size:22px;font-weight:300;margin-left:auto}
.mono{font-family:var(--mono);font-size:11.5px;color:var(--text-ter)}

.sw{width:52px;height:32px;background:#4A4A4A;border-radius:999px;position:relative;flex:0 0 auto;box-shadow:inset 0 0 0 1px rgba(255,255,255,.06)}
.sw::after{content:"";width:28px;height:28px;background:#fff;border-radius:50%;position:absolute;top:2px;left:2px;box-shadow:0 1px 4px rgba(0,0,0,.35)}
.sw.on{background:var(--accent)}
.sw.on::after{left:22px}
.sw.dis{opacity:.38}

.btn{display:flex;align-items:center;justify-content:center;min-height:52px;padding:0 22px;border:0;border-radius:999px;font-family:inherit;font-size:16px;font-weight:700;cursor:default}
.btn.filled{background:var(--accent);color:#fff}
.btn.tonal{background:var(--surface-highest);color:var(--text)}
.btn.ghost{background:transparent;color:var(--accent-bright)}
.btn.danger{background:var(--error);color:#fff}
.btn.block{width:100%}
.btn.sm{min-height:40px;font-size:14px;padding:0 16px}
.btn.dis{opacity:.38}

.chip{display:inline-flex;align-items:center;gap:6px;height:36px;padding:0 14px;border-radius:999px;background:var(--inset);color:var(--text-sec);font-size:13.5px;font-weight:600}
.chip.on{background:var(--accent-soft);color:var(--accent-bright)}
.chips{display:flex;flex-wrap:wrap;gap:8px}

.badge{display:inline-flex;align-items:center;gap:6px;height:22px;padding:0 9px;border-radius:999px;font-size:11px;font-weight:700}
.badge .d{width:6px;height:6px;border-radius:50%;background:currentColor}
.badge.ok{background:var(--accent-soft);color:var(--accent-bright)}
.badge.warn{background:rgba(224,161,46,.18);color:var(--warning-bright)}
.badge.err{background:rgba(196,71,71,.2);color:var(--error-bright)}
.badge.neu{background:rgba(255,255,255,.08);color:var(--text-sec)}

.hero{border-radius:32px;padding:22px 20px 18px;margin-bottom:18px}
.hero.on{background:var(--accent-container);color:var(--on-accent-container)}
.hero.pause{background:var(--warning-container);color:var(--on-warning-container)}
.hero.off{background:var(--error-container);color:var(--on-error-container)}
.hero h2{font-size:22px;font-weight:800;letter-spacing:-.02em;line-height:1.2}
.hero p{font-size:13.5px;opacity:.78;margin-top:4px;line-height:1.4}
.iw{width:52px;height:52px;border-radius:16px;display:grid;place-items:center;margin-bottom:14px;background:rgba(130,177,255,.14)}
.hero.pause .iw{background:rgba(255,193,77,.14)}
.hero.off .iw{background:rgba(255,138,128,.14)}

.nav{flex:0 0 auto;height:64px;background:var(--bg-elev);display:flex;border-top:1px solid var(--hairline)}
.nav a{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;color:var(--text-ter);font-size:10.5px;font-weight:700}
.nav a.on{color:var(--accent-bright)}
.nav svg{width:22px;height:22px}
.gest{height:10px;display:flex;justify-content:center;align-items:flex-start;flex:0 0 auto}
.gest i{width:108px;height:4px;border-radius:4px;background:#2a2a2a}

.search{background:var(--inset);border-radius:20px;height:44px;display:flex;align-items:center;gap:10px;padding:0 14px;color:var(--text-ter);font-size:15px;margin-bottom:14px}
.fab{position:absolute;right:18px;bottom:18px;width:56px;height:56px;border-radius:18px;background:var(--accent);color:#fff;display:grid;place-items:center;box-shadow:0 8px 24px rgba(30,100,216,.35);font-size:28px;font-weight:400}
.topbar{display:flex;align-items:center;gap:8px;padding:4px 12px 0;min-height:40px}
.back{width:40px;height:40px;display:grid;place-items:center;color:var(--text);font-size:22px}

.sk{background:linear-gradient(90deg,#2a2a2a 0%,#3a3a3a 50%,#2a2a2a 100%);background-size:200% 100%;animation:sh 1.2s linear infinite;border-radius:10px}
@keyframes sh{0%{background-position:200% 0}100%{background-position:-200% 0}}
.empty{text-align:center;padding:36px 16px}
.empty h3{font-size:17px;font-weight:800;margin-top:12px}
.empty p{color:var(--text-sec);font-size:13.5px;margin-top:6px;line-height:1.5}

.keypad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding:8px 20px 6px}
.key{height:64px;border-radius:20px;background:var(--surface);display:grid;place-items:center;font-size:22px;font-weight:700}
.key.ok{background:var(--accent);color:#fff;font-size:15px}
.key.ghost{background:transparent;color:var(--text-sec);font-size:18px}
.dots{display:flex;justify-content:center;gap:12px;margin:18px 0 10px}
.dot{width:14px;height:14px;border-radius:50%;background:var(--surface-highest)}
.dot.on{background:var(--accent-bright)}

.seg{display:flex;background:var(--inset);border-radius:999px;padding:4px;margin-bottom:14px}
.seg span{flex:1;height:32px;display:grid;place-items:center;border-radius:999px;font-size:13px;font-weight:700;color:var(--text-sec)}
.seg span.on{background:var(--surface-highest);color:var(--text)}

.slider{height:8px;border-radius:8px;background:var(--inset);position:relative;margin:14px 0 6px}
.slider i{position:absolute;left:0;top:0;bottom:0;background:var(--accent);border-radius:8px}
.slider b{position:absolute;top:50%;width:22px;height:22px;border-radius:50%;background:#fff;border:5px solid var(--accent);transform:translate(-50%,-50%)}

.lockban{background:var(--warning-container);border-radius:20px;padding:12px 14px;display:flex;gap:10px;align-items:center;margin-bottom:14px;color:var(--on-warning-container);font-size:13px;font-weight:700}

.scrim{position:absolute;inset:0;background:rgba(0,0,0,.62);display:flex;align-items:center;justify-content:center;padding:18px}
.card-float{background:var(--surface-highest);border-radius:24px;padding:16px;width:100%;box-shadow:0 18px 48px rgba(0,0,0,.5)}

.footlink{text-align:center;margin-top:28px;color:var(--text-ter);font-size:13px}
.footlink a{margin:0 8px}
"""

IC = {
    "shield": '<path d="M12 3 5 6.2v5.6c0 4.6 3 8.8 7 10.2 4-1.4 7-5.6 7-10.2V6.2L12 3z"/>',
    "check": '<path d="M12 3 5 6.2v5.6c0 4.6 3 8.8 7 10.2 4-1.4 7-5.6 7-10.2V6.2L12 3z"/><path d="m8.6 12.1 2.3 2.3 4.5-4.6"/>',
    "off": '<path d="M12 3 5 6.2v5.6c0 4.6 3 8.8 7 10.2 4-1.4 7-5.6 7-10.2V6.2L12 3z"/><path d="M9 9.2 15 15.2M15 9.2 9 15.2"/>',
    "alert": '<path d="M12 3 5 6.2v5.6c0 4.6 3 8.8 7 10.2 4-1.4 7-5.6 7-10.2V6.2L12 3z"/><path d="M12 8.2v4.2"/><circle cx="12" cy="15.2" r="1" fill="currentColor" stroke="none"/>',
    "home": '<path d="M4 10.2 12 4l8 6.2V19a1.6 1.6 0 0 1-1.6 1.6H5.6A1.6 1.6 0 0 1 4 19z"/>',
    "hist": '<circle cx="12" cy="12" r="8.2"/><path d="M12 7.6V12l3 1.8"/>',
    "set": '<circle cx="12" cy="12" r="2.4"/><path d="M12 4.6v1.6M12 17.8v1.6M4.6 12h1.6M17.8 12h1.6M6.6 6.6l1.2 1.2M16.2 16.2l1.2 1.2M17.4 6.6l-1.2 1.2M7.8 16.2l-1.2 1.2"/>',
    "apps": '<rect x="4" y="4" width="6.4" height="6.4" rx="1.8"/><rect x="13.6" y="4" width="6.4" height="6.4" rx="1.8"/><rect x="4" y="13.6" width="6.4" height="6.4" rx="1.8"/><rect x="13.6" y="13.6" width="6.4" height="6.4" rx="1.8"/>',
    "key": '<circle cx="8.2" cy="12" r="3.2"/><path d="M11.4 12h8M16.4 12v-2.4M19 12v2.2"/>',
    "clock": '<circle cx="12" cy="13" r="7"/><path d="M12 9.6v3.4l2.2 1.4M9.6 3.6h4.8"/>',
    "spark": '<path d="M12 3.6 10.2 8.4l-5.2.3 4 3.5-1.3 5.1L12 14.6l4.3 2.7-1.3-5.1 4-3.5-5.2-.3z"/>',
    "lock": '<rect x="4.6" y="10.4" width="14.8" height="9.2" rx="2.2"/><path d="M8.2 10.4V7.6a3.8 3.8 0 0 1 7.6 0v2.8"/>',
    "search": '<circle cx="10.6" cy="10.6" r="6.2"/><path d="m15.2 15.2 4.4 4.4"/>',
    "acc": '<circle cx="12" cy="5" r="2"/><path d="M12 7.2v4.6M6.8 20l1.7-3.2h7l1.7 3.2M6.8 9.4 4.6 10.8M17.2 9.4l2.2 1.4"/>',
    "bell": '<path d="M6 16h12l-1.2-2.2V10a4.8 4.8 0 0 0-9.6 0v3.8z"/><path d="M10 16.2a2 2 0 0 0 4 0"/>',
    "bat": '<rect x="4" y="8" width="14" height="8" rx="2"/><path d="M18.5 10.5v3H20v-3z"/>',
    "layers": '<path d="M12 4 4.5 8.2 12 12.4l7.5-4.2z"/><path d="M4.5 12.2 12 16.4l7.5-4.2"/><path d="M4.5 15.8 12 20l7.5-4.2"/>',
}


def svg(name, color="currentColor", size=22):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="{color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">{IC[name]}</svg>'


def sb():
    return '<div class="sb"><span>9:41</span><span class="ic">●●●  LTE  84%</span></div>'


def gest():
    return '<div class="gest"><i></i></div>'


def nav(active):
    items = [
        ("home", "Home", "home.html", "home"),
        ("hist", "Activity", "activity.html", "activity"),
        ("check", "Protection", "protection.html", "protection"),
        ("set", "Settings", "settings.html", "settings"),
    ]
    bits = []
    for ic, lab, href, key in items:
        cls = "on" if key == active else ""
        col = "#82B1FF" if key == active else "#7A7A7A"
        bits.append(f'<a class="{cls}" href="{href}">{svg(ic, col)}{lab}</a>')
    return '<div class="nav">' + "".join(bits) + "</div>"


def phone(inner, active=None, extra_cls=""):
    n = nav(active) if active else ""
    return f'<div class="phone {extra_cls}">{sb()}<div class="screen">{inner}</div>{n}{gest()}</div>'


def page(title, kicker, lede, phones_html, note=""):
    n = f'<p class="note">{note}</p>' if note else ""
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} — Guardian Shield · One UI 8</title>
<style>{CSS}</style>
</head>
<body>
<div class="page-h">
  <div class="kicker">{kicker}</div>
  <h1>{title}</h1>
  <p>{lede}</p>
  {n}
</div>
<div class="gallery">
{phones_html}
</div>
<p class="footlink"><a href="index.html">← Gallery</a><a href="design-system.html">Design system</a></p>
</body>
</html>
"""


def blk(cap, html):
    return f'<div class="block">{html}<div class="cap">{cap}</div></div>'


# -------------------- HOME --------------------
def home_body(state):
    if state == "on":
        hero = f"""<div class="hero on">
          <div class="iw">{svg("check", "#82B1FF", 26)}</div>
          <h2 class="bn">সুরক্ষা সক্রিয়</h2>
          <p>Monitoring 12 events today · We're actively blocking harmful content</p>
          <div style="margin:12px 0 16px"><span class="badge ok"><span class="d"></span>Protection Active · সক্রিয়</span></div>
          <button class="btn filled block">Pause</button>
        </div>"""
        recent = """<div class="sheet">
          <div class="row"><div class="iw" style="width:40px;height:40px;margin:0;background:var(--inset)">{spark}</div>
            <div style="flex:1"><div class="t">AI Detection</div><div class="s">Instagram · 14:21</div></div>
            <span class="badge err">AI Blocked</span></div>
          <div class="row"><div class="iw" style="width:40px;height:40px;margin:0;background:var(--inset)">{key}</div>
            <div style="flex:1"><div class="t">Keyword</div><div class="s">Chrome · 13:08</div></div>
            <span class="badge neu">Keyword</span></div>
          <div class="row"><div class="iw" style="width:40px;height:40px;margin:0;background:var(--inset)">{apps}</div>
            <div style="flex:1"><div class="t">App Blocked</div><div class="s">TikTok · 11:44</div></div>
            <span class="badge err">Blocked</span></div>
        </div>""".format(spark=svg("spark", "#82B1FF", 18), key=svg("key", "#A8A8A8", 18), apps=svg("apps", "#FF8A80", 18))
    elif state == "pause":
        hero = f"""<div class="hero pause">
          <div class="iw">{svg("off", "#FFC14D", 26)}</div>
          <h2>Paused</h2>
          <p class="bn">সুরক্ষা বর্তমানে বন্ধ · Tap to resume</p>
          <div style="margin:12px 0 16px"><span class="badge warn"><span class="d"></span>Paused · বিরাম</span></div>
          <button class="btn filled block">Resume</button>
        </div>"""
        recent = """<div class="sheet">
          <div class="row"><div style="flex:1"><div class="t">AI Detection</div><div class="s">Instagram · 09:12</div></div><span class="badge err">AI Blocked</span></div>
        </div>"""
    elif state == "off":
        hero = f"""<div class="hero off">
          <div class="iw">{svg("off", "#FF8A80", 26)}</div>
          <h2>Service Off</h2>
          <p class="bn">এক্সেসিবিলিটি সার্ভিস চালু নেই</p>
          <div style="margin:12px 0 16px"><span class="badge err"><span class="d"></span>Off · বন্ধ</span></div>
          <button class="btn danger block">Enable</button>
        </div>"""
        recent = """<div class="empty">
          <div class="iw" style="margin:0 auto 0;background:var(--inset)">{sh}</div>
          <h3>No blocks yet</h3>
          <p class="bn">এখনো কোনো ব্লক নেই — নিরাপদ থাকুন!</p>
        </div>""".format(sh=svg("check", "#7A7A7A", 26))
    else:  # empty
        hero = f"""<div class="hero on">
          <div class="iw">{svg("check", "#82B1FF", 26)}</div>
          <h2 class="bn">সুরক্ষা সক্রিয়</h2>
          <p>Monitoring 0 events today</p>
          <div style="margin:12px 0 16px"><span class="badge ok"><span class="d"></span>Protection Active · সক্রিয়</span></div>
          <button class="btn filled block">Pause</button>
        </div>"""
        recent = """<div class="empty">
          <div class="iw" style="margin:0 auto;background:var(--inset)">{sh}</div>
          <h3>No blocks yet</h3>
          <p class="bn">এখনো কোনো ব্লক নেই — নিরাপদ থাকুন!</p>
        </div>""".format(sh=svg("check", "#7A7A7A", 26))

    stats = """<div class="sheet">
      <div class="row"><div style="flex:1"><div class="s">Blocked today · মোট ব্লক</div><div class="t tnum" style="font-size:22px">12</div></div>
        <div style="flex:1"><div class="s">AI detections · AI বিশ্লেষণ</div><div class="t tnum" style="font-size:22px">7</div></div>
        <div style="flex:1"><div class="s">Time protected</div><div class="t tnum" style="font-size:18px">2h 24m</div></div>
      </div>
    </div>"""
    if state == "empty":
        stats = """<div class="sheet">
      <div class="row"><div style="flex:1"><div class="s">Blocked today</div><div class="t tnum" style="font-size:22px">0</div></div>
        <div style="flex:1"><div class="s">AI detections</div><div class="t tnum" style="font-size:22px">0</div></div>
        <div style="flex:1"><div class="s">Time protected</div><div class="t tnum" style="font-size:18px">0h 0m</div></div>
      </div>
    </div>"""

    qa = f"""<div class="subhead pad">Quick actions · দ্রুত পদক্ষেপ</div>
    <div class="pad"><div class="sheet">
      <div class="row">{svg("apps","#82B1FF",20)}<div style="flex:1"><div class="t">App Blocking</div><div class="s bn">১২টি ব্লক করা</div></div><span class="chev">›</span></div>
      <div class="row">{svg("key","#82B1FF",20)}<div style="flex:1"><div class="t">Keywords</div><div class="s bn">৮টি কিওয়ার্ড</div></div><span class="chev">›</span></div>
      <div class="row">{svg("check","#82B1FF",20)}<div style="flex:1"><div class="t">Whitelist</div><div class="s bn">৩টি অ্যাপ · filter in App list</div></div><span class="chev">›</span></div>
    </div></div>"""

    see = '<div class="pad" style="display:flex;justify-content:space-between;align-items:center"><div class="subhead" style="padding:0">Today’s activity · সাম্প্রতিক ব্লক</div><span style="color:var(--accent-bright);font-size:13px;font-weight:700">See all</span></div>'
    return f'<div class="title">Home</div><div class="pad">{hero}{stats}</div>{qa}{see}<div class="pad">{recent}</div>'


# -------------------- ACTIVITY --------------------
def activity_body(state):
    chips = '<div class="chips" style="margin-bottom:12px"><span class="chip on">All <span>12</span></span><span class="chip">AI</span><span class="chip">Keyword</span><span class="chip">App</span><span class="chip">Schedule</span></div>'
    leftover = '<div class="seg"><span class="on">Day</span><span>Week</span><span>Month</span></div>'
    if state == "loaded":
        body = leftover + chips + f"""<div style="color:var(--text-ter);font-size:12px;margin:-6px 0 12px">12 events</div>
        <div class="sheet">
          <div class="row">{svg("spark","#82B1FF",18)}<div style="flex:1"><div class="t">AI Detection</div><div class="s">instagram · 14:21</div></div><span class="badge err">AI Blocked</span></div>
          <div class="row">{svg("key","#A8A8A8",18)}<div style="flex:1"><div class="t">Keyword</div><div class="s">chrome · 13:08</div></div><span class="badge neu">Keyword</span></div>
          <div class="row">{svg("apps","#FF8A80",18)}<div style="flex:1"><div class="t">App Blocked</div><div class="s">tiktok · 11:44</div></div><span class="badge err">Blocked</span></div>
          <div class="row">{svg("clock","#A8A8A8",18)}<div style="flex:1"><div class="t">Scheduled</div><div class="s">youtube · 22:05</div></div><span class="badge neu">Schedule</span></div>
          <div class="row">{svg("alert","#FF8A80",18)}<div style="flex:1"><div class="t">Tamper Attempt</div><div class="s">settings · 18:02</div></div><span class="badge err">Blocked</span></div>
        </div>"""
    elif state == "empty":
        body = leftover + chips + f"""<div class="empty">
          <div class="iw" style="margin:0 auto;background:var(--inset)">{svg("hist","#7A7A7A",26)}</div>
          <h3>No events to show</h3>
          <p class="bn">দেখানোর মতো কিছু নেই</p>
        </div>"""
    else:
        body = leftover + chips + """<div class="sheet" style="padding:14px">
          <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px"><div class="sk" style="width:40px;height:40px;border-radius:14px"></div><div style="flex:1"><div class="sk" style="height:12px;width:70%;margin-bottom:8px"></div><div class="sk" style="height:10px;width:40%"></div></div></div>
          <div class="sk" style="height:56px;border-radius:16px;margin-bottom:10px"></div>
          <div class="sk" style="height:56px;border-radius:16px;margin-bottom:10px"></div>
          <div class="sk" style="height:56px;border-radius:16px"></div>
        </div>"""
    return f'<div class="topbar"><div class="back">‹</div></div><div class="title">Activity</div><div class="pad">{body}</div>'


# -------------------- PROTECTION --------------------
def prot_body(state):
    if state == "on":
        hero = f'<div class="hero on"><div class="iw">{svg("check","#82B1FF",26)}</div><h2>All shields active</h2><p>You are fully protected<br><span class="bn">সুরক্ষা সক্রিয় · ৫টি মডিউল সক্রিয়</span></p><div style="margin-top:12px"><span class="badge ok"><span class="d"></span>Active · সক্রিয়</span></div></div>'
        badge = '<span class="badge ok">On</span>'
    elif state == "pause":
        hero = f'<div class="hero pause"><div class="iw">{svg("off","#FFC14D",26)}</div><h2>Protection Paused</h2><p>Protection is paused<br><span class="bn">বিরাম</span></p><div style="margin-top:12px"><span class="badge warn"><span class="d"></span>Paused · বিরাম</span></div></div>'
        badge = '<span class="badge warn">Paused</span>'
    else:
        hero = f'<div class="hero off"><div class="iw">{svg("off","#FF8A80",26)}</div><h2>Protection Off</h2><p>Accessibility service not running<br><span class="bn">সার্ভিস বন্ধ</span></p><div style="margin-top:12px"><span class="badge err"><span class="d"></span>Off · বন্ধ</span></div></div>'
        badge = '<span class="badge err">Off</span>'
    mods = f"""<div class="subhead">Modules · মডিউল</div>
    <div class="sheet">
      <div class="row">{svg("spark","#82B1FF",20)}<div style="flex:1"><div class="t">AI Content Detection</div><div class="s">NSFW detection · 3-strike system</div></div>{badge}<span class="chev">›</span></div>
      <div class="row">{svg("apps","#82B1FF",20)}<div style="flex:1"><div class="t">App Blocking</div><div class="s bn">অ্যাপ ব্লকিং · whitelist</div></div>{badge}<span class="chev">›</span></div>
      <div class="row">{svg("key","#82B1FF",20)}<div style="flex:1"><div class="t">Keywords</div><div class="s bn">কিওয়ার্ড ফিল্টার · regex</div></div>{badge}<span class="chev">›</span></div>
      <div class="row">{svg("clock","#82B1FF",20)}<div style="flex:1"><div class="t">Schedule Rules</div><div class="s bn">সময়সূচী রুলস · প্রতিদিন</div></div>{badge}<span class="chev">›</span></div>
      <div class="row">{svg("acc","#82B1FF",20)}<div style="flex:1"><div class="t">Accessibility</div><div class="s">Content monitoring engine</div></div>{badge}<span class="chev">›</span></div>
    </div>"""
    return f'<div class="title">Protection</div><div class="pad">{hero}{mods}</div>'


# -------------------- SETTINGS --------------------
def settings_body(locked=False):
    ban = ""
    dis = " dis" if locked else ""
    on = "" if locked else " on"
    if locked:
        ban = '<div class="lockban">{ic} Commitment Lock active — 2d 14:05</div>'.format(ic=svg("lock", "#FFC14D", 18))
    return f"""<div class="topbar"><div class="back">‹</div></div>
    <div class="title">Settings</div>
    <div class="pad">
      {ban}
      <div class="subhead">Protection</div>
      <div class="sheet">
        <div class="row">{svg("key","#82B1FF",20)}<div style="flex:1"><div class="t">Keyword Filter</div><div class="s bn">কিওয়ার্ড ফিল্টার</div></div><div class="sw{on}{dis}"></div></div>
        <div class="row">{svg("spark","#82B1FF",20)}<div style="flex:1"><div class="t">AI Content Detection</div><div class="s">On-device · 3-strike</div></div><div class="sw{on}{dis}"></div></div>
      </div>
      <div class="subhead">Unlock delay · আনলক বিলম্ব</div>
      <div class="sheet" style="padding:16px">
        <div style="display:flex;justify-content:space-between"><div class="t">Unlock Delay</div><span class="badge neu tnum">30s</span></div>
        <div class="slider"><i style="width:24%"></i><b style="left:24%"></b></div>
        <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--text-ter)"><span>5s</span><span>120s</span></div>
      </div>
      <div class="subhead">Temp block duration</div>
      <div class="sheet" style="padding:16px">
        <div class="t">সাময়িক ব্লক</div>
        <p class="s bn" style="margin:6px 0 12px">AI বারবার detect করলে app টি এই সময়ের জন্য block হবে (৩টি স্ট্রাইক = temp block)</p>
        <div class="chips"><span class="chip on">15 min</span><span class="chip">30 min</span><span class="chip">1 ঘণ্টা</span></div>
      </div>
      <div class="subhead">Blocking rules</div>
      <div class="sheet">
        <div class="row">{svg("apps","#82B1FF",20)}<div class="t">App List</div><span class="chev">›</span></div>
        <div class="row">{svg("key","#82B1FF",20)}<div class="t">Keywords</div><span class="chev">›</span></div>
        <div class="row">{svg("clock","#82B1FF",20)}<div class="t">Schedule</div><span class="chev">›</span></div>
        <div class="row">{svg("acc","#82B1FF",20)}<div class="t">Permission Health</div><span class="chev">›</span></div>
        <div class="row">{svg("lock","#82B1FF",20)}<div class="t">Commitment Lock · কমিটেড লক</div><span class="chev">›</span></div>
      </div>
      <div class="subhead">AI · AI মডেল</div>
      <div class="sheet" style="padding:16px">
        <div style="display:flex;justify-content:space-between"><div><div class="t">AI Sensitivity</div><div class="s">Ultimate · 0.72</div></div><span class="badge neu tnum">0.72</span></div>
        <div class="slider"><i style="width:64%"></i><b style="left:64%"></b></div>
        <p class="s" style="margin-bottom:10px">Lower = fewer false positives · Higher = stricter blocking</p>
        <div class="chips"><span class="chip">1 · Sensitive</span><span class="chip on">2 · Balanced</span><span class="chip">3 · Strict</span><span class="chip">4 · Very strict</span></div>
      </div>
      <div class="sheet" style="padding:16px">
        <div class="t">Legacy Model</div>
        <div class="s" style="margin:4px 0 12px">✗ Missing</div>
        <div style="display:flex;gap:8px"><button class="btn tonal sm" style="flex:1">Import</button><button class="btn tonal sm" style="flex:1">Remove</button></div>
      </div>
      <div class="subhead">Security</div>
      <div class="sheet">
        <div class="row">{svg("lock","#82B1FF",20)}<div class="t">Change PIN</div><span class="chev">›</span></div>
      </div>
      <p style="text-align:center;color:var(--text-ter);font-size:11px;letter-spacing:.08em;margin:8px 0 20px">GUARDIAN SHIELD · ON-DEVICE · PRIVATE</p>
    </div>"""


# -------------------- APP LIST --------------------
def applist_body(state):
    hero = f"""<div class="sheet" style="padding:16px;margin-bottom:14px">
      <div style="display:flex;align-items:center;gap:12px">
        <div style="flex:1"><div class="t">App Blocking</div><div class="s bn">অনুপযুক্ত অ্যাপ ব্লক · Block inappropriate apps</div></div>
        <div class="sw {'on' if state!='locked' else 'on dis'}"></div>
      </div>
    </div>"""
    chips = '<div class="chips" style="margin-bottom:12px"><span class="chip">Blocked</span><span class="chip on">All</span><span class="chip">Whitelisted</span></div>'
    search = f'<div class="search">{svg("search","#7A7A7A",18)} Search apps… অ্যাপ খুঁজুন</div>'
    lock = '<div class="lockban">{ic} Commitment Lock active — editing disabled</div>'.format(ic=svg("lock", "#FFC14D", 18)) if state == "locked" else ""
    if state == "loading":
        body = search + """<div class="sheet" style="padding:14px">
          <div class="sk" style="height:56px;border-radius:16px;margin-bottom:10px"></div>
          <div class="sk" style="height:56px;border-radius:16px;margin-bottom:10px"></div>
          <div class="sk" style="height:56px;border-radius:16px"></div>
        </div>"""
    elif state == "empty":
        body = search + f'<div class="empty"><div class="iw" style="margin:0 auto;background:var(--inset)">{svg("apps","#7A7A7A",26)}</div><h3>No apps match</h3><p>Try another filter or search.</p></div>'
    else:
        def app_ic(letter):
            return f'<div style="width:40px;height:40px;border-radius:12px;background:#3a3a3a;display:grid;place-items:center;font-size:15px;font-weight:800;color:#F5F5F5">{letter}</div>'
        body = search + f"""<div class="sheet">
          <div class="row">{app_ic("C")}
            <div style="flex:1"><div class="t">Chrome</div><div class="mono">com.android.chrome</div></div>
            <span class="badge err">BLOCKED</span><div class="sw on"></div></div>
          <div class="row">{app_ic("I")}
            <div style="flex:1"><div class="t">Instagram</div><div class="mono">com.instagram.android</div></div>
            <span class="badge err">BLOCKED</span><div class="sw on"></div></div>
          <div class="row">{app_ic("W")}
            <div style="flex:1"><div class="t">WhatsApp</div><div class="mono">com.whatsapp</div></div>
            <span class="badge ok">ALLOWED</span><div class="sw"></div></div>
          <div class="row">{app_ic("Y")}
            <div style="flex:1"><div class="t">YouTube</div><div class="mono">com.google.android.youtube</div></div>
            <span class="badge neu">WHITELIST</span><div class="sw"></div></div>
        </div>"""
    return f'<div class="topbar"><div class="back">‹</div></div><div class="title sm">App blocking</div><div class="pad">{lock}{hero}{chips}{body}</div>'


# -------------------- KEYWORDS / SCHEDULE --------------------
def keywords_body(state):
    lock = '<div class="lockban">{ic} Commitment Lock active — editing disabled</div>'.format(ic=svg("lock", "#FFC14D", 18)) if state == "locked" else ""
    fab = "" if state == "dialog" else '<div class="fab">+</div>'
    if state == "empty":
        list_ = f'<div class="empty"><div class="iw" style="margin:0 auto;background:var(--inset)">{svg("key","#7A7A7A",26)}</div><h3>No keywords yet</h3><p class="bn">কোনো কিওয়ার্ড নেই। + ট্যাপ করে যোগ করুন।</p></div>'
    else:
        list_ = f"""<div class="sheet">
          <div class="row">{svg("key","#82B1FF",18)}<div style="flex:1"><div class="t">porn</div></div><span class="badge neu">PLAIN</span></div>
          <div class="row">{svg("key","#82B1FF",18)}<div style="flex:1"><div class="t">xxx</div></div><span class="badge neu">PLAIN</span></div>
          <div class="row">{svg("key","#82B1FF",18)}<div style="flex:1"><div class="t">\\bnsfw\\b</div></div><span class="badge ok">REGEX</span></div>
        </div>"""
    dlg = ""
    if state == "dialog":
        dlg = f"""<div class="scrim"><div class="card-float">
          <div class="t" style="margin-bottom:12px">Add Keyword</div>
          <div class="search" style="margin:0 0 12px">Enter keyword</div>
          <div class="row" style="padding:8px 0;border:0"><div style="flex:1"><div class="t">Regex</div></div><div class="sw"></div></div>
          <div style="display:flex;gap:8px;margin-top:8px"><button class="btn tonal" style="flex:1">Cancel</button><button class="btn filled" style="flex:1">Save</button></div>
        </div></div>"""
    return f'<div class="topbar"><div class="back">‹</div></div><div class="title">Keywords</div><div class="pad">{lock}{list_}</div>{fab}{dlg}'


def schedule_body(state):
    lock = '<div class="lockban">{ic} Commitment Lock active — editing disabled</div>'.format(ic=svg("lock", "#FFC14D", 18)) if state == "locked" else ""
    fab = "" if state == "dialog" else '<div class="fab">+</div>'
    if state == "empty":
        list_ = f'<div class="empty"><div class="iw" style="margin:0 auto;background:var(--inset)">{svg("clock","#7A7A7A",26)}</div><h3>No schedules yet</h3><p class="bn">কোনো সময়সূচী নেই। + ট্যাপ করে যোগ করুন।</p></div>'
    else:
        list_ = f"""<div class="sheet">
          <div class="row">{svg("clock","#82B1FF",18)}<div style="flex:1"><div class="t">instagram</div><div class="s">22:00 – 06:00 · Mon–Fri</div></div><span style="color:var(--accent-bright);font-size:13px;font-weight:700">Edit</span></div>
          <div class="row">{svg("clock","#82B1FF",18)}<div style="flex:1"><div class="t">youtube</div><div class="s">23:00 – 07:00 · Sun–Sat</div></div><span style="color:var(--accent-bright);font-size:13px;font-weight:700">Edit</span></div>
        </div>"""
    dlg = ""
    if state == "dialog":
        dlg = f"""<div class="scrim"><div class="card-float">
          <div class="t" style="margin-bottom:12px">Add Schedule</div>
          <div class="search" style="margin:0 0 12px">com.instagram.android</div>
          <div class="row" style="padding:8px 0"><div style="flex:1"><div class="s">Start</div><div class="t tnum">22:00</div></div><div style="flex:1"><div class="s">End</div><div class="t tnum">06:00</div></div></div>
          <div class="chips" style="margin:8px 0 14px"><span class="chip">Sun</span><span class="chip on">Mon</span><span class="chip on">Tue</span><span class="chip on">Wed</span><span class="chip on">Thu</span><span class="chip on">Fri</span><span class="chip">Sat</span></div>
          <div style="display:flex;gap:8px"><button class="btn tonal" style="flex:1">Cancel</button><button class="btn filled" style="flex:1">Save</button></div>
        </div></div>"""
    return f'<div class="topbar"><div class="back">‹</div></div><div class="title sm">Schedule</div><div class="pad">{lock}{list_}</div>{fab}{dlg}'


# -------------------- COMMITMENT --------------------
def commit_body(state):
    if state == "unlocked":
        inner = f"""<div class="hero on">
          <div class="iw">{svg("lock","#82B1FF",26)}</div>
          <h2>Choose a duration · সময় নির্বাচন</h2>
          <p class="bn">একবার লক করলে লক শেষ না হওয়া পর্যন্ত খোলা যাবে না</p>
        </div>
        <div class="sheet">
          <div class="row"><div class="t">1 day · ১ দিন</div><span class="chev">›</span></div>
          <div class="row"><div class="t">3 days · ৩ দিন</div><span class="chev">›</span></div>
          <div class="row"><div class="t">7 days · ৭ দিন</div><span class="chev">›</span></div>
          <div class="row"><div class="t">15 days · ১৫ দিন</div><span class="chev">›</span></div>
          <div class="row"><div class="t">30 days · ৩০ দিন</div><span class="chev">›</span></div>
        </div>
        <p class="s bn" style="padding:4px">ট্যাপ করলেই নিশ্চিত করার ডায়ালগ আসবে · Tap a duration to confirm — ফেরানো যাবে না</p>"""
    elif state == "locked":
        inner = f"""<div class="hero pause" style="text-align:center">
          <div class="iw" style="margin:0 auto 14px">{svg("lock","#FFC14D",26)}</div>
          <h2>Commitment Lock active</h2>
          <p class="bn">সেটিংস পরিবর্তন বন্ধ</p>
          <div class="tnum" style="font-size:40px;font-weight:800;letter-spacing:-.03em;margin:12px 0 4px">2d 14:05</div>
          <div style="font-size:12px;opacity:.7">ends Fri, Aug 21 · 09:41</div>
        </div>
        <div class="sheet" style="padding:16px">
          <p class="s" style="margin-bottom:14px">Unlock requests are limited — cooldown applies.</p>
          <button class="btn tonal block">Request Unlock</button>
        </div>"""
    else:
        inner = f"""<div class="hero pause" style="text-align:center">
          <div class="iw" style="margin:0 auto 14px">{svg("clock","#FFC14D",26)}</div>
          <h2>Cooldown</h2>
          <p>Try again after the cooldown ends.</p>
          <div class="tnum" style="font-size:40px;font-weight:800;letter-spacing:-.03em;margin:12px 0 4px">00:47</div>
        </div>
        <div class="sheet" style="padding:16px">
          <button class="btn tonal block dis">Request Unlock</button>
        </div>"""
    return f'<div class="topbar"><div class="back">‹</div></div><div class="title sm">Commitment</div><div class="pad">{inner}</div>'


# -------------------- PERMISSIONS --------------------
def perm_row(name, sub, ic, granted):
    trail = '<span class="badge ok">GRANTED</span>' if granted else '<button class="btn tonal sm">FIX</button>'
    return f'<div class="row">{svg(ic,"#82B1FF" if granted else "#FF8A80",20)}<div style="flex:1"><div class="t">{name}</div><div class="s">{sub}</div></div>{trail}</div>'


def perm_body(state):
    g = state == "granted"
    mix = state == "mixed"
    rows = [
        ("Accessibility Service", "core engine · এক্সেসিবিলিটি সার্ভিস", "acc", g or mix),
        ("Usage Stats Access", "ইউসেজ স্ট্যাটস", "hist", g),
        ("Display Over Other Apps", "block overlays need this · ব্লক ওভারলে", "layers", g or mix),
        ("Notification Permission", "নোটিফিকেশন পারমিশন", "bell", g),
        ("Battery Optimization", "keep service alive · সার্ভিস চালু রাখে", "bat", g),
    ]
    body = '<div class="sheet">' + "".join(perm_row(*r) for r in rows) + "</div>"
    cta = "" if g else '<button class="btn filled block" style="margin-top:8px">Fix All Critical</button>'
    return f'<div class="topbar"><div class="back">‹</div></div><div class="title sm">Permissions</div><div class="pad">{body}{cta}</div>'


# -------------------- PIN --------------------
def keypad(ok=True):
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"]
    cells = []
    for k in keys:
        cls = "key"
        if k == "OK":
            cls += " ok"
        if k == "⌫":
            cls += " ghost"
        cells.append(f'<div class="{cls}">{k}</div>')
    return '<div class="keypad">' + "".join(cells) + "</div>"


def pin_body(kind):
    if kind == "setup":
        title, sub, filled, err = "Set your PIN", "Enter 4–6 digit PIN", 0, ""
    elif kind == "confirm":
        title, sub, filled, err = "Set your PIN", "Re-enter to confirm", 4, ""
    elif kind == "mismatch":
        title, sub, filled, err = "Set your PIN", "PINs do not match", 3, '<p style="color:var(--error-bright);text-align:center;font-size:13px;font-weight:700">PINs do not match</p>'
    elif kind == "verify":
        title, sub, filled, err = "Enter PIN", "পিন দিন · Guardian Shield is locked", 0, ""
    elif kind == "wrong":
        title, sub, filled, err = "Enter PIN", "Wrong PIN — 3 attempts left", 4, '<p style="color:var(--error-bright);text-align:center;font-size:13px;font-weight:700">Wrong PIN — 3 attempts left</p>'
    else:
        title, sub, filled, err = "Enter PIN", "Too many attempts. Try again in 24 seconds.", 0, '<p style="color:var(--error-bright);text-align:center;font-size:13px;font-weight:700">Too many attempts. Try again in 24 seconds.</p>'
    dots = "".join(f'<div class="dot{" on" if i<filled else ""}"></div>' for i in range(6))
    extra = ""
    if kind in ("verify", "wrong", "lockout"):
        extra = '<p style="text-align:center;color:var(--text-ter);font-size:12px;margin-top:8px">Forgot PIN? Reinstall is required to reset</p>'
    return f"""<div class="title sm" style="text-align:center;padding-top:20px">{title}</div>
    <p style="text-align:center;color:var(--text-sec);font-size:14px" class="bn">{sub}</p>
    <div class="dots">{dots}</div>
    {err}
    <div style="flex:1"></div>
    {keypad()}
    {extra}"""


# -------------------- OVERLAYS --------------------
def overlay_body(kind):
    if kind == "ai":
        mark = '<button class="btn ghost block">ভুল ব্লক হয়েছে? মনে রাখো, আর ব্লক হবে না</button>'
        unlock = '<button class="btn tonal block" style="margin:10px 0">Request Unlock</button>'
        banner = '<div class="lockban">🚫 Blocked for 15 minutes</div>'
        reason = "AI detected unsafe content"
        cat = "Adult Content · AI Detection"
    elif kind == "keyword":
        mark = ""
        unlock = '<button class="btn tonal block" style="margin:10px 0">Request Unlock</button>'
        banner = ""
        reason = "Keyword matched: xxx"
        cat = "Keyword · xxx"
    elif kind == "tamper":
        mark = ""
        unlock = ""
        banner = ""
        reason = "⚠️ Tampering attempt detected! Changing settings is forbidden while a Commitment Lock is active."
        cat = "Tamper Attempt"
    else:  # schedule
        mark = ""
        unlock = '<button class="btn tonal block" style="margin:10px 0">Request Unlock</button>'
        banner = ""
        reason = "Outside allowed schedule"
        cat = "Schedule · Outside hours"
    return f"""<div style="padding:20px 20px 8px;text-align:center">
      <div class="iw" style="margin:12px auto 16px;background:var(--error-container)">{svg("alert","#FF8A80",28)}</div>
      <div style="font-size:24px;font-weight:800;letter-spacing:-.02em;line-height:1.2">Sensitive Content Blocked</div>
      <p class="bn" style="color:var(--text-sec);margin:8px 0 12px;font-size:13.5px;line-height:1.45">এই কন্টেন্টটি আপনাকে সুরক্ষিত রাখতে ব্লক করা হয়েছে</p>
      <span class="badge neu mono">com.instagram.android</span>
    </div>
    <div class="pad">
      {banner}
      <div class="sheet">
        <div class="row"><div style="flex:1"><div class="s">Protection</div><div class="t">Active</div></div>
          <div style="flex:1"><div class="s">Category</div><div class="t" style="font-size:14px">{cat}</div></div>
          <div style="flex:1"><div class="s">Time</div><div class="t">Now</div></div>
        </div>
      </div>
      <div class="sheet" style="padding:14px;text-align:center"><div class="s" style="color:var(--warning-bright);font-weight:700">{reason}</div></div>
      <div style="height:12px"></div>
      <button class="btn danger block">Stay Protected</button>
      {unlock}
      {mark}
      <p style="text-align:center;color:var(--text-ter);font-size:11px;letter-spacing:.12em;margin-top:16px">STAY STRONG 💪</p>
    </div>"""


def delay_body():
    return f"""<div class="title sm" style="text-align:center;padding-top:28px">Unlock Pending</div>
    <p style="text-align:center;color:var(--text-sec)">Access will be granted in:</p>
    <div class="mono" style="text-align:center;margin:8px 0 4px"><span class="badge neu">com.instagram.android</span></div>
    <div class="tnum" style="font-size:72px;font-weight:800;letter-spacing:-.04em;text-align:center;margin:20px 0 4px">29</div>
    <p style="text-align:center;color:var(--text-ter)">seconds</p>
    <div class="pad" style="margin-top:40px"><button class="btn tonal block">Cancel</button></div>"""


def strike_card(n):
    return f"""<div class="scrim" style="align-items:flex-start;padding-top:72px">
      <div class="card-float" style="display:flex;gap:12px;align-items:flex-start">
        <div class="iw" style="width:44px;height:44px;margin:0;background:var(--warning-container)">{svg("alert","#FFC14D",20)}</div>
        <div style="flex:1">
          <div style="font-size:11px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--warning-bright)">STRIKE WARNING</div>
          <div class="t bn" style="margin-top:4px">সতর্কতা {n}/3</div>
          <p class="s bn" style="margin-top:4px">পরের বার শনাক্ত হলে অ্যাপটি সাময়িকভাবে ব্লক হবে</p>
          <div style="margin-top:10px;color:var(--text-sec);font-size:12.5px;font-weight:700">Not sensitive</div>
        </div>
      </div>
    </div>"""


def blocked_detail():
    return f"""<div class="topbar"><div class="back">‹</div><div style="font-weight:800">Blocked Content</div></div>
    <div class="pad">
      <div class="hero off" style="text-align:center">
        <div class="iw" style="margin:0 auto 12px">{svg("alert","#FF8A80",26)}</div>
        <h2>Content Blocked</h2>
        <p>You are safe — Guardian Shield protected you</p>
      </div>
      <div class="sheet">
        <div class="row"><div style="flex:1"><div class="s">App</div><div class="t">Instagram</div></div></div>
        <div class="row"><div style="flex:1"><div class="s">Blocked Category</div><div class="t">AI Detection</div></div></div>
        <div class="row"><div style="flex:1"><div class="s">Content source</div><div class="mono">com.instagram.android</div></div></div>
        <div class="row"><div style="flex:1"><div class="s">Time</div><div class="t">14:21</div></div></div>
      </div>
      <div class="subhead">What you can do</div>
      <div class="sheet">
        <div class="row"><div class="t">Stay Focused · মনোযোগ ধরে রাখুন</div><span class="chev">›</span></div>
        <div class="row"><div class="t">View Activity · অ্যাক্টিভিটি দেখুন</div><span class="chev">›</span></div>
        <div class="row"><div class="t">Add to Whitelist · হোয়াইটলিস্ট</div><span class="chev">›</span></div>
      </div>
      <button class="btn danger block">Back to App</button>
      <button class="btn ghost block">Report This Content</button>
    </div>"""


# -------------------- PROMPT / REEL / ONBOARDING / ICON / DIALOGS --------------------
def prompt_body():
    return f"""<div style="padding:48px 24px 24px;text-align:center">
      <div class="iw" style="margin:0 auto 18px;width:72px;height:72px;background:var(--error-container)">{svg("off","#FF8A80",32)}</div>
      <div style="font-size:22px;font-weight:800;line-height:1.25" class="bn">⚠️ সুরক্ষা বন্ধ হয়ে গেছে!</div>
      <p class="bn" style="color:var(--text-sec);margin:14px auto 0;max-width:300px;line-height:1.55;font-size:14.5px">Guardian Shield এর Accessibility Service বন্ধ করা হয়েছে। harmful content থেকে সুরক্ষিত থাকতে এখনই চালু করুন।</p>
    </div>
    <div class="pad" style="margin-top:auto;padding-bottom:12px">
      <button class="btn danger block">Accessibility Service চালু করুন</button>
      <p style="text-align:center;color:var(--text-ter);font-size:12px;margin-top:12px">Settings → Accessibility → Guardian Shield → Enable</p>
    </div>"""


def reel_body():
    return f"""<div style="padding:40px 24px 16px;text-align:center">
      <div class="iw" style="margin:0 auto 16px;width:72px;height:72px;background:var(--accent-container)">{svg("spark","#82B1FF",30)}</div>
      <div class="bn" style="font-size:28px;font-weight:800;letter-spacing:-.02em">একটু থামো, ভাই!</div>
      <p class="bn" style="color:var(--text-sec);margin:18px 8px;line-height:1.65;font-size:14.5px">“তোমরা কুরআন পড়ো, কারণ কিয়ামতের দিন এটি তোমাদের জন্য সুপারিশকারী হবে।”<br><br>— সহীহ মুসলিম</p>
      <p class="bn" style="color:var(--text-sec);font-size:13.5px;line-height:1.55">তুমি অনেকক্ষণ ধরে রিলস দেখছো। এই মূল্যবান সময়টা কুরআন বা হাদীসে ব্যয় করো।</p>
    </div>
    <div class="pad" style="padding-bottom:12px">
      <button class="btn filled block">📖 কুরআন অ্যাপ খুলুন</button>
      <button class="btn ghost block" style="margin-top:8px">এখন নয়, ফিরে যাই</button>
    </div>"""


def onboard_body(page):
    pages = [
        ("Guardian Shield", "তোমার ডিজিটাল ঢাল", "ক্ষতিকর কন্টেন্ট থেকে নিজেকে এবং পরিবারকে রক্ষা করুন। অন-ডিভাইস AI দিয়ে স্মার্ট ব্লকিং।", "check"),
        ("যা পাচ্ছেন", "শক্তিশালী ফিচারসমূহ", "App ও Website ব্লকিং<br>AI দিয়ে NSFW detection<br>Keyword filter<br>Schedule-based রুল<br>রিলস addiction রিমাইন্ডার", "spark"),
        ("কয়েকটি অনুমতি দরকার", "Privacy-friendly · On-device", "সুরক্ষা চালু করতে আমাদের কিছু permission লাগবে:<br><br>Accessibility Service<br>Display over apps<br>Notification<br>Battery exception", "acc"),
        ("PIN দিয়ে সুরক্ষিত রাখুন", "শেষ ধাপ", "পরের ধাপে একটা ৪-৬ সংখ্যার PIN সেট করতে হবে। এই PIN ছাড়া কেউ Settings পাল্টাতে পারবে না।", "lock"),
    ]
    t, hi, body, ic = pages[page]
    nxt = "Get Started" if page == 3 else "Next →"
    skip = "" if page == 3 else '<div style="text-align:right;padding:8px 16px;color:var(--accent-bright);font-weight:700">Skip</div>'
    dots = "".join(
        f'<i style="width:{"18" if i==page else "8"}px;height:8px;border-radius:8px;background:{"#82B1FF" if i==page else "#3C3C3C"};display:inline-block;margin:0 3px"></i>'
        for i in range(4)
    )
    return f"""{skip}
    <div style="padding:28px 24px 12px;text-align:center;min-height:420px">
      <div class="iw" style="margin:12px auto 22px;width:88px;height:88px;border-radius:28px;background:var(--accent-container)">{svg(ic,"#82B1FF",36)}</div>
      <div style="font-size:12px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:var(--accent-bright);margin-bottom:10px">{hi}</div>
      <div class="bn" style="font-size:28px;font-weight:800;letter-spacing:-.02em;line-height:1.2">{t}</div>
      <p class="bn" style="color:var(--text-sec);margin-top:16px;line-height:1.6;font-size:15px">{body}</p>
    </div>
    <div style="text-align:center;margin:8px 0 18px">{dots}</div>
    <div class="pad" style="display:flex;gap:10px;padding-bottom:8px">
      <button class="btn tonal" style="width:56px">{'‹' if page else ' '}</button>
      <button class="btn filled" style="flex:1">{nxt}</button>
    </div>"""


def icon_body():
    return f"""<div style="padding:48px 24px;text-align:center">
      <div style="width:168px;height:168px;border-radius:40px;margin:0 auto;background:#000;display:grid;place-items:center;box-shadow:0 0 0 1px #222, 0 20px 50px rgba(0,0,0,.5)">
        <div style="width:112px;height:112px;border-radius:32px;background:var(--accent-container);display:grid;place-items:center">{svg("check","#82B1FF",56)}</div>
      </div>
      <div style="margin-top:22px;font-weight:800;font-size:18px">Guardian Shield</div>
      <p style="color:var(--text-sec);font-size:13px;margin-top:6px">Adaptive icon · monochrome uses the same mark</p>
      <div style="display:flex;gap:16px;justify-content:center;margin-top:28px">
        <div>
          <div style="width:64px;height:64px;border-radius:16px;background:#000;display:grid;place-items:center;box-shadow:0 0 0 1px #222">{svg("check","#fff",32)}</div>
          <div class="s" style="margin-top:6px">Mono</div>
        </div>
        <div>
          <div style="width:64px;height:64px;border-radius:16px;background:#111;display:grid;place-items:center;box-shadow:0 0 0 1px #222">{svg("check","#82B1FF",32)}</div>
          <div class="s" style="margin-top:6px">Themed</div>
        </div>
      </div>
      <p class="s" style="margin-top:24px">No splash Activity exists — launcher is MainActivity.</p>
    </div>"""


def dialogs_inner(kind):
    if kind == "battery":
        t, m, yes, no = "🔋 Stability Fix", "Disable battery optimization to prevent Guardian Shield from being killed in the background.", "Fix", "Later"
    elif kind == "admin":
        t, m, yes, no = "⚠️ Uninstall Protection", "Enable Device Admin to protect the app from being uninstalled.", "Enable", "Later"
    elif kind == "clear":
        t, m, yes, no = "Clear Logs", "Are you sure? This cannot be undone.", "Confirm", "Cancel"
    else:
        t, m, yes, no = "১ দিন", "একবার লক করলে লক শেষ না হওয়া পর্যন্ত খোলা যাবে না", "নিশ্চিত করুন", "বাতিল"
    return f"""<div class="scrim">
      <div class="card-float" style="max-width:300px">
        <div class="t" style="font-size:18px;margin-bottom:8px">{t}</div>
        <p class="s bn" style="line-height:1.5;margin-bottom:18px">{m}</p>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn ghost sm">{no}</button>
          <button class="btn filled sm">{yes}</button>
        </div>
      </div>
    </div>"""


def write(name, html):
    (OUT / name).write_text(html, encoding="utf-8")
    print("wrote", name, "bytes", (OUT / name).stat().st_size)


# ---- emit files ----
write("home.html", page(
    "Home / Dashboard",
    "One UI 8 · Screen 1",
    "Large title up top. Status is a display surface. Pause / Resume / Enable sit at the bottom of the hero — thumb reach. Stats and shortcuts are grouped sheets, not colorful tiles. Real copy: সুরক্ষা সক্রিয়, Pause/Resume/Enable.",
    blk("On · 12 events", phone(home_body("on"), "home"))
    + blk("Paused", phone(home_body("pause"), "home"))
    + blk("Service off", phone(home_body("off"), "home"))
    + blk("On · empty recent", phone(home_body("empty"), "home")),
    "Time protected is still the existing placeholder formula (not a real uptime clock). Whitelist row deep-links to App List. <b>txtStatKeyword</b> stays in the layout (hidden today)."
))

write("activity.html", page(
    "Activity Log",
    "One UI 8 · Screen 2",
    "Reason chips are the real filter. Day / Week / Month exist in XML but are <b>not wired</b> — shown as leftover chrome, no fake +24% chart.",
    blk("Loaded · 12 events", phone(activity_body("loaded")))
    + blk("Empty", phone(activity_body("empty")))
    + blk("Loading skeleton", phone(activity_body("loading"))),
    "No spinner. Row tap → Blocked detail. Long-press delete unchanged."
))

write("protection.html", page(
    "Protection Hub",
    "One UI 8 · Screen 3",
    "Five real modules only. Same ON / Paused / Off states as Home. Rows navigate to App List, Keywords, Schedule, Settings (AI), Settings (Accessibility).",
    blk("All shields active", phone(prot_body("on"), "protection"))
    + blk("Paused", phone(prot_body("pause"), "protection"))
    + blk("Off", phone(prot_body("off"), "protection")),
    "No DNS / VPN / browser / Strict Mode / gender module."
))

write("settings.html", page(
    "Settings",
    "One UI 8 · Screen 4",
    "Classic One UI grouped sheets. Large pill switches. Commitment Lock disables the same control list as today; navigation rows stay enabled.",
    blk("Normal", phone(settings_body(False)))
    + blk("Commitment locked", phone(settings_body(True))),
    "PIN gate still happens before this screen when a PIN is set. Gender filter strings exist with no UI — not invented here."
))

write("app-blocking.html", page(
    "App Blocking",
    "One UI 8 · Screen 5",
    "Hero toggle + All / Blocked / Whitelisted chips + search. Whitelist is a filter, not a screen. Loading uses the existing <code>AppListState.loading</code> flag.",
    blk("Loaded", phone(applist_body("loaded")))
    + blk("Empty filter", phone(applist_body("empty")))
    + blk("Loading skeleton", phone(applist_body("loading")))
    + blk("Locked", phone(applist_body("locked")))
))

write("keywords.html", page(
    "Keywords",
    "One UI 8 · Screen 6",
    "FAB sits in the thumb zone. Add-keyword dialog restyled as a One UI sheet. Regex checkbox preserved.",
    blk("Loaded", phone(keywords_body("loaded")))
    + blk("Empty", phone(keywords_body("empty")))
    + blk("Add dialog", phone(keywords_body("dialog")))
    + blk("Locked", phone(keywords_body("locked")))
))

write("schedule.html", page(
    "Schedule",
    "One UI 8 · Screen 7",
    "Same pattern as Keywords. Start/End stay tappable TextViews (time picker hosts). Day chips Sun–Sat.",
    blk("Loaded", phone(schedule_body("loaded")))
    + blk("Empty", phone(schedule_body("empty")))
    + blk("Editor dialog", phone(schedule_body("dialog")))
    + blk("Locked", phone(schedule_body("locked")))
))

write("commitment.html", page(
    "Commitment Lock",
    "One UI 8 · Screen 8",
    "Unlocked = duration list in a sheet (thumb-friendly). Locked = countdown as display, Request Unlock at the bottom. Durations unchanged: 1 / 3 / 7 / 15 / 30 days.",
    blk("Choose duration", phone(commit_body("unlocked")))
    + blk("Locked", phone(commit_body("locked")))
    + blk("Cooldown", phone(commit_body("cooldown")))
))

write("permissions.html", page(
    "Permission Health",
    "One UI 8 · Screen 9",
    "Five real rows. Granted vs FIX. Fix All Critical sits at the bottom.",
    blk("Mixed", phone(perm_body("mixed")))
    + blk("All granted", phone(perm_body("granted")))
    + blk("All missing", phone(perm_body("missing")))
))

write("pin.html", page(
    "PIN setup & verify",
    "One UI 8 · Screens 10–11",
    "Keypad lives in the lower third — the most One UI screen in the app. No biometric. No Forgot-PIN control (note is copy-only, already in strings).",
    blk("Setup · enter", phone(pin_body("setup")))
    + blk("Setup · confirm", phone(pin_body("confirm")))
    + blk("Setup · mismatch", phone(pin_body("mismatch")))
    + blk("Verify · idle", phone(pin_body("verify")))
    + blk("Verify · wrong", phone(pin_body("wrong")))
    + blk("Verify · lockout 30s", phone(pin_body("lockout")))
))

# overlays file — need a dimmed "host" behind strike cards
def phone_overlay(inner, host_note=""):
    return phone(inner)


write("overlays.html", page(
    "Overlays & interruptions",
    "One UI 8 · Screens 12–14, 19",
    "Strike-3 full block, Delay Unlock, Blocked detail, and the strike 1/2 warning card. Actions stay at the bottom. Mark False only on AI. 3.5s auto-dismiss is visualized, not changed.",
    blk("Strike-3 · AI + Mark False", phone(overlay_body("ai")))
    + blk("Keyword match", phone(overlay_body("keyword")))
    + blk("Tamper (no unlock)", phone(overlay_body("tamper")))
    + blk("Schedule", phone(overlay_body("schedule")))
    + blk("Delay Unlock", phone(delay_body()))
    + blk("Blocked detail", phone(blocked_detail()))
    + blk("Strike 1/3 + Not sensitive", phone(home_body("on") + strike_card("১"), "home"))
    + blk("Strike 2/3", phone(home_body("on") + strike_card("২"), "home")),
    "<b>Reskin only.</b> btnHome → HOME, btnUnlock → DelayUnlock, btnMarkFalse → FalsePositiveMemory. Strike card tap-dismiss + 3500ms + audit-only Not sensitive stay."
))

write("accessibility.html", page(
    "Accessibility prompt",
    "One UI 8 · Screen 16",
    "Urgent, copy verbatim (including ⚠️). Single Enable action in the thumb zone.",
    blk("Default", phone(prompt_body()))
))

write("reel.html", page(
    "Reel reminder",
    "One UI 8 · Screen 17",
    "Islamic interstitial. Real Bengali copy. Open Quran / Continue destinations unchanged.",
    blk("Default", phone(reel_body()))
))

write("onboarding.html", page(
    "Onboarding",
    "One UI 8 · Screen 18",
    "Four existing pages. Emoji chrome replaced with the outlined family. Skip hides on the last page. Next becomes Get Started. Copy stays the hardcoded Bengali from OnboardingPagerAdapter.",
    blk("1 · Guardian Shield", phone(onboard_body(0)))
    + blk("2 · Features", phone(onboard_body(1)))
    + blk("3 · Permissions", phone(onboard_body(2)))
    + blk("4 · PIN", phone(onboard_body(3)))
))

write("icon.html", page(
    "App icon",
    "One UI 8 · Screen 21",
    "Refined shield on AMOLED black. No splash Activity exists — none invented.",
    blk("Adaptive + mono", phone(icon_body()))
))

write("dialogs.html", page(
    "System dialogs",
    "One UI 8 · Screen 20",
    "Material alerts restyled as L3 sheets. Battery, Device Admin, Clear logs, Commitment confirm — same buttons and copy.",
    blk("Battery optimization", phone(home_body("on") + dialogs_inner("battery"), "home"))
    + blk("Device Admin", phone(home_body("on") + dialogs_inner("admin"), "home"))
    + blk("Clear logs", phone(activity_body("loaded") + dialogs_inner("clear")))
    + blk("Lock confirm", phone(commit_body("unlocked") + dialogs_inner("lock")))
))

# -------------------- INDEX --------------------
cards = [
    ("design-system.html", "0", "Design system", "Tokens, type, switches, motion, contrast"),
    ("home.html", "1", "Home / Dashboard", "On · Paused · Off · empty"),
    ("activity.html", "2", "Activity Log", "Loaded · empty · skeleton"),
    ("protection.html", "3", "Protection Hub", "On · Paused · Off · 5 modules"),
    ("settings.html", "4", "Settings", "Normal · Commitment locked"),
    ("app-blocking.html", "5", "App Blocking", "Loaded · empty · skeleton · locked"),
    ("keywords.html", "6", "Keywords", "Loaded · empty · dialog · locked"),
    ("schedule.html", "7", "Schedule", "Loaded · empty · dialog · locked"),
    ("commitment.html", "8", "Commitment Lock", "Picker · locked · cooldown"),
    ("permissions.html", "9", "Permission Health", "Mixed · granted · missing"),
    ("pin.html", "10–11", "PIN setup & verify", "Enter · confirm · error · lockout"),
    ("overlays.html", "12–14, 19", "Overlays", "Strike-3 · delay · detail · 1/2 card"),
    ("accessibility.html", "16", "Accessibility prompt", "Urgent copy verbatim"),
    ("reel.html", "17", "Reel reminder", "একটু থামো, ভাই!"),
    ("onboarding.html", "18", "Onboarding", "4 existing pages"),
    ("icon.html", "21", "App icon", "Adaptive + mono · no splash"),
    ("dialogs.html", "20", "Dialogs", "Battery · admin · clear · lock"),
]
card_html = []
for href, num, title, sub in cards:
    card_html.append(
        f'<a class="gcard" href="{href}"><span class="n">{num}</span><span class="t">{title}</span><span class="s">{sub}</span></a>'
    )

index = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Guardian Shield — One UI 8 mock gallery</title>
<style>
{CSS}
.wrap{{max-width:1100px;margin:0 auto}}
.ggrid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px;margin-top:22px}}
.gcard{{background:var(--surface);border-radius:24px;padding:18px 18px 16px;display:flex;flex-direction:column;gap:6px;color:var(--text);min-height:120px;transition:transform .16s var(--spring)}}
.gcard:hover{{transform:scale(1.02);background:var(--surface-high)}}
.gcard .n{{font-size:11px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:var(--accent-bright)}}
.gcard .t{{font-size:18px;font-weight:800;letter-spacing:-.02em}}
.gcard .s{{font-size:13px;color:var(--text-sec);line-height:1.4}}
.check{{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}}
.check span{{background:var(--surface);border-radius:999px;padding:6px 12px;font-size:12px;color:var(--text-sec)}}
</style>
</head>
<body>
<div class="wrap">
  <div class="kicker">Guardian Shield · One Shield · One UI 8</div>
  <h1 style="font-size:40px;font-weight:800;letter-spacing:-.03em;line-height:1.1;margin:8px 0 12px">Mock gallery</h1>
  <p style="color:var(--text-sec);max-width:720px;line-height:1.55">
    Full screen inventory from <b style="color:var(--text)">AUDIT-ONEUI8.md</b>, drawn in the approved One Shield tokens.
    Every file is self-contained (inline CSS). No Kotlin / XML has been touched.
    App name remains <b style="color:var(--text)">Guardian Shield</b>.
  </p>
  <div class="check">
    <span>XML Views + ViewBinding</span>
    <span>Real copy · EN + বাংলা</span>
    <span>No DNS / VPN / biometric</span>
    <span>Strike timing untouched</span>
    <span>Skeleton, never spinner</span>
  </div>
  <div class="ggrid">
    {''.join(card_html)}
  </div>
  <p class="note" style="margin-top:28px">
    <b>Not invented:</b> standalone whitelist, splash Activity, gender-filter panel,
    Day/Week analytics (buttons exist unbound), Forgot PIN, biometric.
    Day/Week/Month appear on Activity as leftover chrome only.
  </p>
</div>
</body>
</html>
"""
write("index.html", index)
print("done", OUT)
