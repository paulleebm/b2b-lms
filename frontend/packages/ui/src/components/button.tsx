import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { LoaderCircle } from "lucide-react";
import { Slot } from "radix-ui";

import { cn } from "../lib/utils";

/**
 * DESIGN.md §4 Buttons. One primary action per screen; the primary uses the shell ink,
 * so the brand colour and the main action are the same thing.
 */
const buttonVariants = cva(
  [
    "relative inline-flex shrink-0 items-center justify-center gap-2 whitespace-nowrap select-none",
    "rounded-control font-semibold transition-colors duration-150 ease-standard",
    "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent",
    "disabled:cursor-not-allowed disabled:opacity-55 aria-busy:cursor-progress",
    "[&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4 [&_svg]:stroke-[1.75]",
  ],
  {
    variants: {
      variant: {
        primary: "bg-primary text-primary-fg hover:bg-primary-hover active:bg-shell",
        secondary:
          "border border-field-border bg-surface text-ink shadow-press hover:bg-surface-sunken active:bg-line",
        ghost: "text-ink-2 hover:bg-surface-sunken hover:text-ink active:bg-line",
        danger: "bg-danger text-primary-fg hover:bg-danger/90 active:bg-danger/80",
        link: "h-auto px-0 font-medium text-accent underline underline-offset-3 hover:text-ink",
      },
      size: {
        sm: "h-8 px-3 text-label",
        md: "h-9 px-4 text-body-sm",
        lg: "h-11 px-5 text-body",
        icon: "size-9",
        "icon-sm": "size-8",
      },
    },
    compoundVariants: [{ variant: "link", className: "h-auto px-0" }],
    defaultVariants: { variant: "primary", size: "md" },
  },
);

type ButtonProps = React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    /** Render the single child element (e.g. `<a>`) with button styles. */
    asChild?: boolean;
    /** Keeps the label, adds a spinner, locks the button and sets `aria-busy`. Width does not change. */
    loading?: boolean;
  };

function Button({
  className,
  variant,
  size,
  asChild = false,
  loading = false,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const classes = cn(buttonVariants({ variant, size }), className);

  if (asChild) {
    return (
      <Slot.Root data-slot="button" className={classes} {...props}>
        {children}
      </Slot.Root>
    );
  }

  return (
    <button
      data-slot="button"
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <LoaderCircle className="animate-spin" aria-hidden="true" /> : null}
      {children}
    </button>
  );
}

export { Button, buttonVariants, type ButtonProps };
