import type { Meta, StoryObj } from "@storybook/react-vite";

import { Button } from "./button";
import { Panel, PanelBody, PanelFooter, PanelHeader } from "./panel";

const meta = {
  title: "Components/Panel",
  component: Panel,
  decorators: [(Story) => <div className="max-w-xl">{Story()}</div>],
} satisfies Meta<typeof Panel>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A flat sheet on the desk: line, no shadow, no coloured left border. */
export const Default: Story = {
  render: () => (
    <Panel>
      <PanelHeader
        title="부서"
        description="멤버를 부서에 배치하면 부서 단위로 교육을 배정할 수 있습니다."
        actions={
          <Button size="sm" variant="secondary">
            부서 추가
          </Button>
        }
      />
      <PanelBody>
        <p className="text-body-sm text-ink-2">개발팀 · 영업팀 · 재무팀 · 인사팀 · 고객지원팀</p>
      </PanelBody>
      <PanelFooter>
        <Button variant="ghost" size="sm">
          취소
        </Button>
        <Button size="sm">저장</Button>
      </PanelFooter>
    </Panel>
  ),
};
