import type { Meta, StoryObj } from "@storybook/react-vite";

import { CompletionMark } from "./components/completion-mark";

/** Living view of DESIGN.md §2–§3. Values come from CSS variables, so this page cannot drift. */
const meta = {
  title: "Foundations/Tokens",
  parameters: { layout: "padded" },
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

const groups: { title: string; note: string; tokens: string[] }[] = [
  { title: "바탕과 면", note: "넓은 면은 조용하게. 구분은 그림자가 아니라 선.", tokens: ["paper", "surface", "surface-sunken", "line", "field-border"] },
  { title: "글자", note: "본문 17:1, 보조 10:1, 설명 6:1 (흰 바탕).", tokens: ["ink", "ink-2", "muted"] },
  { title: "셸", note: "먹남 사이드바가 브랜드를 진다.", tokens: ["shell", "shell-fg", "shell-muted", "shell-active", "shell-ring"] },
  { title: "행동과 강조", note: "강조색은 화면당 2곳 이하. 기본 인디고 금지.", tokens: ["primary", "primary-hover", "accent", "accent-soft", "selection"] },
  {
    title: "상태",
    note: "항상 아이콘 + 글자와 함께.",
    tokens: ["success", "success-soft", "warning", "warning-soft", "danger", "danger-soft", "info", "info-soft"],
  },
];

function Swatch({ token }: { token: string }) {
  return (
    <li className="grid gap-2">
      <span className="h-14 rounded-panel border border-line" style={{ background: `var(--${token})` }} />
      <span className="grid">
        <code className="font-mono text-caption text-ink">--{token}</code>
        <span className="text-caption text-muted tabular">
          {typeof window === "undefined"
            ? ""
            : getComputedStyle(document.documentElement).getPropertyValue(`--${token}`).trim()}
        </span>
      </span>
    </li>
  );
}

export const Colors: Story = {
  render: () => (
    <div className="grid max-w-5xl gap-10">
      {groups.map((g) => (
        <section key={g.title} className="grid gap-3">
          <div>
            <h2 className="text-title-sm font-semibold">{g.title}</h2>
            <p className="text-body-sm text-muted">{g.note}</p>
          </div>
          <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {g.tokens.map((t) => (
              <Swatch key={t} token={t} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  ),
};

const scale = [
  { cls: "text-display font-bold", name: "display 32 / 1.3", sample: "수료증" },
  { cls: "text-headline font-semibold", name: "headline 24 / 1.35", sample: "교육 현황" },
  { cls: "text-title font-semibold", name: "title 20 / 1.4", sample: "정보보안 기초" },
  { cls: "text-title-sm font-semibold", name: "title-sm 18 / 1.4", sample: "부서별 수료율" },
  { cls: "text-body", name: "body 16 / 1.75 — 학습자 본문", sample: "피싱 메일은 발신자 주소와 링크 주소를 먼저 확인합니다. 의심되면 열지 말고 보안팀에 신고하세요." },
  { cls: "text-body-sm", name: "body-sm 14 / 1.6 — 관리자 본문, 표", sample: "마감이 지났거나 임박한 사람부터 보여줍니다." },
  { cls: "text-label font-medium", name: "label 13 / 1.5", sample: "회사 이메일 도메인" },
  { cls: "text-caption", name: "caption 12 / 1.5", sample: "지난주 대비 +6명" },
];

export const Typography: Story = {
  render: () => (
    <div className="grid max-w-3xl gap-6">
      <p className="text-body-sm text-muted">
        Pretendard 한 가족. 한글 제목 행간 1.3~1.4, 본문 1.7~1.8, 음수 자간 없음. 숫자는 고정폭.
      </p>
      {scale.map((s) => (
        <div key={s.name} className="grid gap-1 border-b border-line pb-4">
          <span className="font-mono text-caption text-muted">{s.name}</span>
          <span className={s.cls}>{s.sample}</span>
        </div>
      ))}
      <div className="grid gap-1">
        <span className="font-mono text-caption text-muted">tabular-nums</span>
        <span className="grid w-40 text-right text-body-sm tabular">
          <span>1,204명</span>
          <span>38명</span>
          <span>100%</span>
        </span>
      </div>
    </div>
  ),
};

export const Completion: Story = {
  name: "수료 표시",
  render: () => (
    <div className="grid max-w-2xl gap-6">
      <p className="text-body-sm text-muted">
        수료는 일반 성공색(초록) 체크로 표시한다. 표 행과 배지는 StatusBadge의 "수료" 칩, 코스 행과 수료 순간은
        CompletionMark를 쓴다. 색만으로 구분하지 않도록 항상 "수료" 글자나 읽어 주는 이름이 함께 있다.
      </p>
      <div className="flex items-end gap-10">
        {(["md", "lg"] as const).map((size) => (
          <div key={size} className="grid justify-items-center gap-2">
            <CompletionMark size={size} completedAt="2026. 11. 6." />
            <code className="font-mono text-caption text-muted">{size}</code>
          </div>
        ))}
      </div>
    </div>
  ),
};
