"""Builds a.html, b.html, c.html: three layouts for the course page and the lesson page.

Same content in all three (the demo seed's 정보보안 기초, step 2 of 4 in progress), same tokens.
    python docs/design/options/learning/gen.py
"""
from pathlib import Path

HERE = Path(__file__).parent

BRAND = ('<svg viewBox="0 0 32 32"><rect width="32" height="32" rx="8" fill="#E9ECF1"/>'
         '<path d="M11 8v14.5h4.5L24 12" fill="none" stroke="#1D2738" stroke-width="3" '
         'stroke-linecap="round" stroke-linejoin="round"/></svg>')
CHECK = '<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>'
LOCK = '<svg viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>'
ARROW_L = '<svg class="i" viewBox="0 0 24 24"><path d="M15 6l-6 6 6 6"/></svg>'
ARROW_R = '<svg class="i" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg>'
PLAY = '<svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M10 8.5v7l5.5-3.5z"/></svg>'
TEXT = '<svg class="i" viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h10"/></svg>'
QUIZ = '<svg class="i" viewBox="0 0 24 24"><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4h6v3H9zM9 12l2 2 4-4"/></svg>'
CHEV = '<svg class="i" viewBox="0 0 24 24"><path d="M6 9l6 6 6-6"/></svg>'
DONE_I = '<svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l3 3 5-5.5"/></svg>'

COURSE = "정보보안 기초"
DESC = "피싱 메일과 비밀번호, 사내 자료를 다루는 기본 원칙을 20분 안에 익힙니다."
DUE = "마감 11월 14일"
# (title, kind, state) — state: done | now | todo | lock
STEPS = [
    ("피싱 메일 알아보기", "영상", "done"),
    ("안전한 비밀번호", "텍스트", "now"),
    ("사내 자료 반출 규칙", "텍스트", "todo"),
    ("최종 퀴즈", "퀴즈", "lock"),
]
NOW = 1
BODY = ('<div class="body"><p>안전한 비밀번호</p><p>이 레슨은 데모용 본문입니다. 업무에서 지켜야 할 원칙을 읽고 '
        '\'다 읽었어요\'를 누르세요.</p></div>')
KIND_ICON = {"영상": PLAY, "텍스트": TEXT, "퀴즈": QUIZ}


def dot(i: int, state: str) -> str:
    inner = CHECK if state == "done" else LOCK if state == "lock" else str(i + 1)
    return f'<span class="dot {state}">{inner}</span>'


def desk(content: str) -> str:
    return (f'<div class="desk"><aside class="shell"><div class="brand">{BRAND}LMS</div>'
            f'<div class="navi">내 교육</div></aside><div><div class="top">데모전자<span>박서연 · 학습자</span></div>'
            f'<main class="work">{content}</main></div></div>')


def phone(content: str) -> str:
    return f'<div class="phone"><div class="top">데모전자<span>박서연</span></div><main class="work">{content}</main></div>'


def page(name: str, title: str, note: str, course: str, lesson: str, lesson_phone: str, css: str) -> None:
    html = f"""<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>{title}</title>
<link rel="stylesheet" href="base.css"><style>{css}</style></head><body>
<div class="note"><h1>{title}</h1><p>{note}</p></div>
<p class="label">코스 화면</p>{desk(course)}
<p class="label">레슨 화면 (데스크톱 1280 · 휴대폰 360)</p>
<div class="row">{desk(lesson)}</div>
<div class="row" style="margin-top:24px">{phone(lesson_phone)}</div>
</body></html>"""
    (HERE / f"{name}.html").write_text(html, encoding="utf-8")


# ---------- A. 단계 막대: a horizontal stepper on both pages ----------
A_CSS = """
.stepper{display:grid;grid-template-columns:repeat(4,1fr);gap:0;margin:22px 0 24px}
.st{position:relative;display:grid;justify-items:center;gap:8px;text-align:center;padding:0 6px}
.st:not(:last-child)::after{content:"";position:absolute;top:12px;left:calc(50% + 18px);right:calc(-50% + 18px);height:2px;background:var(--line)}
.st.done:not(:last-child)::after{background:var(--success)}
.st b{font-size:13px;font-weight:600;color:var(--ink-2);line-height:1.4}
.st.now b{color:var(--ink)}
.st small{font-size:12px;color:var(--muted)}
.cur{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:20px 24px}
.cur small{font-size:13px;color:var(--muted)}
.cur h3{font-size:20px;margin-top:2px}
.mini{display:flex;align-items:center;gap:10px;margin:2px 0 18px}
.mini .seg{flex:1;display:grid;grid-template-columns:repeat(4,1fr);gap:4px}
.mini .seg i{height:4px;border-radius:2px;background:var(--line)}
.mini .seg i.done{background:var(--success)}.mini .seg i.now{background:var(--accent)}
.lt{font-size:13px;color:var(--muted)}
.foot{display:flex;justify-content:space-between;align-items:center;border-top:1px solid var(--line);margin-top:20px;padding-top:16px}
"""


def a_stepper(compact: bool = False) -> str:
    items = "".join(
        f'<div class="st {s}">{dot(i, s)}<b>{t}</b>' + ("" if compact else f'<small>{k}</small>') + '</div>'
        for i, (t, k, s) in enumerate(STEPS))
    return f'<div class="stepper">{items}</div>'


a_course = f"""<p class="back">{ARROW_L}내 교육</p><h2 class="h1">{COURSE}</h2>
<p class="meta" style="margin-top:6px">{DUE} · 4단계 중 1단계 완료</p>
<div class="panel" style="margin-top:20px;padding:4px 24px 0">{a_stepper()}</div>
<div class="panel cur" style="margin-top:16px"><div><small>2단계 · 텍스트</small><h3>안전한 비밀번호</h3></div>
<span class="btn pri lg">{PLAY}이어서 학습하기</span></div>"""

a_lesson = f"""<p class="back">{ARROW_L}{COURSE}</p><h2 class="h1">안전한 비밀번호</h2>
<div class="panel" style="margin-top:16px;padding:0 24px">{a_stepper(compact=True)}</div>
<div class="panel" style="margin-top:16px;padding:24px">{BODY}
<div class="foot"><span class="btn ghost">{ARROW_L}피싱 메일 알아보기</span><span class="btn pri">다 읽었어요</span></div></div>"""

a_phone = f"""<p class="back">{ARROW_L}{COURSE}</p>
<div class="mini"><span class="lt">2/4단계</span><div class="seg"><i class="done"></i><i class="now"></i><i></i><i></i></div></div>
<h2 class="h1" style="font-size:22px">안전한 비밀번호</h2>
<div class="panel" style="margin-top:14px;padding:18px">{BODY}</div>
<div style="display:grid;gap:8px;margin-top:16px"><span class="btn pri lg">다 읽었어요</span><span class="btn ghost">{ARROW_L}이전 단계</span></div>"""

page("a", "A. 단계 막대",
     "두 화면 모두 위쪽에 가로 단계 막대(완료 · 지금 · 남음 · 잠김)를 둡니다. 코스 화면은 막대 + '지금 할 단계' 카드 하나뿐이라 가장 짧습니다. "
     "단계가 6개를 넘으면 가로 막대가 비좁아져 제목이 줄어드는 것이 약점입니다(그때는 세로 목록으로 바꿈). 휴대폰은 막대 대신 4칸 선.",
     a_course, a_lesson, a_phone, A_CSS)


# ---------- B. 목차 옆에: classic LMS outline beside the content ----------
B_CSS = """
.cols{display:grid;grid-template-columns:1fr 300px;gap:24px;align-items:start;margin-top:18px}
.outline .hd{padding:16px 18px 14px;border-bottom:1px solid var(--line)}
.outline .hd b{font-size:15px}.outline .hd p{font-size:12px;color:var(--muted);margin:4px 0 10px}
.ol{list-style:none}
.ol li{display:flex;align-items:center;gap:10px;padding:11px 18px;font-size:14px;color:var(--ink-2);border-left:3px solid transparent}
.ol li+li{border-top:1px solid var(--line)}
.ol li.now{background:var(--accent-soft);border-left-color:var(--accent);color:var(--ink);font-weight:600}
.ol li .k{margin-left:auto;font-size:12px;color:var(--muted);font-weight:500}
.cl li{padding:16px 20px;font-size:15px}
.cl li.now{display:grid;grid-template-columns:auto 1fr auto;row-gap:2px}
.cl li.now small{grid-column:2;font-size:13px;color:var(--muted);font-weight:500}
.cl li.now .btn{grid-row:1/3;grid-column:3}
.tog{display:flex;align-items:center;gap:10px;padding:12px 14px;font-size:14px}
.tog b{flex:1}
"""


def b_list(cls: str = "", rich_now: bool = False) -> str:
    rows = []
    for i, (t, k, s) in enumerate(STEPS):
        if rich_now and s == "now":
            rows.append(f'<li class="now">{dot(i, s)}<span>{t}</span><small>2단계 · {k}</small>'
                        f'<span class="btn pri">{PLAY}이어서 학습하기</span></li>')
        else:
            rows.append(f'<li class="{s}">{dot(i, s)}<span>{t}</span><span class="k">{k}</span></li>')
    return f'<ul class="ol {cls}">{"".join(rows)}</ul>'


b_course = f"""<p class="back">{ARROW_L}내 교육</p><h2 class="h1">{COURSE}</h2>
<p class="meta" style="margin-top:6px">{DUE}</p>
<div class="panel" style="margin-top:20px"><div style="padding:16px 20px;border-bottom:1px solid var(--line);display:flex;align-items:center;gap:14px">
<span class="meta" style="white-space:nowrap">1/4 단계</span><div class="bar" style="flex:1"><i style="width:25%"></i></div></div>
{b_list("cl", rich_now=True)}</div>"""

b_lesson = f"""<p class="back">{ARROW_L}{COURSE}</p><h2 class="h1">안전한 비밀번호</h2>
<div class="cols"><div class="panel" style="padding:24px">{BODY}
<div style="display:flex;justify-content:flex-end;border-top:1px solid var(--line);margin-top:20px;padding-top:16px"><span class="btn pri">다 읽었어요</span></div></div>
<aside class="panel outline"><div class="hd"><b>{COURSE}</b><p>1/4 단계 · {DUE}</p><div class="bar"><i style="width:25%"></i></div></div>{b_list()}</aside></div>"""

b_phone = f"""<p class="back">{ARROW_L}{COURSE}</p>
<div class="panel tog"><span class="dot now">2</span><b>2/4 · 안전한 비밀번호</b>{CHEV}</div>
<h2 class="h1" style="font-size:22px;margin-top:16px">안전한 비밀번호</h2>
<div class="panel" style="margin-top:14px;padding:18px">{BODY}</div>
<div style="display:grid;margin-top:16px"><span class="btn pri lg">다 읽었어요</span></div>"""

page("b", "B. 목차 옆에",
     "레슨 화면 오른쪽에 코스 목차를 늘 둡니다(온라인 강의 사이트에서 익숙한 배치). 지금 단계는 강조색 줄로, 끝낸 단계는 체크로 보입니다. "
     "코스 화면은 같은 목록 하나에 '지금 단계' 줄만 펼쳐 버튼을 붙였습니다. 단계가 많아도 무너지지 않지만, 레슨 화면 본문 폭이 좁아집니다. "
     "휴대폰은 목차를 '2/4 · 제목' 한 줄로 접고 누르면 펼칩니다.",
     b_course, b_lesson, b_phone, B_CSS)


# ---------- C. 집중: one task per screen, the outline only on the course page ----------
C_CSS = """
.crumb{display:flex;align-items:center;gap:12px;font-size:13px;color:var(--muted)}
.crumb b{color:var(--ink);font-weight:600}
.seg{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;margin:12px 0 22px}
.seg i{height:4px;border-radius:2px;background:var(--line)}
.seg i.done{background:var(--success)}.seg i.now{background:var(--accent)}
.next{padding:28px;display:grid;gap:14px}
.next small{font-size:13px;color:var(--muted)}
.next h3{font-size:24px;line-height:1.3}
.check{list-style:none;margin-top:24px}
.check li{display:flex;align-items:center;gap:12px;padding:12px 4px;font-size:15px;color:var(--ink-2);border-top:1px solid var(--line)}
.check li:last-child{border-bottom:1px solid var(--line)}
.check li.now{color:var(--ink);font-weight:600}
.check li .s{margin-left:auto;font-size:13px;color:var(--muted);font-weight:500}
.dock{display:flex;justify-content:space-between;align-items:center;margin-top:20px}
"""

c_seg = '<div class="seg"><i class="done"></i><i class="now"></i><i></i><i></i></div>'
c_check = "".join(
    f'<li class="{s}">{dot(i, s)}<span>{t}</span><span class="s">'
    + {"done": "완료", "now": "진행 중", "todo": "", "lock": "레슨을 마치면 열림"}[s] + '</span></li>'
    for i, (t, k, s) in enumerate(STEPS))

c_course = f"""<p class="back">{ARROW_L}내 교육</p><h2 class="h1">{COURSE}</h2>
<p class="meta" style="margin-top:6px">{DUE} · 1/4 단계</p>{c_seg}
<div class="panel next"><small>지금 할 단계 · 2/4 · 텍스트</small><h3>안전한 비밀번호</h3>
<div><span class="btn pri lg">{PLAY}이어서 학습하기</span></div></div>
<ul class="check">{c_check}</ul>"""

c_lesson = f"""<div class="crumb">{ARROW_L}<b>{COURSE}</b><span>2/4 단계</span></div>{c_seg}
<div style="max-width:760px"><h2 class="h1">안전한 비밀번호</h2>
<div class="panel" style="margin-top:16px;padding:28px">{BODY}</div>
<div class="dock"><span class="btn ghost">{ARROW_L}이전</span><span class="btn pri lg">다 읽었어요</span></div></div>"""

c_phone = f"""<div class="crumb">{ARROW_L}<b>{COURSE}</b><span>2/4</span></div>{c_seg}
<h2 class="h1" style="font-size:22px">안전한 비밀번호</h2>
<div class="panel" style="margin-top:14px;padding:18px">{BODY}</div>
<div class="dock"><span class="btn ghost">{ARROW_L}이전</span><span class="btn pri lg">다 읽었어요</span></div>"""

page("c", "C. 집중",
     "레슨 화면에는 '코스 이름 · 2/4 단계'와 4칸 진행선, 본문, 버튼만 남깁니다. 목차는 코스 화면에만 둡니다. "
     "코스 화면은 맨 위에 '지금 할 단계' 카드 하나, 그 아래 단계 체크리스트. 가장 조용하지만, 레슨 화면에서 다른 단계 제목은 보이지 않습니다.",
     c_course, c_lesson, c_phone, C_CSS)
print("ok")
