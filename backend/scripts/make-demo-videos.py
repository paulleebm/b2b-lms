"""Make the demo seed's lesson videos: slide-style, silent, 30 s, 1280x720 H.264.

The demo courses (DemoSeeder) open with a video lesson. These are drawn here with ffmpeg rather
than taken from stock footage: no licence to track, a few hundred KB each, and the text matches
the lesson. Colours and font are the app's (DESIGN.md: paper, ink, muted, line, primary; Pretendard).

    python backend/scripts/make-demo-videos.py     # needs ffmpeg on PATH; writes resources/demo-videos/
"""

import shutil
import subprocess
import tempfile
from pathlib import Path

BACKEND = Path(__file__).resolve().parent.parent
FONTS = BACKEND / "src/main/resources/fonts"
OUT = BACKEND / "src/main/resources/demo-videos"

DURATION = 30  # seconds; DemoSeeder.VIDEO_SECONDS must match
PAPER, INK, MUTED, LINE, PRIMARY = "0xF7F7F4", "0x161B24", "0x5B6372", "0xE1E2DE", "0x1D2738"

# file name -> (course, lesson title, four points shown one after another)
VIDEOS = {
    "phishing.mp4": ("정보보안 기초", "피싱 메일 알아보기", [
        "보낸 사람 주소를 끝까지 확인합니다",
        "급하게 재촉하는 메일일수록 의심합니다",
        "링크는 누르기 전에 실제 주소를 확인합니다",
        "의심되면 열지 말고 보안팀에 신고합니다",
    ]),
    "harassment.mp4": ("직장 내 괴롭힘 예방", "괴롭힘의 정의", [
        "직장에서의 지위나 관계의 우위를 이용해",
        "업무상 적정 범위를 넘어",
        "신체적·정신적 고통을 주거나 근무환경을 악화시키는 행위",
        "근로기준법 제76조의2가 정한 기준입니다",
    ]),
    "privacy.mp4": ("개인정보보호 교육", "개인정보란 무엇인가", [
        "살아 있는 개인을 알아볼 수 있는 정보입니다",
        "이름, 주민등록번호, 연락처, 얼굴이 담긴 영상",
        "다른 정보와 쉽게 합쳐 알아볼 수 있는 정보도 포함됩니다",
        "업무에 필요한 만큼만 수집하고 이용합니다",
    ]),
    "loading-zone.mp4": ("물류 현장 안전", "적재 구역 수칙", [
        "적재 구역 표시선 안으로 들어가지 않습니다",
        "안전모와 안전화를 반드시 착용합니다",
        "지게차 작업 반경에서 떨어져 걷습니다",
        "적재물이 기울면 바로 관리자에게 알립니다",
    ]),
}


def text(work: Path, name: str, value: str, font: str, size: int, color: str, x: int, y: int,
         appear: float | None = None) -> str:
    """drawtext reading from a file, so Korean and punctuation need no escaping."""
    (work / name).write_text(value, encoding="utf-8")
    alpha = f":alpha='if(lt(t,{appear}),0,min(1,(t-{appear})/0.4))'" if appear is not None else ""
    return f"drawtext=fontfile={font}:textfile={name}:fontsize={size}:fontcolor={color}:x={x}:y={y}{alpha}"


def make(file_name: str, course: str, title: str, points: list[str]) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        shutil.copy(FONTS / "Pretendard-Regular.ttf", work / "regular.ttf")
        shutil.copy(FONTS / "Pretendard-Bold.ttf", work / "bold.ttf")
        filters = [
            text(work, "course.txt", course, "regular.ttf", 28, MUTED, 96, 92),
            text(work, "title.txt", title, "bold.ttf", 60, INK, 96, 136),
            f"drawbox=x=96:y=236:w=1088:h=2:color={LINE}:t=fill",
        ]
        for i, point in enumerate(points):
            y, appear = 290 + i * 76, 3 + i * 5
            filters.append(text(work, f"n{i}.txt", str(i + 1), "bold.ttf", 34, PRIMARY, 96, y, appear))
            filters.append(text(work, f"p{i}.txt", point, "regular.ttf", 34, INK, 144, y, appear))
        filters += [
            text(work, "label.txt", "데모 영상", "regular.ttf", 22, MUTED, "w-tw-96", 614),
            f"drawbox=x=96:y=660:w=1088:h=6:color={LINE}:t=fill",
        ]
        # Progress fill: a full-width bar (input 1) slides in from the left; drawbox cannot
        # change size per frame, overlay can move. The paper strip left of the track hides it.
        graph = (f"[0:v]{','.join(filters)}[slide];"
                 f"[slide][1:v]overlay=x='96-1088+1088*t/{DURATION}':y=660,"
                 f"drawbox=x=0:y=660:w=96:h=6:color={PAPER}:t=fill[out]")
        OUT.mkdir(parents=True, exist_ok=True)
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", f"color=c={PAPER}:s=1280x720:r=30:d={DURATION}",
            "-f", "lavfi", "-i", f"color=c={PRIMARY}:s=1088x6:r=30:d={DURATION}",
            "-filter_complex", graph, "-map", "[out]",
            "-c:v", "libx264", "-preset", "slow", "-crf", "28", "-pix_fmt", "yuv420p",
            "-movflags", "+faststart", str(OUT / file_name),
        ], cwd=work, check=True)
    print(f"{file_name}: {(OUT / file_name).stat().st_size // 1024} KB")


if __name__ == "__main__":
    for name, (course, title, points) in VIDEOS.items():
        make(name, course, title, points)
