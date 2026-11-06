import * as React from "react";

import { cn } from "../lib/utils";

/**
 * DESIGN.md §4 Panels. A sheet of paper on the desk: white, 1px line, 8px radius, no shadow.
 * Never nest a panel in a panel; never tile identical panels to build a page.
 */
function Panel({ className, ...props }: React.ComponentProps<"section">) {
  return (
    <section
      data-slot="panel"
      className={cn("rounded-panel border border-line bg-surface", className)}
      {...props}
    />
  );
}

function PanelHeader({
  className,
  title,
  description,
  actions,
  headingLevel = 2,
  ...props
}: Omit<React.ComponentProps<"header">, "title"> & {
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  headingLevel?: 2 | 3;
}) {
  const Heading = headingLevel === 2 ? "h2" : "h3";
  return (
    <header
      data-slot="panel-header"
      className={cn("flex flex-wrap items-start justify-between gap-x-4 gap-y-2 px-5 pt-4 pb-3", className)}
      {...props}
    >
      <div className="grid min-w-0 gap-0.5">
        <Heading className="text-title-sm font-semibold text-ink">{title}</Heading>
        {description ? <p className="text-body-sm text-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </header>
  );
}

function PanelBody({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="panel-body" className={cn("px-5 pb-5", className)} {...props} />;
}

function PanelFooter({ className, ...props }: React.ComponentProps<"footer">) {
  return (
    <footer
      data-slot="panel-footer"
      className={cn("flex items-center justify-end gap-2 border-t border-line px-5 py-3", className)}
      {...props}
    />
  );
}

export { Panel, PanelHeader, PanelBody, PanelFooter };
