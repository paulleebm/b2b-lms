import * as React from "react";
import { ChevronDown, CircleAlert } from "lucide-react";

import { cn } from "../lib/utils";

/**
 * DESIGN.md §4 Inputs & Forms, craft/form-validation.
 * Label above, hint below, error replaces the hint (with an icon), all wired with
 * aria-describedby / aria-invalid. When to show the error is the form's job
 * (first blur, then on every input while invalid, all on submit) — not this component's.
 */

function Label({ className, ...props }: React.ComponentProps<"label">) {
  return (
    <label
      data-slot="label"
      className={cn("text-label font-medium text-ink peer-disabled:opacity-55", className)}
      {...props}
    />
  );
}

const controlBase = [
  "w-full min-w-0 rounded-control border border-field-border bg-surface text-body-sm text-ink",
  "placeholder:text-muted transition-colors duration-150",
  "focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-accent focus-visible:border-accent",
  "disabled:cursor-not-allowed disabled:bg-surface-sunken disabled:text-muted",
  "aria-invalid:border-danger aria-invalid:focus-visible:outline-danger",
];

function Input({ className, type = "text", ...props }: React.ComponentProps<"input">) {
  return <input data-slot="input" type={type} className={cn(controlBase, "h-9 px-3", className)} {...props} />;
}

function Textarea({ className, ...props }: React.ComponentProps<"textarea">) {
  return (
    <textarea
      data-slot="textarea"
      className={cn(controlBase, "min-h-24 px-3 py-2 leading-relaxed", className)}
      {...props}
    />
  );
}

type SelectProps = Omit<React.ComponentProps<"select">, "size"> & {
  /** md = 36px (forms), sm = 32px (table rows, toolbars). */
  size?: "sm" | "md";
  /** Classes for the wrapper (width, margins). The native select always fills it. */
  className?: string;
};

/**
 * Native `<select>` styled like Input. Native on purpose: keyboard, type-ahead, mobile pickers
 * and screen-reader support come for free, and there is nothing to re-implement. The chevron
 * is drawn on top (pointer-events: none) so the whole control stays clickable.
 * Works inside Field: id / aria-* props are forwarded to the select.
 */
function Select({ className, size = "md", children, ...props }: SelectProps) {
  return (
    <span data-slot="select" className={cn("relative block w-full min-w-0", className)}>
      <select
        className={cn(
          controlBase,
          "appearance-none truncate pr-8",
          size === "sm" ? "h-8 pl-2.5 text-label" : "h-9 pl-3",
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown
        className="pointer-events-none absolute top-1/2 right-2.5 size-4 -translate-y-1/2 stroke-[1.75] text-muted"
        aria-hidden="true"
      />
    </span>
  );
}

type FieldProps = {
  /** Visible label. Required: placeholders are not labels. */
  label: string;
  /** Explains format or consequence ("나중에 바꿀 수 없습니다"). Hidden while an error shows. */
  hint?: React.ReactNode;
  /** Error message: names the problem and the fix. */
  error?: string;
  /** Marks the label; required fields are the norm, so we mark the optional ones instead. */
  optional?: boolean;
  className?: string;
  /** A single control (Input, Textarea, Select). id / aria-* are injected. */
  children: React.ReactElement<React.ComponentProps<"input">>;
};

function Field({ label, hint, error, optional, className, children }: FieldProps) {
  const reactId = React.useId();
  const id = children.props.id ?? `field-${reactId}`;
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = error ? errorId : hint ? hintId : undefined;

  return (
    <div data-slot="field" className={cn("grid gap-1.5", className)}>
      <Label htmlFor={id}>
        {label}
        {optional ? <span className="ml-1 font-normal text-muted">(선택)</span> : null}
      </Label>
      {React.cloneElement(children, {
        id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": describedBy,
      })}
      {error ? (
        <p id={errorId} className="flex items-start gap-1.5 text-label text-danger">
          <CircleAlert className="mt-0.5 size-3.5 shrink-0 stroke-[1.75]" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : hint ? (
        <p id={hintId} className="text-label text-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export { Field, Input, Label, Select, Textarea, type FieldProps, type SelectProps };
