# -*- coding: utf-8 -*-
"""Builds all Guardian Shield "Sentinel" design-language mockups (static HTML)."""
import os
from _lib import (TOKENS as T, css, icon, statusbar, appbar, navbar, device,
                  board, page, contrast, ICONS)

OUT = os.path.dirname(os.path.abspath(__file__))

def w(name, html):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        f.write(html)
    print("wrote", name)

# ---- tiny helpers -----------------------------------------------------------
def appic(h1, h2, letter):
    return ('<div class="ico" style="background:linear-gradient(135deg,%s,%s);color:#fff;'
            'font-weight:800;font-size:15px">%s</div>' % (h1, h2, letter))

def stat(ic, iccls, val, label, delta=None, delta_up=True):
    d = ""
    if delta:
        d = '<span class="dl %s">%s</span>' % ("up" if delta_up else "", delta)
    return ('<div class="stat"><div class="ic %s">%s</div><b>%s</b><span>%s</span>%s</div>'
            % (iccls, icon(ic, 18), val, label, d))

def sw(on=True, disabled=False):
    return '<div class="sw %s" style="%s"></div>' % ("on" if on else "", "opacity:.45" if disabled else "")

def chip(label, on=False):
    return '<div class="chip %s">%s</div>' % ("on" if on else "", label)

# =============================================================================
# HOME DASHBOARD
# =============================================================================
def dashboard(state="active"):
    if state == "skeleton":
        body = (statusbar() + appbar("Guardian Shield", action="•••") +
            '<div class="scroll"><div class="pad stack">'
            '<div class="hero" style="height:300px"></div>'
            '<div class="stats"><div class="sk" style="height:86px;border-radius:20px"></div><div class="sk" style="height:86px;border-radius:20px"></div><div class="sk" style="height:86px;border-radius:20px"></div></div>'
            '<div class="grid3">' + '<div class="sk" style="height:64px;border-radius:14px"></div>'*3 + '</div>'
            '<div class="skrow"><div class="sk" style="width:40px;height:40px;border-radius:12px"></div><div style="flex:1"><div class="sk" style="height:12px;width:70%"></div><div class="sk" style="height:10px;width:45%;margin-top:6px"></div></div></div>'
            '<div class="skrow"><div class="sk" style="width:40px;height:40px;border-radius:12px"></div><div style="flex:1"><div class="sk" style="height:12px;width:60%"></div><div class="sk" style="height:10px;width:40%;margin-top:6px"></div></div></div>'
            '</div></div>' + navbar(0))
        return device(body, "<b>Skeleton (loading)</b> — shimmer placeholders, never a spinner. Binds to the same data flows; replaces the bare list while Room loads.")

    hero = ""
    if state == "active":
        hero = ('<div class="hero"><div class="in"><div class="glow"></div>'
            '<div class="mark">%s</div>'
            '<h2>সুরক্ষা সক্রিয়<small>Protection is ON</small></h2>'
            '<p>আজ ১২টি ইভেন্ট মনিটর করা হচ্ছে • actively blocking</p>'
            '<div style="margin-top:16px"><span class="btn tonal" style="display:inline-flex;width:auto;height:40px;padding:0 18px;background:rgba(233,241,236,.1);border-color:rgba(233,241,236,.16);color:var(--onpc)">%s বিরতি দিন • Pause</span></div>'
            '<div class="pill"><span class="dot"></span>Protection Active • সক্রিয়</div>'
            '</div></div>' % (icon("shield", 40), icon("block", 14)))
    elif state == "paused":
        hero = ('<div class="hero" style="background:linear-gradient(150deg,#23241f,#1c1e18 45%%,#151612);border-color:rgba(255,201,77,.25)"><div class="in"><div class="glow" style="background:radial-gradient(circle,rgba(255,201,77,.28),transparent 70%%)"></div>'
            '<div class="mark" style="background:rgba(26,22,10,.6);border-color:rgba(255,201,77,.4);color:var(--warn)">%s</div>'
            '<h2 style="color:#FFE2A8">বিরাম<small>Protection Paused</small></h2>'
            '<p style="color:rgba(255,226,168,.72)">সুরক্ষা বর্তমানে বন্ধ • tap to resume</p>'
            '<div style="margin-top:16px"><span class="btn" style="display:inline-flex;width:auto;height:40px;padding:0 18px;background:var(--warn);color:#241a05">%s চালু করুন • Resume</span></div>'
            '<div class="pill" style="background:rgba(255,201,77,.14);border-color:rgba(255,201,77,.3);color:var(--warn)"><span class="dot" style="background:var(--warn);box-shadow:0 0 0 3px rgba(255,201,77,.2)"></span>Paused • বিরাম</div>'
            '</div></div>' % (icon("shield_x", 40), icon("shield", 14)))
    else:  # service off
        hero = ('<div class="hero" style="background:linear-gradient(150deg,#2a1c1c,#241719 45%%,#181011);border-color:rgba(255,143,134,.3)"><div class="in"><div class="glow" style="background:radial-gradient(circle,rgba(255,143,134,.28),transparent 70%%)"></div>'
            '<div class="mark" style="background:rgba(28,12,12,.6);border-color:rgba(255,143,134,.4);color:var(--err)">%s</div>'
            '<h2 style="color:#FFC6C0">সার্ভিস বন্ধ<small>Accessibility Service Off</small></h2>'
            '<p style="color:rgba(255,198,192,.72)">এক্সেসিবিলিটি সার্ভিস চালু নেই • protection inactive</p>'
            '<div style="margin-top:16px"><span class="btn" style="display:inline-flex;width:auto;height:40px;padding:0 18px;background:var(--err);color:#2B0B08">%s চালু করুন • Enable</span></div>'
            '<div class="pill" style="background:rgba(255,143,134,.14);border-color:rgba(255,143,134,.3);color:var(--err)"><span class="dot" style="background:var(--err);box-shadow:0 0 0 3px rgba(255,143,134,.2)"></span>Service Off • বন্ধ</div>'
            '</div></div>' % (icon("shield_x", 40), icon("shield", 14)))

    stats = ('<div class="stats">' + stat("shield", "", "42", "মোট ব্লক\nBlocked Today", "+12", True)
        + stat("chip", "v", "18", "AI Detection\nAI ব্লক", "+8", True)
        + stat("history", "b", "5h 32m", "Protected Time\nসুরক্ষিত সময়", "+1h 20m", True) + "</div>")

    quick = ('<div class="grid3">'
        '<div class="card flat" style="padding:12px"><div class="ic" style="width:34px;height:34px;border-radius:10px;background:rgba(92,240,172,.13);color:var(--suc);display:flex;align-items:center;justify-content:center;margin-bottom:8px">%s</div><b style="font-size:12px">App Blocking</b><span class="dim" style="font-size:10px;display:block;margin-top:2px">১২ ব্লক</span></div>'
        '<div class="card flat" style="padding:12px"><div class="ic" style="width:34px;height:34px;border-radius:10px;background:rgba(124,196,255,.13);color:var(--info);display:flex;align-items:center;justify-content:center;margin-bottom:8px">%s</div><b style="font-size:12px">Safe Search</b><span class="dim" style="font-size:10px;display:block;margin-top:2px">৮ কিওয়ার্ড</span></div>'
        '<div class="card flat" style="padding:12px"><div class="ic" style="width:34px;height:34px;border-radius:10px;background:rgba(183,155,255,.15);color:var(--ai);display:flex;align-items:center;justify-content:center;margin-bottom:8px">%s</div><b style="font-size:12px">Whitelist</b><span class="dim" style="font-size:10px;display:block;margin-top:2px">৩ অনুমোদিত</span></div>'
        '</div>' % (icon("block", 18), icon("search", 18), icon("checkc", 18)))

    recent_rows = ""
    if state == "active":
        rows = [("Instagram", "#F58529", "#DD2A7B", "🤖 AI Detection", "AI Blocked", "err", "2 min ago"),
                ("Chrome", "#4285F4", "#34A853", "🔑 Keyword", "Keyword", "neu", "18 min ago"),
                ("YouTube", "#FF0000", "#282828", "🕐 Scheduled", "Schedule", "ai", "1 hr ago")]
        for name, c1, c2, reason, badge, bcls, t in rows:
            recent_rows += ('<div class="row"><div class="bar %s"></div>%s'
                '<div class="mid"><b>%s</b><span>%s</span></div>'
                '<div class="meta"><span class="bdg %s">%s</span>%s</div></div>'
                % (bcls, appic(c1, c2, name[0]), name, reason, bcls, badge, t))
    else:
        recent_rows = ('<div class="empty" style="padding:26px 16px"><div class="eic" style="width:52px;height:52px;border-radius:16px">%s</div>'
                       '<b style="font-size:13.5px">এখনো কোনো ব্লক নেই</b><p>নিরাপদ থাকুন — Guardian Shield সক্রিয় আছে।</p></div>' % icon("shield", 22))

    body = (statusbar() + appbar("Guardian Shield", action="•••") +
        '<div class="scroll"><div class="pad stack">' + hero + stats +
        '<div class="sec"><span class="h">Quick Actions • দ্রুত অ্যাকশন</span></div>' + quick +
        '<div class="sec"><span class="h">আজকের কার্যকলাপ</span><span class="link">See All • সব দেখুন ›</span></div>' +
        '<div class="stack">' + recent_rows + '</div><div class="pb"></div></div></div>' + navbar(0))
    return device(body, {"active": "<b>Loaded (default)</b> — real bindings: <code>txtStatusTitle/Subtitle</code>, <code>txtStatTotal/Ai/Time</code>, <code>recyclerRecent</code>. Toggle → TimeLock guard → AccessibilityPrompt → <code>toggleProtection()</code>.",
                          "paused": "<b>Paused state</b> — <code>btnToggle</code> = Resume, badge amber.",
                          "service-off": "<b>Service-off state</b> — <code>btnToggle</code> = Enable → AccessibilityPrompt, badge red.",
                          "skeleton": "<b>Skeleton (loading)</b>."}[state])

# =============================================================================
# ACTIVITY LOG
# =============================================================================
def activity_log(state="default"):
    bars = "".join('<div class="br %s" style="height:%d%%"></div>' % ("hi" if i == 4 else "", h)
                   for i, h in enumerate([34, 52, 44, 70, 100, 62, 40]))
    if state == "empty":
        mid = ('<div class="empty"><div class="eic">%s</div><b>দেখানোর মতো কিছু নেই</b>'
               '<p>ব্লক ইভেন্ট এখানে দেখাবে।</p></div>' % icon("history", 24))
    elif state == "skeleton":
        mid = ('<div class="stack">' +
               '<div class="skrow"><div class="sk" style="width:40px;height:40px;border-radius:12px"></div><div style="flex:1"><div class="sk" style="height:12px;width:60%"></div><div class="sk" style="height:10px;width:40%;margin-top:6px"></div></div></div>' * 3 + '</div>')
    else:
        rows = [("Instagram", "#F58529", "#DD2A7B", "🤖 AI Detection", "AI", "ai", "09:41"),
                ("Chrome", "#4285F4", "#34A853", "🔑 Keyword: porn", "Keyword", "neu", "09:12"),
                ("Facebook", "#1877F2", "#0B5FC7", "🚫 App Blocked", "App", "err", "08:47"),
                ("YouTube", "#FF0000", "#282828", "🕐 Schedule", "Schedule", "ai", "22:05")]
        mid = '<div class="stack">' + "".join(
            '<div class="row"><div class="bar %s"></div>%s<div class="mid"><b>%s</b><span>%s</span></div>'
            '<div class="meta"><span class="bdg %s">%s</span>%s</div></div>' %
            (bc, appic(c1, c2, n[0]), n, r, bc, b, t) for n, c1, c2, r, b, bc, t in rows) + "</div>"
    chips_html = " ".join([chip("All", True), chip("AI"), chip("Keyword"), chip("App"), chip("Schedule")])
    ai_icon = icon("chip", 18)
    body = (statusbar() + appbar("Activity", "অ্যাক্টিভিটি লগ • 128 events") +
        '<div class="scroll"><div class="pad stack">'
        '<div class="seg"><div class="sg">Day</div><div class="sg on">Week</div><div class="sg">Month</div></div>'
        '<div class="card"><div style="display:flex;align-items:flex-start;justify-content:space-between">'
        '<div><div class="dim" style="font-size:12px">Blocked Attempts\nব্লক করা প্রচেষ্টা</div>'
        '<div style="display:flex;align-items:center;gap:10px;margin-top:6px"><b style="font-size:28px;letter-spacing:-.02em">78</b>'
        '<span class="bdg suc">↗ 24% vs yesterday</span></div></div>'
        '<div class="ic" style="width:40px;height:40px;border-radius:12px;background:rgba(183,155,255,.15);color:var(--ai);display:flex;align-items:center;justify-content:center">' + ai_icon + '</div></div>'
        '<div class="bars">' + bars + '</div>'
        '<div style="display:flex;justify-content:space-between;margin-top:6px;font-size:9px" class="dim">'
        '<span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span><span>Sat</span><span>Sun</span></div>'
        '</div>'
        '<div class="sec"><span class="h">Filter</span></div>'
        '<div class="chips">' + chips_html + '</div>'
        + '<div class="sec"><span class="h">Recent Events</span></div>' + mid + '<div class="pb"></div></div></div>')
    return device(body, {"default": "<b>Loaded</b> — real bindings: <code>txtBlockedAttempts</code> (optional), filter chips → <code>setFilter</code>, <code>txtCount</code>, <code>txtEmpty</code>. Period segment is decorative.",
                          "empty": "<b>Empty state</b> — <code>txtEmpty</code> visible when <code>events.isEmpty()</code>.",
                          "skeleton": "<b>Skeleton</b>."}[state])

# =============================================================================
# PROTECTION HUB
# =============================================================================
def protection(state="active"):
    on = state == "active"
    if on:
        hero = ('<div class="hero"><div class="in"><div class="glow"></div>'
                '<div class="mark">%s</div><h2>All Shields Active<small>সুরক্ষা সক্রিয়</small></h2>'
                '<p>You are fully protected • সব মডিউল চালু</p>'
                '<div class="pill"><span class="dot"></span>Active • সক্রিয়</div></div></div>' % icon("shield", 40))
    else:
        hero = ('<div class="hero" style="background:linear-gradient(150deg,#2a1c1c,#241719 45%%,#181011);border-color:rgba(255,143,134,.3)"><div class="in"><div class="glow" style="background:radial-gradient(circle,rgba(255,143,134,.28),transparent 70%%)"></div>'
                '<div class="mark" style="background:rgba(28,12,12,.6);border-color:rgba(255,143,134,.4);color:var(--err)">%s</div>'
                '<h2 style="color:#FFC6C0">Protection Off<small>সার্ভিস বন্ধ</small></h2>'
                '<p style="color:rgba(255,198,192,.72)">Accessibility service not running</p>'
                '<div class="pill" style="background:rgba(255,143,134,.14);border-color:rgba(255,143,134,.3);color:var(--err)"><span class="dot" style="background:var(--err);box-shadow:0 0 0 3px rgba(255,143,134,.2)"></span>Off • বন্ধ</div></div></div>' % icon("shield_x", 40))

    def mod(ic, t, sub, icc="suc", active=True):
        return ('<div class="card flat" style="padding:14px"><div style="display:flex;align-items:center;gap:10px">'
                '<div class="ic" style="width:38px;height:38px;border-radius:12px;background:rgba(%s,.13);color:%s;display:flex;align-items:center;justify-content:center">%s</div>'
                '<div class="mid" style="flex:1;min-width:0"><b style="font-size:13px">%s</b><span class="%s" style="font-size:10.5px;display:block;margin-top:2px;font-weight:700">%s</span></div>'
                '%s</div></div>') % (
                {"suc": "92,240,172", "info": "124,196,255", "warn": "255,201,77", "ai": "183,155,255"}[icc],
                {"suc": "var(--suc)", "info": "var(--info)", "warn": "var(--warn)", "ai": "var(--ai)"}[icc],
                icon(ic, 20), t, icc, sub,
                icon("checkc", 20) if active else icon("info", 20))

    body = (statusbar() + appbar("Protection", "সুরক্ষা হাব") +
        '<div class="scroll"><div class="pad stack">' + hero +
        '<div class="sec"><span class="h">Modules • মডিউল</span></div>' +
        '<div class="stack">' +
        mod("block", "App Blocking", "Active • ১২ ব্লক", "suc") +
        mod("search", "Keyword Filtering", "Active • ৮ কিওয়ার্ড", "info") +
        mod("history", "Schedule Blocking", "Active • রাত ১০টা–৬টা", "warn") +
        mod("chip", "AI Detection", "Active • 0.72 threshold", "ai") +
        '</div>'
        '<div class="card" style="margin-top:14px"><div class="srow" style="padding:0"><div class="ic acc">%s</div>'
        '<div class="mid"><b>Strict Mode</b><span>সর্বোচ্চ সুরক্ষা • maximum protection</span></div>%s</div></div>'
        '<p class="dim" style="font-size:10.5px;line-height:1.4;margin-top:12px">Reference DNS/VPN/Browser modules are intentionally NOT added — this hub maps only to real features (App Blocking, Keyword, Schedule, AI Detection, Accessibility, Gender Filter).</p>'
        '<div class="pb"></div></div></div>' % (icon("shield", 18), icon("checkc", 20)) + navbar(3))
    return device(body, "<b>Loaded</b> — <code>txtProtectionTitle/Subtitle</code>, <code>imgShield</code>, <code>txtBadgeActive</code>; module cards → real activities (AppList / Keyword / Schedule / Settings)." if on
                  else "<b>Off state</b>.")

# =============================================================================
# APP BLOCKING
# =============================================================================
def app_blocking(state="default"):
    lockbanner = '<div class="lockbanner">%s <span>🔒 Commitment Lock active — ২ দিন ৪ ঘণ্টা বাকি</span></div>' % icon("lock", 16) if state == "locked" else ""
    if state == "empty":
        mid = ('<div class="empty"><div class="eic">%s</div><b>No apps found</b><p>কোনো অ্যাপ পাওয়া যায়নি।</p></div>' % icon("search", 24))
    elif state == "skeleton":
        mid = '<div class="stack">' + ('<div class="skrow"><div class="sk" style="width:44px;height:44px;border-radius:12px"></div><div style="flex:1"><div class="sk" style="height:12px;width:55%"></div><div class="sk" style="height:10px;width:70%;margin-top:6px"></div></div></div>' * 4) + "</div>"
    else:
        rows = [("Chrome", "#4285F4", "#34A853", "com.android.chrome", "Browser", "BLOCKED", True, False),
                ("Instagram", "#F58529", "#DD2A7B", "com.instagram.android", "Social", "BLOCKED", True, False),
                ("WhatsApp", "#25D366", "#128C7E", "com.whatsapp", "Messaging", "ALLOWED", False, True),
                ("YouTube", "#FF0000", "#282828", "com.google.android.youtube", "Video", "BLOCKED", True, False),
                ("Facebook", "#1877F2", "#0B5FC7", "com.facebook.katana", "Social", "BLOCKED", True, False)]
        mid = '<div class="stack">' + "".join(
            '<div class="row"><div class="bar %s"></div>%s<div class="mid"><b>%s</b><span class="mono" style="font-size:10px">%s</span></div>'
            '<span class="bdg neu" style="margin-right:10px">%s</span><span class="bdg %s" style="margin-right:10px">%s</span>%s</div>' %
            (("err" if bl else ("suc" if wl else "")), appic(c1, c2, n[0]), n, pkg, cat,
             ("err" if bl else "suc" if wl else "neu"), bd, sw(bl if bl else wl)) 
            for n, c1, c2, pkg, cat, bd, bl, wl in rows) + "</div>"
    hero_switch = sw(False, True) if state == "locked" else sw(True)
    search_field = '<div class="field">' + icon("search", 18) + '<input placeholder="Search apps… অ্যাপ খুঁজুন"></div>'
    body = (statusbar() + appbar("App Blocking", "অ্যাপ ব্লক") + lockbanner +
        '<div class="scroll"><div class="pad stack">'
        '<div class="card" style="padding:16px"><div style="display:flex;align-items:center;gap:10px">'
        '<div class="mid" style="flex:1"><b style="font-size:15px">App Blocking</b><span class="suc" style="font-size:12.5px;font-weight:700;display:block;margin-top:2px">ON • সক্রিয়</span></div>' + hero_switch + '</div>'
        '<p class="dim" style="font-size:11px;margin:10px 0 12px">Block inappropriate apps from being used.\nঅনুপযুক্ত অ্যাপ ব্যবহার ব্লক করুন।</p>'
        '<div class="hr"></div>'
        '<div class="seg" style="margin-top:2px"><div class="sg on">Blocked Apps • 12</div><div class="sg">All</div><div class="sg">Whitelisted • 3</div></div>'
        '</div>' + search_field
        + mid + '<div class="pb"></div></div></div>')
    return device(body, {"default": "<b>Loaded</b> — <code>switchHero</code> (always-ON guard + snackbar), <code>filterGroup</code>, <code>editSearch</code> → <code>setQuery</code>; row switches → <code>setBlocked/setWhitelisted</code> (one-way rule: whitelist switch hidden when blocked).",
                          "locked": "<b>Locked</b> — <code>lockBanner</code> + <code>txtLockRemaining</code> visible; all toggles disabled.",
                          "empty": "<b>Empty (no search results)</b>.",
                          "skeleton": "<b>Skeleton</b> — binds to the existing <code>AppListState.loading</code> flag."}[state])

# =============================================================================
# KEYWORDS
# =============================================================================
def keywords(state="default"):
    fab = '<div style="position:absolute;right:16px;bottom:18px;height:48px;padding:0 18px;border-radius:999px;background:var(--pri);color:var(--onpri);display:flex;align-items:center;gap:8px;font-weight:800;font-size:13px;box-shadow:0 12px 30px -10px rgba(59,227,154,.6)">%s Add Keyword • যোগ করুন</div>' % icon("add", 18)
    if state == "empty":
        mid = ('<div class="empty" style="margin-top:90px"><div class="eic">%s</div><b>কোনো কিওয়ার্ড নেই</b><p>+ ট্যাপ করে কিওয়ার্ড যোগ করুন।</p></div>' % icon("key", 24))
    else:
        kws = [("gambling", "PLAIN"), ("betting", "PLAIN"), ("xxx", "PLAIN"), ("^(porn|nude)$", "REGEX")]
        mid = '<div class="stack">' + "".join(
            '<div class="row"><div class="ico" style="background:var(--s3);color:var(--info)">%s</div>'
            '<div class="mid"><b>%s</b></div><span class="bdg %s">%s</span>%s</div>' %
            (icon("key", 18), k, ("ai" if b == "REGEX" else "neu"), b, icon("delete", 18))
            for k, b in kws) + "</div>"
    body = (statusbar() + appbar("Keywords", "কিওয়ার্ড ফিল্টার") +
        '<div class="scroll"><div class="pad stack"><div class="sec"><span class="h">Blocked keywords</span><span class="dim">৪ টি</span></div>' +
        mid + '<div class="pb"></div></div></div>' + fab)
    return device(body, {"default": "<b>Loaded</b> — <code>txtKeyword</code>, <code>badge</code> (REGEX/PLAIN), <code>btnDelete</code>, swipe-to-delete; <code>txtEmpty</code> when empty.",
                          "empty": "<b>Empty state</b>."}[state])

# =============================================================================
# SCHEDULE
# =============================================================================
def schedule(state="default"):
    fab = '<div style="position:absolute;right:16px;bottom:18px;height:48px;padding:0 18px;border-radius:999px;background:var(--pri);color:var(--onpri);display:flex;align-items:center;gap:8px;font-weight:800;font-size:13px;box-shadow:0 12px 30px -10px rgba(59,227,154,.6)">%s Add Schedule • যোগ করুন</div>' % icon("add", 18)
    if state == "empty":
        mid = ('<div class="empty" style="margin-top:90px"><div class="eic">%s</div><b>কোনো সময়সূচী নেই</b><p>+ ট্যাপ করে সময়সূচী যোগ করুন।</p></div>' % icon("history", 24))
    else:
        rows = [("com.instagram.android", "22:00 – 06:00", "Mo Tu We Th Fr"),
                ("com.google.android.youtube", "23:00 – 05:00", "Every day"),
                ("com.tiktok...", "21:00 – 07:00", "Sa Su")]
        mid = '<div class="stack">' + "".join(
            '<div class="row"><div class="ico" style="background:var(--s3);color:var(--warn)">%s</div>'
            '<div class="mid"><b class="mono" style="font-size:12.5px">%s</b><span>%s • %s</span></div>%s</div>' %
            (icon("history", 18), p, t, d, icon("edit", 18))
            for p, t, d in rows) + "</div>"
    body = (statusbar() + appbar("Schedule", "সময়সূচী") +
        '<div class="scroll"><div class="pad stack"><div class="sec"><span class="h">Schedule rules</span><span class="dim">৩ টি</span></div>' +
        mid + '<div class="pb"></div></div></div>' + fab)
    return device(body, {"default": "<b>Loaded</b> — <code>txtPackage</code>, <code>txtSchedule</code>, <code>btnEdit</code>, long-press delete.",
                          "empty": "<b>Empty state</b>."}[state])

# =============================================================================
# COMMITMENT LOCK
# =============================================================================
def commitment(state="unlocked"):
    if state == "locked":
        body = (statusbar() + appbar("Commitment Lock", "কমিটেড লক") +
            '<div class="scroll"><div class="pad stack">'
            '<div class="hero" style="background:linear-gradient(150deg,#2a2210,#231d10 45%%,#181306);border-color:rgba(255,201,77,.3)"><div class="in"><div class="glow" style="background:radial-gradient(circle,rgba(255,201,77,.28),transparent 70%%)"></div>'
            '<div class="mark" style="background:rgba(26,20,6,.6);border-color:rgba(255,201,77,.4);color:var(--warn)">%s</div>'
            '<h2 style="color:#FFE2A8">Lock Active<small>লক সক্রিয়</small></h2>'
            '<p style="color:rgba(255,226,168,.72)">Settings locked • কোনো setting বদলানো যাবে না</p>'
            '<div class="pill" style="background:rgba(255,201,77,.14);border-color:rgba(255,201,77,.3);color:var(--warn)"><span class="dot" style="background:var(--warn);box-shadow:0 0 0 3px rgba(255,201,77,.2)"></span>Remaining</div></div></div>'
            '<div class="card" style="text-align:center;padding:22px"><div class="kicker">Time remaining • বাকি সময়</div>'
            '<div style="font-size:40px;font-weight:800;letter-spacing:-.02em;margin-top:8px;font-variant-numeric:tabular-nums">২ দিন ৪ ঘণ্টা</div>'
            '<div class="dim" style="font-size:12px;margin-top:6px">Unlock Request দিলে cooldown শুরু হবে</div></div>'
            '<div class="card" style="background:var(--succ);border-color:rgba(92,240,172,.3)"><div style="display:flex;gap:10px;align-items:center;color:var(--suc)">%s'
            '<div style="font-size:12.5px;line-height:1.4;color:var(--onv)">Cooldown চলছে — সময় শেষ হলে স্বয়ংক্রিয়ভাবে unlock হয়ে যাবে। Stay strong! 💪</div></div></div>'
            '<span class="btn outline" style="margin-top:12px">%s Unlock Request করুন</span>'
            '<p class="dim" style="font-size:11.5px;text-align:center;line-height:1.4;margin-top:14px">Unlock request দিলে cooldown শুরু হবে। Cooldown শেষে lock উঠবে।</p>'
            '<div class="pb"></div></div></div>' % (icon("lock", 40), icon("history", 18), icon("lockopen", 16)))
    else:
        chips = " ".join(chip("১ দিন") + chip("৩ দিন", True) + chip("৭ দিন") + chip("১৫ দিন") + chip("৩০ দিন"))
        body = (statusbar() + appbar("Commitment Lock", "কমিটেড লক") +
            '<div class="scroll"><div class="pad stack">'
            '<div class="center" style="padding:10px 0 6px"><div class="mark" style="width:72px;height:72px;border-radius:22px;background:rgba(59,227,154,.12);border:1px solid rgba(59,227,154,.35);color:var(--pri);display:flex;align-items:center;justify-content:center">%s</div></div>'
            '<h2 style="font-size:20px;font-weight:800;text-align:center;margin-top:14px">Commitment Lock</h2>'
            '<p class="v" style="font-size:13px;text-align:center;line-height:1.5;margin-top:10px">Cooldown duration বেছে নিন। Lock করার পর কোনো setting বদলানো যাবে না। Unlock করতে চাইলে request দিতে হবে — তারপর এই duration শেষে lock উঠবে।</p>'
            '<div class="card" style="margin-top:18px"><div class="kicker">Cooldown Duration</div>'
            '<div class="chips" style="margin-top:12px">%s</div></div>'
            '<div class="card" style="background:var(--warnc);border-color:rgba(255,201,77,.3)"><div style="display:flex;gap:10px;align-items:flex-start;color:var(--warn)">%s'
            '<div style="font-size:12.5px;line-height:1.45;color:var(--onv)">⚠️ Lock করার পর শুধু Unlock Request দিয়ে cooldown শুরু করা যাবে। Cooldown শেষে lock উঠবে — আগে নয়।</div></div></div>'
            '<span class="btn primary" style="margin-top:14px">%s Lock Now • লক করুন</span>'
            '<div class="pb"></div></div></div>' % (icon("lock", 32), chips, icon("warning", 18), icon("lock", 16)))
    return device(body, {"unlocked": "<b>Unlocked state</b> — <code>groupUnlocked</code>, duration chips <code>chip1day…30day</code>.",
                          "locked": "<b>Locked state</b> — <code>txtRemaining</code>, <code>txtLockEnd</code>, <code>txtCooldownNote</code>, <code>btnRequestUnlock</code>."}[state])

# =============================================================================
# PERMISSIONS
# =============================================================================
def permissions():
    def prow(ic, name, granted, note=""):
        return ('<div class="srow"><div class="ic %s">%s</div><div class="mid"><b>%s</b><span>%s</span></div>'
                '<span class="%s" style="font-weight:800;font-size:11px">%s</span></div>'
                % ("acc" if granted else "", icon(ic, 18), name, note,
                   "suc" if granted else "pri", "GRANTED" if granted else "FIX"))
    body = (statusbar() + appbar("Permission Health", "পারমিশন হেলথ") +
        '<div class="scroll"><div class="pad stack">'
        '<div class="card"><div style="padding:0 4px">' +
        prow("access", "Accessibility Service", "সুরক্ষার মূল ইঞ্জিন", True) +
        prow("layers", "Display Over Other Apps", "ব্লক ওভারলে দেখাতে দরকার", True) +
        prow("usage", "Usage Stats Access", "অ্যাপ ব্যবহার মনিটরিং · প্রয়োজনীয়", False) +
        prow("bell", "Notification Permission", "সতর্কবার্তার জন্য", True) +
        prow("battery", "Battery Optimization", "ব্যাকগ্রাউন্ডে চলতে দরকার · ক্রিটিক্যাল", False) +
        '</div></div>'
        '<span class="btn primary">%s Fix All Critical • সব ক্রিটিক্যাল ঠিক করুন</span>'
        '<div class="pb"></div></div></div>' % icon("check", 16))
    return device(body, "<b>Mixed states</b> — each row: <code>icon*</code> (check/warning) + <code>btn*</code> (GRANTED/FIX) + row click → system settings. <code>btnFixAll</code> → <code>fixAllCritical()</code>.")

# =============================================================================
# SETTINGS
# =============================================================================
def settings(state="default"):
    lockbanner = ('<div class="lockbanner">%s <span>🔒 Commitment Lock active — ২ দিন ৪ ঘণ্টা বাকি</span></div>' % icon("lock", 16)) if state == "locked" else ""
    dis = ' style="opacity:.45"' if state == "locked" else ""
    def row(ic, name, note, ctrl, icc=""):
        return '<div class="srow"><div class="ic %s">%s</div><div class="mid"><b>%s</b><span>%s</span></div>%s</div>' % (icc, icon(ic, 18), name, note, ctrl)
    body = (statusbar() + appbar("Settings", "সেটিংস") + lockbanner +
        '<div class="scroll"><div class="pad stack">'
        # PROTECTION
        '<div class="sec"><span class="h">Protection • সুরক্ষা</span></div>'
        '<div class="card" style="padding:0 16px">' +
        row("key", "Keyword Filter", "কিওয়ার্ড ভিত্তিক ব্লক", sw(True) if state == "default" else sw(False, True), "info") +
        row("chip", "AI Content Detection", "AI ভিত্তিক ডিটেকশন", sw(True) if state == "default" else sw(True, True), "acc") +
        '</div>'
        '<div class="card"><div style="display:flex;justify-content:space-between;align-items:center">'
        '<div><b style="font-size:13.5px">Unlock Delay</b><span class="dim" style="font-size:11px;display:block;margin-top:2px">ব্লক হওয়ার পর কতক্ষণে unlock</span></div>'
        '<span class="bdg neu" style="font-size:11px;padding:5px 10px">30s</span></div>'
        '<div class="slider"><div class="rail"></div><div class="fill" style="width:40%"></div><div class="knob" style="left:40%"></div></div></div>'
        # TEMP BLOCK
        '<div class="sec"><span class="h">Temp Block Duration • সাময়িক ব্লক</span></div>'
        '<div class="card"><p class="v" style="font-size:12px;line-height:1.5">AI বারবার ডিটেক্ট করলে অ্যাপটি এই সময়ের জন্য ব্লক হবে (৩টি স্ট্রাইক = ১৫ মিনিট ব্লক)</p>'
        '<div class="chips" style="margin-top:12px">' + chip("১৫ মিনিট", True) + chip("৩০ মিনিট") + chip("১ ঘণ্টা") + '</div></div>'
        # BLOCKING RULES
        '<div class="sec"><span class="h">Blocking Rules • ব্লকিং রুলস</span></div>'
        '<div class="card" style="padding:0 16px">' +
        row("block", "App List", "অ্যাপ ব্লক/হোয়াইটলিস্ট", icon("chev", 16)) +
        row("key", "Keywords", "কিওয়ার্ড ও regex", icon("chev", 16), "info") +
        row("history", "Schedule", "সময়ভিত্তিক ব্লক", icon("chev", 16), "warn") +
        row("access", "Permission Health", "সব পারমিশন ঠিক আছে কিনা", icon("chev", 16)) +
        row("lock", "Commitment Lock", "কমিটেড লক", icon("chev", 16)) +
        '</div>'
        # AI SENSITIVITY
        '<div class="sec"><span class="h">AI Sensitivity • সংবেদনশীলতা</span></div>'
        '<div class="card"><div style="display:flex;justify-content:space-between;align-items:center">'
        '<b style="font-size:13px">Guardian Model Threshold</b><span class="bdg ai" style="font-size:11px;padding:5px 10px">0.72</span></div>'
        '<div class="slider"><div class="rail"></div><div class="fill" style="width:72%"></div><div class="knob" style="left:72%"></div></div>'
        '<div class="chips" style="margin-top:6px">' + chip("১টা · Sensitive") + chip("২টা · Balanced", True) + chip("৩টা · Strict") + chip("৪টা · Very Strict") + '</div>'
        '<p class="dim" style="font-size:10px;line-height:1.4;margin-top:10px">↑ বাড়ালে কম false positive, কম detection · ↓ কমালে বেশি detection, বেশি false positive</p></div>'
        # AI MODELS
        '<div class="sec"><span class="h">AI Models • এআই মডেল</span></div>'
        '<div class="card"><b style="font-size:13px">Guardian Model (.tflite)</b>'
        '<div class="suc" style="font-size:11px;margin:3px 0 12px">✓ 12.4 MB</div>'
        '<div class="btnrow"><span class="btn tonal sm">%s Import</span><span class="btn tonal sm">%s Remove</span></div>'
        '<div class="hr"></div>'
        '<span class="btn tonal sm">%s Change PIN • পিন পরিবর্তন</span></div>'
        '<div class="pb"></div></div></div>'
        % (icon("upload", 16), icon("delete", 16), icon("key", 16)))
    return device(body, {"default": "<b>Loaded</b> — real bindings: <code>switchKeyword/Ai</code>, <code>sliderDelay</code>+<code>txtDelayValue</code>, <code>sliderGuardianThreshold</code>+<code>txtGuardianThresholdValue</code>, <code>chip15/30/60</code>, <code>chipVote1-4</code>, <code>btnImportLegacy/RemoveLegacy</code>, <code>txtLegacyStatus</code>, <code>btnChangePin</code>, nav rows.",
                          "locked": "<b>Locked</b> — <code>lockBanner</code> visible; every control <code>isEnabled=false</code>."}[state])

# =============================================================================
# PIN LOCK
# =============================================================================
def pin(state="verify"):
    def keypad(filled):
        dots = '<div class="dots">' + "".join('<div class="d %s"></div>' % ("f" if i < filled else "") for i in range(6)) + "</div>"
        keys = [("1", ""), ("2", ""), ("3", ""), ("4", ""), ("5", ""), ("6", ""),
                ("7", ""), ("8", ""), ("9", ""), ("⌫", "dim"), ("0", ""), ("✓", "acc")]
        kp = '<div class="keypad">' + "".join('<div class="key %s">%s</div>' % (c, t) for t, c in keys) + "</div>"
        return dots + kp
    if state == "verify":
        inner = (statusbar() + '<div class="scroll" style="display:flex;flex-direction:column;align-items:center;padding-top:26px">'
            '<div class="mark" style="width:88px;height:88px;border-radius:26px;background:var(--pc);border:1px solid rgba(59,227,154,.4);color:var(--pri);display:flex;align-items:center;justify-content:center;position:relative">'
            '<div style="position:absolute;inset:-18px;border-radius:50%%;background:radial-gradient(circle,rgba(59,227,154,.25),transparent 70%%)"></div>%s</div>'
            '<h2 style="font-size:20px;font-weight:800;text-align:center;margin-top:22px">App is Locked<br><span class="v" style="font-size:15px;font-weight:600">Enter PIN • পিন দিন</span></h2>'
            '<p class="dim" style="font-size:11.5px;margin-top:8px">Enter 4–6 digit PIN to continue</p>'
            '<div style="margin-top:6px">%s</div>'
            '<div class="card" style="display:flex;align-items:center;gap:8px;padding:10px 16px;border-radius:999px;margin-top:20px;color:var(--onv)">%s<span style="font-size:12px;font-weight:700">Biometric • বায়োমেট্রিক</span></div>'
            '<div class="v" style="font-size:12px;margin-top:16px">Forgot PIN? • পিন ভুলে গেছেন?</div>'
            '</div>' % (icon("lock", 40), keypad(3), icon("fp", 18)))
    elif state == "wrong":
        inner = (statusbar() + '<div class="scroll" style="display:flex;flex-direction:column;align-items:center;padding-top:26px">'
            '<div class="mark" style="width:88px;height:88px;border-radius:26px;background:var(--pc);border:1px solid rgba(59,227,154,.4);color:var(--pri);display:flex;align-items:center;justify-content:center">%s</div>'
            '<h2 style="font-size:20px;font-weight:800;text-align:center;margin-top:22px">App is Locked<br><span class="v" style="font-size:15px;font-weight:600">Enter PIN • পিন দিন</span></h2>'
            '<div style="margin-top:14px">%s</div>'
            '<div style="position:absolute;left:20px;right:20px;bottom:120px;background:#3A1E1C;border:1px solid rgba(255,143,134,.35);color:var(--err);padding:12px 14px;border-radius:14px;font-size:12.5px;font-weight:700;display:flex;gap:8px;align-items:center">%s Wrong PIN — আর ২ বার চেষ্টা করা যাবে</div>'
            '</div>' % (icon("lock", 40), keypad(0), icon("warning", 16)))
    else:  # setup
        inner = (statusbar() + '<div class="scroll" style="display:flex;flex-direction:column;align-items:center;padding-top:26px">'
            '<div class="mark" style="width:88px;height:88px;border-radius:26px;background:var(--pc);border:1px solid rgba(59,227,154,.4);color:var(--pri);display:flex;align-items:center;justify-content:center">%s</div>'
            '<h2 style="font-size:20px;font-weight:800;text-align:center;margin-top:22px">Set Your Security PIN<br><span class="v" style="font-size:15px;font-weight:600">পিন সেট করুন</span></h2>'
            '<p class="dim" style="font-size:11.5px;margin-top:8px">৪-৬ ডিজিটের পিন দিন</p>'
            '<div style="margin-top:6px">%s</div>'
            '</div>' % (icon("key", 40), keypad(2)))
    return device(inner, {"verify": "<b>Verify</b> — dots <code>dot1-6</code>, keypad <code>btn0-9/Del/Ok</code>, biometric pill, Forgot PIN.",
                          "wrong": "<b>Wrong attempt</b> — Snackbar <code>pin_wrong_attempts</code> (attempts left), dots reset.",
                          "setup": "<b>Setup</b> — <code>txtPrompt</code> (enter → confirm), min-length/mismatch snackbars."}[state])

# =============================================================================
# BLOCK OVERLAY (strike 3 / full block)
# =============================================================================
def block_overlay(variant="ai_temp"):
    temp = variant in ("ai_temp", "day")
    ai = variant in ("ai_temp", "ai_plain")
    dur = "২৪ ঘণ্টা" if variant == "day" else "১৫ মিনিট"
    banner = ('<div class="infocard" style="background:var(--warnc);border-color:rgba(255,201,77,.3);margin-top:14px">'
              '<span style="font-size:16px">🚫</span><span class="warn" style="font-weight:800;font-size:13px">Blocked for ' + dur + ' • সাময়িক ব্লক</span></div>') if temp else ""
    unlock = "" if temp else ('<span class="btn tonal">' + icon("lockopen", 16) + ' Request Unlock • আনলক রিকোয়েস্ট</span>')
    markfalse = "" if not ai else '<span class="btn text">ভুল ব্লক হয়েছে? মনে রাখো, আর ব্লক হবে না</span>'
    reason = ("AI বারবার ডিটেক্ট করেছে — অ্যাপটি সাময়িকভাবে ব্লক" if temp else
              ("AI ক্ষতিকারক বা অশ্লীল কন্টেন্ট খুঁজে পেয়েছে" if ai else "অ্যাপটি ব্লক করা হয়েছে"))
    def info_row(ic, lbl, val, tag):
        return ('<div class="infocard"><div class="ic">' + icon(ic, 18) + '</div><div><div class="lbl">'
                + lbl + '</div><div class="val">' + val + '</div></div>' + tag + '</div>')
    body = (statusbar() + '<div class="ovl">' +
        '<div class="omark">' + icon("warning", 40) + '</div>'
        '<h2>Sensitive Content Blocked<br><span class="v" style="font-size:15px;font-weight:600">সংবেদনশীল কন্টেন্ট ব্লক</span></h2>'
        '<p class="sub">This content has been blocked to protect you</p>'
        '<div class="pack">com.facebook.katana</div>'
        + banner +
        info_row("info", "Reason • কারণ", reason, "") +
        info_row("shield", "Protection • সুরক্ষা", "Active", '<span class="bdg suc tag">Active</span>') +
        info_row("chip", "Blocked Category", "Adult Content • AI Detection", '<span class="bdg ai tag">AI • 0.89</span>') +
        info_row("history", "Time • সময়", "Today, 9:41 AM", '<span class="bdg neu tag">Now</span>') +
        '<div class="acts">'
        '<span class="btn danger">' + icon("shield", 16) + ' Stay Protected • সুরক্ষিত থাকুন</span>' + unlock +
        markfalse + '</div>'
        '<div class="foot"><div class="ln"></div>STAY STRONG 💪 • Guardian Shield</div>'
        '</div>')
    return device(body, {"ai_temp": "<b>AI 3rd-strike temp block</b> — <code>cardTempBlock</code> visible, <code>btnUnlock</code> hidden (hard lock), <code>btnMarkFalse</code> visible (AI only → <code>takePendingCandidate()+addSignature()</code>).",
                          "day": "<b>24h escalation block</b> — same overlay, duration formatted in Bengali.",
                          "ai_plain": "<b>AI block (non-temp)</b> — <code>btnUnlock</code> → DelayUnlock, <code>btnMarkFalse</code> visible.",
                          "non_ai": "<b>Non-AI block</b> — no temp banner, <code>btnUnlock</code> visible, <code>btnMarkFalse</code> hidden (no candidate)."}[variant])

# =============================================================================
# STRIKE WARNING CARD (1/2)
# =============================================================================
def strike_warning(strike=1):
    def wcard(n):
        return ('<div class="wcard"><div class="wic">%s</div>'
                '<div class="wb"><div class="wk">Strike Warning</div>'
                '<div class="wt">সতর্কতা %s/৩</div>'
                '<div class="wd">পরের বার শনাক্ত হলে অ্যাপটি সাময়িকভাবে ব্লক হবে</div>'
                '<span class="wn">Not sensitive ›</span></div>'
                '<div class="wdot">%s/৩</div></div>' % (icon("warning", 22), "১২৩"[n - 1], "১২৩"[n - 1]))
    hero = ('<div class="hero" style="margin:14px 16px 0"><div class="in" style="padding:18px 16px"><div class="mark" style="width:56px;height:56px;border-radius:18px">%s</div>'
            '<h2 style="font-size:18px;margin-top:10px">সুরক্ষা সক্রিয়<small style="font-size:11px">Protection is ON</small></h2>'
            '<div class="pill" style="margin-top:10px"><span class="dot"></span>Protection Active</div></div></div>'
            % icon("shield", 30))
    stats = ('<div class="stats" style="padding:0 16px;margin-top:10px">' +
             stat("shield", "", "42", "Blocked Today", "+12") +
             stat("chip", "v", "18", "AI Blocks", "+8") +
             stat("history", "b", "5h 32m", "Time", "+1h") + "</div>")
    body = ('<div class="scroll">' + hero + stats + wcard(strike) +
            '<div style="margin:150px 16px 0"><div class="stack">'
            + '<div class="skrow"><div class="sk" style="width:40px;height:40px;border-radius:12px"></div><div style="flex:1"><div class="sk" style="height:12px;width:60%"></div><div class="sk" style="height:10px;width:40%;margin-top:6px"></div></div></div>' * 2 +
            '</div></div></div>')
    return device(body, "<b>Strike %d/3 warning card</b> — WindowManager overlay, width = screen − 40dp, <code>TOP|CENTER_HORIZONTAL</code>, <code>y = 18%%</code>, <b>auto-dismiss 3.5 s</b>, tap-to-dismiss. «Not sensitive» writes a <code>NOT_SENSITIVE</code> audit event only — never touches FalsePositiveMemory/AiDetector." % strike)

# =============================================================================
# BLOCKED DETAILS
# =============================================================================
def blocked_details():
    def info_row(ic, lbl, val, tag):
        return ('<div class="infocard"><div class="ic">' + icon(ic, 18) + '</div><div><div class="lbl">'
                + lbl + '</div><div class="val">' + val + '</div></div>' + tag + '</div>')
    body = (statusbar() + appbar("Blocked Content", "ব্লক করা কন্টেন্ট") +
        '<div class="scroll"><div class="pad stack">'
        '<div class="center" style="padding-top:8px"><div class="mark" style="width:88px;height:88px;border-radius:26px;background:var(--errc);border:1px solid rgba(255,143,134,.35);color:var(--err);display:flex;align-items:center;justify-content:center">' + icon("shield_x", 40) + '</div>'
        '<h2 style="font-size:20px;font-weight:800;margin-top:14px">Content Blocked</h2>'
        '<p class="v" style="font-size:12px;max-width:280px;margin-top:6px">Guardian Shield protected you from viewing inappropriate content.</p></div>'
        + info_row("apps", "App", "Facebook", "")
        + info_row("chip", "Blocked Category", "Adult Content", '<span class="bdg err tag">AI</span>')
        + info_row("eye", "Content Source", "facebook.com/reel/...", "")
        + info_row("history", "Time", "Today, 9:41 AM", "")
        + '<div class="card" style="background:var(--succ);border-color:rgba(92,240,172,.28)"><div style="display:flex;gap:12px;align-items:center">'
        '<div class="ic" style="width:40px;height:40px;border-radius:12px;background:rgba(92,240,172,.15);color:var(--suc);display:flex;align-items:center;justify-content:center">' + icon("checkc", 20) + '</div>'
        '<div><b style="font-size:13.5px">You\'re doing great</b><span class="suc" style="font-size:11.5px;display:block;margin-top:2px;font-weight:700">২৩ বার এড়িয়ে গেছেন ✓</span></div></div></div>'
        '<div class="sec"><span class="h">What you can do</span></div>'
        '<div class="card" style="padding:0 16px">'
        '<div class="srow"><div class="ic acc">' + icon("moon", 18) + '</div><div class="mid"><b>Stay Focused</b></div>' + icon("chev", 16) + '</div>'
        '<div class="srow"><div class="ic">' + icon("history", 18) + '</div><div class="mid"><b>View Activity</b></div>' + icon("chev", 16) + '</div>'
        '<div class="srow"><div class="ic">' + icon("checkc", 18) + '</div><div class="mid"><b>Add to Whitelist</b></div>' + icon("chev", 16) + '</div></div>'
        '<span class="btn danger">' + icon("home", 16) + ' Back to App</span>'
        '<span class="btn text">Report This Content</span>'
        '<div class="pb"></div></div></div>')
    return device(body, "<b>Optional details screen</b> — entry from Activity-log row tap. Extras <code>PACKAGE/CATEGORY/SOURCE/TIME</code>; <code>btnBackToApp</code>/<code>rowStayFocused</code> → goHome, <code>rowViewActivity</code> → ActivityLog, <code>rowWhitelist</code> → AppList, <code>btnReport</code> → finish.")

# =============================================================================
# ACCESSIBILITY PROMPT
# =============================================================================
def accessibility_prompt():
    body = (statusbar() + '<div class="ovl" style="justify-content:center;gap:0">' +
        '<div class="omark">' + icon("shield_x", 40) + '</div>'
        '<h2 style="font-size:22px">⚠️ সুরক্ষা বন্ধ হয়ে গেছে!</h2>'
        '<p class="sub" style="max-width:300px">Guardian Shield এর Accessibility Service বন্ধ করা হয়েছে। harmful content থেকে সুরক্ষিত থাকতে এখনই চালু করুন।</p>'
        '<div style="width:100%;margin-top:22px"><span class="btn danger">' + icon("shield", 16) + ' Accessibility Service চালু করুন</span></div>'
        '<div class="pack" style="margin-top:16px">Settings → Accessibility → Guardian Shield → Enable</div>'
        '</div>')
    return device(body, "<b>Accessibility prompt</b> — <code>btnEnable</code> → accessibility settings; shown when service off and user tries to enable protection.")

# =============================================================================
# DELAY UNLOCK
# =============================================================================
def delay_unlock():
    body = (statusbar() + '<div class="ovl" style="justify-content:center">' +
        '<div class="card" style="width:100%;text-align:center;padding:26px 20px">'
        '<div class="mark" style="width:76px;height:76px;border-radius:24px;background:var(--warnc);border:1px solid rgba(255,201,77,.35);color:var(--warn);display:flex;align-items:center;justify-content:center;margin:0 auto">' + icon("timer", 32) + '</div>'
        '<h2 style="font-size:20px;font-weight:800;margin-top:16px">Unlock Pending<br><span class="v" style="font-size:14px;font-weight:600">আনলক পেন্ডিং</span></h2>'
        '<div class="pack" style="margin-top:14px">com.facebook.katana</div>'
        '<p class="v" style="font-size:13px;margin-top:16px">Access will be granted in • এক্সেস দেওয়া হবে:</p>'
        '<div style="font-size:64px;font-weight:800;letter-spacing:-.03em;font-variant-numeric:tabular-nums;margin-top:6px;color:var(--pri)">30</div>'
        '<div class="dim" style="font-size:12px">seconds • সেকেন্ড পর</div>'
        '<div style="margin-top:22px"><span class="btn tonal">' + icon("back", 16) + ' Cancel • বাতিল</span></div>'
        '</div></div>')
    return device(body, "<b>Delay unlock</b> — <code>txtPackage</code>, <code>txtCountdown</code> (live), <code>btnCancel</code>.")

# =============================================================================
# ONBOARDING (4 pages)
# =============================================================================
def onboarding():
    pages = [
        ("🛡️", "GUARDIAN SHIELD", "আপনার ডিজিটাল অভিভাবক", "ক্ষতিকারক কন্টেন্ট, অশ্লীলতা ও আসক্তি থেকে আপনার পরিবারকে সুরক্ষিত রাখুন — ২৪/৭, ডিভাইসেই।"),
        ("🔍", "SMART PROTECTION", "AI + কিওয়ার্ড ডিটেকশন", "অন-ডিভাইস AI ছবি ও টেক্সট স্ক্যান করে, সাথে কিওয়ার্ড ও সময়সূচী ভিত্তিক ব্লকিং।"),
        ("🔐", "PERMISSIONS", "সুরক্ষার জন্য অনুমতি দিন", "Accessibility Service ও Overlay পারমিশন দিলেই সুরক্ষা কাজ করবে। একবার সেট করলেই শেষ।"),
        ("🔑", "SECURITY PIN", "সেটিংস লক করুন", "৪–৬ ডিজিটের PIN দিয়ে সেটিংস সুরক্ষিত রাখুন, যাতে কেউ ব্লকিং নিয়ম বদলাতে না পারে।"),
    ]
    phones = ""
    for i, (emoji, hi, title, body) in enumerate(pages):
        dots = "".join('<div style="width:%s;height:7px;border-radius:4px;background:%s;margin:0 3px"></div>' %
                       (("16px", "var(--pri)") if j == i else ("7px", "var(--s4)")) for j in range(4))
        btn = ('<div style="display:flex;gap:8px;padding:0 20px 26px;align-items:center">'
               '<span class="btn outline sm" style="flex:1;%s">%s Back</span>'
               '<span class="btn primary sm" style="flex:2">%s</span></div>'
               % ("visibility:hidden" if i == 0 else "", icon("back", 14),
                  (("Get Started") if i == 3 else "Next ›")))
        inner = (statusbar() + '<div class="scroll" style="display:flex;flex-direction:column;align-items:center;padding:36px 26px 0">'
                 '<div style="font-size:74px;line-height:1">' + emoji + '</div>'
                 '<div class="kicker" style="margin-top:30px">' + hi + '</div>'
                 '<h2 style="font-size:27px;font-weight:800;letter-spacing:-.02em;text-align:center;margin-top:12px">' + title + '</h2>'
                 '<p class="v" style="font-size:14px;line-height:1.55;text-align:center;margin-top:18px;max-width:280px">' + body + '</p>'
                 '</div><div style="position:absolute;left:0;right:0;bottom:90px;display:flex;justify-content:center">' + dots + '</div>' + btn)
        phones += device(inner, "<b>Page %d/4</b> — <code>txtIcon/txtHighlight/txtTitle/txtBody</code>; dots via <code>indicatorContainer</code>; Skip/Back/Next." % (i + 1))
    return board(phones)

# =============================================================================
# REEL REMINDER
# =============================================================================
def reel_reminder():
    body = (statusbar() + '<div class="scroll" style="display:flex;flex-direction:column;align-items:center;padding:56px 26px 0">'
            '<div style="font-size:70px;line-height:1">☪️</div>'
            '<h2 style="font-size:27px;font-weight:800;color:var(--pri);margin-top:20px">একটু থামো, ভাই!</h2>'
            '<div class="card" style="width:100%;margin-top:16px;padding:22px;text-align:center"><p style="font-size:14.5px;line-height:1.6;color:var(--on)">“তোমরা কুরআন পড়ো, কারণ কিয়ামতের দিন এটি তোমাদের জন্য সুপারিশকারী হবে।”</p><p class="dim" style="font-size:12px;margin-top:10px">— সহীহ মুসলিম</p></div>'
            '<p class="v" style="font-size:13px;line-height:1.5;text-align:center;margin-top:20px">তুমি অনেকক্ষণ ধরে রিলস দেখছো। এই মূল্যবান সময়টা কুরআন বা হাদীসে ব্যয় করো।</p>'
            '<div style="position:absolute;left:26px;right:26px;bottom:40px">'
            '<span class="btn primary">📖 কুরআন অ্যাপ খুলুন</span>'
            '<span class="btn outline" style="margin-top:12px">এখন নয়, ফিরে যাই</span></div>'
            '</div>')
    return device(body, "<b>Reel reminder</b> — <code>txtTitle/txtHadith/txtSubtitle</code>, <code>btnOpenQuran</code> (first installed Islamic app → Play fallback), <code>btnContinue</code> → finish.")

# =============================================================================
# APP ICON + SPLASH
# =============================================================================
def app_icon():
    icon_md = ('<div style="width:96px;height:96px;border-radius:24px;background:linear-gradient(150deg,#1B4A38,#0E2A1F);border:1px solid rgba(59,227,154,.35);display:flex;align-items:center;justify-content:center;color:var(--pri);box-shadow:0 18px 40px -16px rgba(0,0,0,.8)">%s</div>' % icon("shield", 46))
    mono = ('<div style="width:96px;height:96px;border-radius:24px;background:#0B0F0D;border:1px solid var(--bo);display:flex;align-items:center;justify-content:center;color:#fff">%s</div>' % icon("shield", 46))
    body = ('<div class="board">'
        '<div class="slot"><div class="device" style="width:280px"><div class="screen" style="height:340px;display:flex;align-items:center;justify-content:center;gap:20px">%s</div></div>'
        '<div class="cap"><b>Adaptive icon</b> — evolved shield mark: refined outline + check, emerald-on-charcoal, matches the new brand accent (replaces the teal <code>#00E5CC</code> foreground that clashes with everything).</div></div>'
        '<div class="slot"><div class="device" style="width:280px"><div class="screen" style="height:340px;display:flex;align-items:center;justify-content:center">%s</div></div>'
        '<div class="cap"><b>Monochrome layer</b> (themed icons, Android 13+) — single-color shield.</div></div>'
        '<div class="slot"><div class="device" style="width:280px"><div class="screen" style="height:340px;padding:16px">'
        '<div style="width:100%%;height:100%%;border-radius:20px;background:var(--bg);border:1px solid var(--bo);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px">'
        '<div style="width:64px;height:64px;border-radius:16px;background:var(--pc);border:1px solid rgba(59,227,154,.35);display:flex;align-items:center;justify-content:center;color:var(--pri)">%s</div>'
        '<b style="font-size:14px">Guardian Shield</b><span class="dim" style="font-size:11px">স্প্ল্যাশ — Android 12 auto-splash</span></div></div>'
        '<div class="cap"><b>Splash</b> — no dedicated splash activity exists; the OS auto-generates one from the icon + <code>windowBackground</code> (<code>bg</code>). In scope: theme the window background + icon. A custom SplashScreen API screen is optional extra asset work — flagged, not silently skipped.</div></div>'
        '</div>' % (icon_md, mono, icon("shield", 28)))
    html = page("App Icon & Splash", "Scope flag — the adaptive-icon foreground vector is fully editable in-repo (no raster mipmaps exist; minSdk 26 ⇒ mipmap-anydpi-v26 covers every device).",
        body, "Guardian Shield — App Icon & Splash")
    w("app-icon.html", html)
    return

# =============================================================================
# DESIGN TOKENS
# =============================================================================
def design_tokens():
    swatches = [
        ("bg", T["bg"], "canvas / window background"), ("bg_el", T["bg_el"], "app bar / bottom nav"),
        ("s1", T["s1"], "surface 1 — tab wells"), ("s2", T["s2"], "surface 2 — standard cards"),
        ("s3", T["s3"], "surface 3 — elevated / inputs"), ("s4", T["s4"], "surface 4 — highest / hover"),
        ("on", T["on"], "primary text"), ("onv", T["onv"], "secondary text"), ("ond", T["ond"], "tertiary / labels"),
        ("pri", T["pri"], "primary — phosphor emerald"), ("onpri", T["onpri"], "on-primary"),
        ("pc", T["pc"], "primary container"), ("pc2", T["pc2"], "container light step"),
        ("pcend", T["pcend"], "container gradient end"), ("onpc", T["onpc"], "on-primary-container"),
        ("suc", T["suc"], "success"), ("succ", T["succ"], "success container"),
        ("info", T["info"], "info"), ("infoc", T["infoc"], "info container"),
        ("ai", T["ai"], "AI / tertiary"), ("aic", T["aic"], "AI container"),
        ("warn", T["warn"], "warning"), ("warnc", T["warnc"], "warning container"),
        ("err", T["err"], "error"), ("errc", T["errc"], "error container"),
    ]
    sw = "".join('<div class="csw" style="background:%s"><div class="m"><b>%s</b><span>%s</span></div></div>' %
                 (c, n, c) for n, c, d in swatches)
    # contrast table (computed)
    def cr(a, b): return contrast(a, b)
    pairs = [("on · bg", "on", "bg"), ("on · s2", "on", "s2"), ("on · s3", "on", "s3"),
             ("onv · bg", "onv", "bg"), ("onv · s2", "onv", "s2"),
             ("ond · bg", "ond", "bg"), ("ond · s2", "ond", "s2"), ("ond · s3", "ond", "s3"),
             ("pri · bg (icon/text)", "pri", "bg"), ("pri · s2", "pri", "s2"),
             ("onpri · pri", "onpri", "pri"), ("onpc · pc", "onpc", "pc"),
             ("suc · bg", "suc", "bg"), ("info · bg", "info", "bg"), ("ai · bg", "ai", "bg"),
             ("warn · bg", "warn", "bg"), ("warn · s2", "warn", "s2"),
             ("err · bg", "err", "bg"), ("err · errc", "err", "errc")]
    ctab = "".join('<tr><td class="mono">%s</td><td class="mono">%s on %s</td><td>%.2f : 1</td><td class="%s">%s</td></tr>'
                   % (a, T[b], T[c], cr(T[b], T[c]),
                      "suc" if cr(T[b], T[c]) >= 4.5 else "warn",
                      "AAA" if cr(T[b], T[c]) >= 7 else ("AA" if cr(T[b], T[c]) >= 4.5 else "AA-large"))
                  for a, b, c in pairs)
    types = [("Display", "30/38", "800", "-0.02", "hero numerals, big stats"),
             ("Headline", "22/28", "700", "-0.01", "screen titles"),
             ("Title", "17/24", "700", "0", "card titles"),
             ("Body", "14/20", "400", "0", "default text"),
             ("Label", "12/16", "600", "0", "secondary, captions"),
             ("Overline", "11/16", "800", "+0.13 (caps)", "section headers, kickers"),
             ("Badge", "10/14", "800", "+0.05 (caps)", "pills, status")]
    ttab = "".join('<tr><td><b>%s</b></td><td class="mono">%s</td><td class="mono">%s</td><td class="mono">%s</td><td>%s</td></tr>' % t for t in types)
    radius = [("r-xs", "10px", "icons, inputs"), ("r-sm", "14px", "search, lock banner"),
              ("r-md", "20px", "cards (default)"), ("r-lg", "28px", "hero, primary button, stat"),
              ("r-pill", "999px", "chips, segmented, badges, nav pill")]
    rtab = "".join('<tr><td><b>%s</b></td><td class="mono">%s</td><td>%s</td></tr>' % r for r in radius)
    spacing = [("sp-1", "4dp"), ("sp-2", "8dp"), ("sp-3", "12dp"), ("sp-4", "16dp"), ("sp-5", "20dp"), ("sp-6", "24dp"), ("sp-7", "32dp")]
    stab = "".join('<tr><td><b>%s</b></td><td class="mono">%s</td></tr>' % s for s in spacing)
    icongrid = "".join('<div class="ig" title="%s">%s</div>' % (n, icon(n, 22)) for n in sorted(ICONS))
    motion = [
        ("Screen transitions", "Fade-through (fade + 5% scale on enter, 200ms) for activity hops; shared-axis for the onboarding pager."),
        ("Container transform", "Activity-log row → BlockedContent details (shared icon/title), the natural shared-element pair."),
        ("Card press", "0.985 scale + ripple state layer, 150ms ease-out."),
        ("Toggle", "Material Switch (thumb glide + track color, 180ms)."),
        ("List entrance", "Staggered fade-up, 40ms/row (cap 200ms) — refines the existing item_slide_in."),
        ("Skeleton", "Shimmer 1.4s loop while Room loads — never a spinner."),
        ("Overlay appear", "Block overlay: scrim fade + card rise 240ms; strike card: pulse ×2 then settle."),
        ("Pull-to-refresh", "Not implemented today — out of scope, do not invent.")]
    mtab = "".join('<tr><td><b>%s</b></td><td>%s</td></tr>' % m for m in motion)
    html = page("Design Tokens — Sentinel", "The complete, from-scratch design language. Light-on-dark, green-graphite obsidian with a single phosphor-emerald signal color.",
        ('<div class="k">Why dark + emerald</div>'
         '<p class="v" style="font-size:13px;max-width:960px;margin-top:8px">A security product is <b>always-on, always-watching</b>: dark lowers OLED drain and distraction, and reads as vigilant. The single emerald accent is the product\u2019s one semantic truth — <b>"protected / all-clear"</b> — so every green element means the same thing. Amber = warning, red = blocked/error, violet = AI, blue = info. No emoji-as-icon: one stroke-based icon family, one weight.</p>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Color · রঙ</h2>'
         '<div class="swgrid">%s</div>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Contrast ratios (WCAG 2.1, computed)</h2>'
         '<table><tr><th>pair</th><th>foreground / background</th><th>ratio</th><th>grade</th></tr>%s</table>'
         '<p class="dim" style="font-size:11px;margin-top:8px">Every text/background pair meets AA (4.5:1) for body text. on-primary & on-primary-container exceed 10:1. Badge/overline sizes use the same colors and remain AA.</p>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Typography · টাইপোগ্রাফি</h2>'
         '<table><tr><th>role</th><th>size/line</th><th>weight</th><th>tracking</th><th>usage</th></tr>%s</table>'
         '<p class="dim" style="font-size:11px;margin-top:8px">Family: system sans stack + Noto Sans Bengali for Bengali glyphs; tabular figures via <code>font-variant-numeric</code> for counts/scores/PIN.</p>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Shape & elevation</h2>'
         '<table><tr><th>token</th><th>radius</th><th>usage</th></tr>%s</table>'
         '<p class="v" style="font-size:12.5px;margin-top:10px">Elevation is expressed with a <b>1px inner top highlight + soft 24–60px shadow + 1px border</b>, not flat borders. Cards sit on the canvas at 0dp elevation; hero/overlay get a radial glow.</p>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Spacing & grid (4pt)</h2>'
         '<table><tr><th>token</th><th>value</th></tr>%s</table>'
         '<p class="v" style="font-size:12.5px;margin-top:10px">Screen padding 16dp, card inner padding 16dp (hero 20–24), section gap 24dp, card gap 12dp, chip gap 8dp. Baseline 4dp everywhere.</p>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Iconography · আইকন</h2>'
         '<div class="icongrid">%s</div>'
         '<p class="dim" style="font-size:11px;margin-top:10px">One family: 24dp grid, 1.8 stroke, rounded caps, consistent optical weight. The shield mark is refined (not replaced). Replaces all emoji-as-icon usage.</p>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:30px 0 12px">Motion principles</h2>'
         '<table><tr><th>pattern</th><th>spec</th></tr>%s</table>'
        ) % (sw, ctab, ttab, rtab, stab, icongrid, mtab),
        "Guardian Shield — Design Tokens")
    w("design-tokens.html", html)

# =============================================================================
# COMPONENT LIBRARY
# =============================================================================
def components():
    btns = ('<div class="grid2" style="gap:12px">'
            '<span class="btn primary">%s Primary</span><span class="btn tonal">%s Tonal</span>'
            '<span class="btn outline">%s Outline</span><span class="btn danger">%s Danger</span>'
            '<span class="btn ghost">%s Ghost</span><span class="btn text">%s Text</span>'
            '<span class="btn primary" style="opacity:.42">Disabled</span><span class="btn tonal" style="opacity:.42">Disabled</span></div>'
            % tuple(icon("shield", 15) for _ in range(6)))
    switches = '<div class="card" style="display:flex;gap:24px;align-items:center">%s %s <div class="sw" style="opacity:.45"></div><span class="dim" style="font-size:11px">disabled</span></div>' % (sw(True), sw(False))
    chips = '<div class="chips">' + chip("Filter chip", True) + chip("Filter chip") + chip("Choice", True) + chip("Badge-like") + '</div>'
    badges = '<div class="tagrow">' + ''.join('<span class="bdg %s">%s</span>' % (c, l) for c, l in
        [("suc", "Active"), ("err", "Blocked"), ("ai", "AI · 0.89"), ("info", "Keyword"), ("warn", "Warning"), ("neu", "Schedule"), ("suc", "Allowed")]) + '</div>'
    seg = '<div class="seg"><div class="sg on">Day</div><div class="sg">Week</div><div class="sg">Month</div></div>'
    sliders = '<div class="card"><div style="display:flex;justify-content:space-between"><b style="font-size:12.5px">Threshold</b><span class="bdg ai">0.72</span></div><div class="slider"><div class="rail"></div><div class="fill" style="width:72%"></div><div class="knob" style="left:72%"></div></div></div>'
    field = '<div class="field">%s<input placeholder="Search apps…"></div>' % icon("search", 18)
    keypad = '<div class="keypad" style="width:auto;grid-template-columns:repeat(3,64px);justify-content:center;gap:8px"><div class="key" style="height:56px;border-radius:18px">1</div><div class="key" style="height:56px;border-radius:18px">2</div><div class="key" style="height:56px;border-radius:18px">3</div><div class="key dim" style="height:56px;border-radius:18px">⌫</div><div class="key" style="height:56px;border-radius:18px">0</div><div class="key acc" style="height:56px;border-radius:18px">✓</div></div>'
    empty = '<div class="empty" style="padding:20px"><div class="eic" style="width:52px;height:52px;border-radius:16px">%s</div><b style="font-size:13px">কোনো কিওয়ার্ড নেই</b><p>+ ট্যাপ করে যোগ করুন।</p></div>' % icon("key", 22)
    skel = '<div class="stack"><div class="skrow"><div class="sk" style="width:40px;height:40px;border-radius:12px"></div><div style="flex:1"><div class="sk" style="height:12px;width:60%"></div><div class="sk" style="height:10px;width:40%;margin-top:6px"></div></div></div></div>'
    lock = '<div class="lockbanner" style="margin:0">%s <span>🔒 Commitment Lock active — ২ দিন ৪ ঘণ্টা বাকি</span></div>' % icon("lock", 16)
    toast = '<div style="display:flex;justify-content:center"><div style="background:#2A2F2B;border:1px solid var(--bo);color:var(--on);padding:11px 18px;border-radius:999px;font-size:12.5px;font-weight:600;box-shadow:var(--shpop)">✓ ঠিক আছে, এই প্যাটার্নটি আর ব্লক হবে না</div></div>'
    dialog = ('<div style="background:rgba(5,8,6,.6);border-radius:20px;padding:20px;display:flex;align-items:center;justify-content:center"><div style="background:var(--s3);border:1px solid var(--bo);border-radius:24px;padding:20px;width:100%;max-width:280px;box-shadow:var(--shpop)">'
              '<b style="font-size:16px">লগ মুছুন?</b><p class="v" style="font-size:13px;margin-top:8px;line-height:1.45">আপনি কি নিশ্চিত? এটি আর ফেরত আনা যাবে না।</p>'
              '<div class="btnrow" style="margin-top:18px"><span class="btn tonal sm">বাতিল</span><span class="btn primary sm">নিশ্চিত করুন</span></div></div></div>')
    fab = '<div style="display:flex;justify-content:flex-end"><div style="height:48px;padding:0 18px;border-radius:999px;background:var(--pri);color:var(--onpri);display:flex;align-items:center;gap:8px;font-weight:800;font-size:13px">%s Add • যোগ করুন</div></div>' % icon("add", 18)
    rows = ('<div class="card" style="padding:0 16px">'
            '<div class="srow"><div class="ic acc">%s</div><div class="mid"><b>Keyword Filter</b><span>কিওয়ার্ড ভিত্তিক ব্লক</span></div>%s</div>'
            '<div class="srow"><div class="ic">%s</div><div class="mid"><b>App List</b><span>অ্যাপ ব্লক/হোয়াইটলিস্ট</span></div>%s</div></div>'
            % (icon("key", 18), sw(True), icon("block", 18), icon("chev", 16)))
    html = page("Component Library", "Every reusable component with its states — used identically across all screens.",
        ('<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Buttons</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Switches</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Chips</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Badges / status</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Segmented control</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Slider</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Search field</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Keypad & PIN dots</h2><div class="grid2" style="gap:16px;align-items:center"><div class="dots" style="margin:0">%s</div>%s</div>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Empty state</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Loading skeleton</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Lock banner</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Snackbar / toast</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Dialog (app uses AlertDialog — no bottom sheets today)</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Extended FAB</h2>%s'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:28px 0 12px">Setting rows</h2>%s'
        ) % (btns, switches, chips, badges, seg, sliders, field,
             '<div class="d"></div><div class="d"></div><div class="d f"></div><div class="d"></div>', keypad,
             empty, skel, lock, toast, dialog, fab, rows),
        "Guardian Shield — Component Library")
    w("components.html", html)

# =============================================================================
# INDEX / GALLERY
# =============================================================================
def index():
    screens = [
        ("home-dashboard.html", "Home / Dashboard", "hero status card · stats · quick actions · recent activity · active/paused/off/skeleton"),
        ("activity-log.html", "Activity / History", "segmented period · stats + bar chart · filters · empty/skeleton"),
        ("protection.html", "Protection hub", "All-Shields hero · real module cards (App/Keyword/Schedule/AI)"),
        ("app-blocking.html", "App Blocking", "hero toggle · tabs · search · rows · locked/empty/skeleton"),
        ("keywords.html", "Keywords", "list · FAB · empty"),
        ("schedule.html", "Schedule rules", "list · FAB · empty"),
        ("commitment-lock.html", "Commitment Lock", "locked / unlocked duration picker"),
        ("permissions.html", "Permission Health", "5 rows · Fix All"),
        ("settings.html", "Settings", "grouped sections · switches · sliders · chips · models · locked"),
        ("pin-lock.html", "PIN Lock", "verify / setup / wrong / lockout · keypad · biometric"),
        ("block-overlay.html", "Block Overlay (Strike 3)", "AI temp block · 24h lock · non-AI · false-positive flow"),
        ("strike-warning.html", "Strike 1/2 warning card", "re-skinned card · «Not sensitive» audit · 3.5s auto-dismiss"),
        ("blocked-details.html", "Blocked Content details", "info rows · actions"),
        ("accessibility-prompt.html", "Accessibility prompt", "service-off CTA"),
        ("delay-unlock.html", "Delay unlock", "countdown"),
        ("onboarding.html", "Onboarding (4 pages)", "Welcome / Features / Permissions / PIN"),
        ("reel-reminder.html", "Reel reminder", "Islamic interstitial"),
        ("app-icon.html", "App icon & splash", "adaptive icon · monochrome · splash flag"),
        ("design-tokens.html", "Design tokens", "color · contrast · type · shape · spacing · icons · motion"),
        ("components.html", "Component library", "buttons · toggles · chips · badges · rows · states"),
    ]
    cards = "".join('<a class="sc" href="%s"><b>%s</b><span>%s</span><span class="go">Open ›</span></a>' % (f, t, d) for f, t, d in screens)
    html = page("Guardian Shield — Sentinel Redesign · Mockups", "Static HTML/CSS approval gate. Every screen, every state, one shared design language. No Kotlin/XML has been touched.",
        ('<div class="sgrid">%s</div>'
         '<h2 style="font-size:13px;font-weight:800;letter-spacing:.13em;text-transform:uppercase;color:var(--pri);margin:34px 0 12px">Compliance checklist</h2>'
         '<div class="card"><ul style="list-style:none;display:flex;flex-direction:column;gap:8px;font-size:13px;color:var(--onv)">'
         '<li>✅ App name kept <b style="color:var(--on)">Guardian Shield</b> — no rebrand, no mascot.</li>'
         '<li>✅ Detection/blocking logic untouched — cosmetic only (strike timing 3.5s, thresholds, grace, DAO queries preserved).</li>'
         '<li>✅ Real screens only — no invented DNS/VPN/browser/whitelist-activity/side-drawer.</li>'
         '<li>✅ Bengali copy uses the real <code>values-bn/strings.xml</code> tone.</li>'
         '<li>✅ Every screen shows default + empty + loading(skeleton) + error/edge states where applicable.</li>'
         '<li>✅ One shared design system (tokens + component library) used across all screens.</li>'
         '<li>⚠️ App icon/splash: in scope as vector/theme work; raster mipmap regeneration flagged in app-icon.html.</li>'
         '</ul></div>'
        ) % cards, "Guardian Shield — Mockups")
    w("index.html", html)

# =============================================================================
def main():
    w("home-dashboard.html",
      page("Home / Dashboard", "Hero protection-status card, stat tiles, quick actions, recent activity — with active / paused / service-off / skeleton states.",
          board(dashboard("active"), dashboard("paused"), dashboard("service-off"), dashboard("skeleton")),
          "Guardian Shield — Home / Dashboard"))
    w("activity-log.html",
      page("Activity / History", "Period segmented control, stats card with 7-day bars, filter chips, event list — default / empty / skeleton.",
          board(activity_log("default"), activity_log("empty"), activity_log("skeleton")),
          "Guardian Shield — Activity Log"))
    w("protection.html",
      page("Protection Hub", "Central shield + real module cards (App Blocking, Keyword, Schedule, AI Detection) — active / off.",
          board(protection("active"), protection("off")),
          "Guardian Shield — Protection"))
    w("app-blocking.html",
      page("App Blocking", "Hero toggle, tabs, search, app rows with switches — default / locked / empty / skeleton.",
          board(app_blocking("default"), app_blocking("locked"), app_blocking("empty"), app_blocking("skeleton")),
          "Guardian Shield — App Blocking"))
    w("keywords.html",
      page("Keywords", "Keyword filter list with REGEX/PLAIN badges — default / empty.",
          board(keywords("default"), keywords("empty")),
          "Guardian Shield — Keywords"))
    w("schedule.html",
      page("Schedule Rules", "Time-based block rules — default / empty.",
          board(schedule("default"), schedule("empty")),
          "Guardian Shield — Schedule"))
    w("commitment-lock.html",
      page("Commitment Lock", "Duration picker (unlocked) and active-lock countdown (locked).",
          board(commitment("unlocked"), commitment("locked")),
          "Guardian Shield — Commitment Lock"))
    w("permissions.html",
      page("Permission Health", "Five permission rows with granted/FIX states and Fix All.",
          board(permissions()),
          "Guardian Shield — Permissions"))
    w("settings.html",
      page("Settings", "Grouped sections with the correct control per row — default / locked.",
          board(settings("default"), settings("locked")),
          "Guardian Shield — Settings"))
    w("pin-lock.html",
      page("PIN Lock", "Keypad + dots + biometric — verify / setup / wrong-attempt / lockout.",
          board(pin("verify"), pin("setup"), pin("wrong")),
          "Guardian Shield — PIN Lock"))
    w("block-overlay.html",
      page("Block Overlay (Strike 3 / full block)", "Re-skinned full-screen block overlay — keep false-positive flow, duration rendering, hard-lock rule.",
          board(block_overlay("ai_temp"), block_overlay("day"), block_overlay("ai_plain"), block_overlay("non_ai")),
          "Guardian Shield — Block Overlay"))
    w("strike-warning.html",
      page("Strike 1/2 Warning Card", "Re-skin only — keeps the 3.5s auto-dismiss, 18%-height position, tap-to-dismiss, and the audit-only «Not sensitive» report button.",
          board(strike_warning(1), strike_warning(2)),
          "Guardian Shield — Strike Warning"))
    w("blocked-details.html",
      page("Blocked Content Details", "Optional details screen reachable from the activity log.",
          board(blocked_details()),
          "Guardian Shield — Blocked Details"))
    w("accessibility-prompt.html",
      page("Accessibility Prompt", "Service-off prompt with single CTA to system settings.",
          board(accessibility_prompt()),
          "Guardian Shield — Accessibility Prompt"))
    w("delay-unlock.html",
      page("Delay Unlock", "Countdown before access is granted.",
          board(delay_unlock()),
          "Guardian Shield — Delay Unlock"))
    w("onboarding.html",
      page("Onboarding", "Four-page first-run flow (Welcome → Features → Permissions → PIN).",
          onboarding(),
          "Guardian Shield — Onboarding"))
    w("reel-reminder.html",
      page("Reel Reminder", "Islamic interstitial after extended reel scrolling.",
          board(reel_reminder()),
          "Guardian Shield — Reel Reminder"))
    design_tokens()
    components()
    app_icon()
    index()
    print("DONE —", len(os.listdir(OUT)), "files in", OUT)

if __name__ == "__main__":
    main()
