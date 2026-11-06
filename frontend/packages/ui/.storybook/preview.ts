import type { Preview } from "@storybook/react-vite";

import "./storybook.css";

const preview: Preview = {
  parameters: {
    layout: "padded",
    backgrounds: {
      options: {
        paper: { name: "paper (앱 바탕)", value: "#f7f7f4" },
        surface: { name: "surface (패널)", value: "#ffffff" },
        shell: { name: "shell (사이드바)", value: "#1d2738" },
      },
    },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    a11y: {
      // axe violations fail the story test (docs/design/REVIEW.md, step 1).
      test: "error",
    },
    options: {
      storySort: {
        order: ["Foundations", "Components", "Screens"],
      },
    },
  },
  initialGlobals: {
    backgrounds: { value: "paper" },
  },
  tags: ["autodocs"],
};

export default preview;
