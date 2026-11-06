import * as React from "react";

import { cn } from "../lib/utils";

/**
 * The signature move (DESIGN.md §1, §4): a square seal stamped in vermilion ink.
 * Shown ONLY where training is completed or a certificate is issued, so seeing a seal
 * always means "done, on record".
 *
 * - sm sits in table rows (upright: alignment first), md on course cards, lg on the
 *   completion moment and certificate (tilted -4°, like a hand stamp).
 * - `stamp` plays the one authored animation (200ms settle). Reduced motion: appears at once.
 * - Ink texture is a faint SVG displacement; legibility wins over texture.
 */
type SealMarkProps = Omit<React.ComponentProps<"span">, "children"> & {
  size?: "sm" | "md" | "lg";
  /** Play the stamping moment once (use when completion just happened). */
  stamp?: boolean;
  /** Completion date shown to assistive tech, e.g. "2026. 11. 6." */
  completedAt?: string;
};

const dimensions = {
  sm: { box: 20, border: 1.5, inset: 1.5, font: 7, tilt: 0 },
  md: { box: 40, border: 2, inset: 2.5, font: 12, tilt: 0 },
  lg: { box: 72, border: 2.5, inset: 4, font: 21, tilt: -4 },
} as const;

function SealMark({ size = "md", stamp = false, completedAt, className, style, ...props }: SealMarkProps) {
  const d = dimensions[size];
  const filterId = React.useId().replace(/:/g, "");
  const label = completedAt ? `수료 (${completedAt})` : "수료";

  return (
    <span
      data-slot="seal-mark"
      role="img"
      aria-label={label}
      className={cn("inline-flex shrink-0 text-seal", stamp && "animate-seal-in", className)}
      style={{ "--seal-rotate": `${d.tilt}deg`, transform: `rotate(${d.tilt}deg)`, ...style } as React.CSSProperties}
      {...props}
    >
      <svg width={d.box} height={d.box} viewBox={`0 0 ${d.box} ${d.box}`} aria-hidden="true" focusable="false">
        {size !== "sm" ? (
          <defs>
            <filter id={filterId} x="-5%" y="-5%" width="110%" height="110%">
              <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="7" result="noise" />
              <feDisplacementMap in="SourceGraphic" in2="noise" scale={size === "lg" ? 1.4 : 0.8} />
            </filter>
          </defs>
        ) : null}
        <g filter={size !== "sm" ? `url(#${filterId})` : undefined} fill="none" stroke="currentColor">
          <rect
            x={d.border / 2}
            y={d.border / 2}
            width={d.box - d.border}
            height={d.box - d.border}
            rx={3}
            strokeWidth={d.border}
          />
          {size !== "sm" ? (
            <rect
              x={d.border + d.inset}
              y={d.border + d.inset}
              width={d.box - 2 * (d.border + d.inset)}
              height={d.box - 2 * (d.border + d.inset)}
              rx={1.5}
              strokeWidth={0.75}
              opacity={0.7}
            />
          ) : null}
        </g>
        {size === "sm" ? (
          <text
            x="50%"
            y="54%"
            dominantBaseline="middle"
            textAnchor="middle"
            fill="currentColor"
            fontSize={d.font + 2}
            fontWeight={800}
          >
            修
          </text>
        ) : (
          <g fill="currentColor" fontWeight={800} fontSize={d.font} textAnchor="middle">
            <text x="50%" y="47%" dominantBaseline="auto">
              수
            </text>
            <text x="50%" y="53%" dominantBaseline="hanging">
              료
            </text>
          </g>
        )}
      </svg>
    </span>
  );
}

export { SealMark, type SealMarkProps };
