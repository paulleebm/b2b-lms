import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

/**
 * tailwind-merge must know our custom theme keys. Otherwise it reads `text-body-sm` (a font
 * size) as a text *colour*, treats it as conflicting with `text-primary-fg`, and silently drops
 * the colour — the primary button rendered ink-on-ink (1.15:1). Caught by the axe story tests.
 */
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: ["caption", "label", "body-sm", "body", "title-sm", "title", "headline", "display"],
      radius: ["badge", "control", "panel", "dialog"],
      shadow: ["press", "overlay"],
      ease: ["standard", "out-expo"],
    },
  },
});

/** Merge conditional class names and resolve Tailwind conflicts (later wins). */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
