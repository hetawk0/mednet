import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/cn";

/**
 * Reference: dashboard panels ("Next appointment", "Recent activity"). Measured: 12px radius,
 * 24px padding, 1px line border, card shadow, white surface.
 */
export function Card({
  as: Tag = "section",
  padding = "md",
  className,
  ...props
}: Omit<ComponentProps<"section">, "ref"> & { as?: "section" | "div" | "article" | "li"; padding?: "md" | "sm" | "none" }) {
  return (
    <Tag
      className={cn(
        "min-w-0 rounded-md border border-border bg-surface shadow-card",
        padding === "md" && "p-5 md:p-6",
        padding === "sm" && "p-4",
        className,
      )}
      {...props}
    />
  );
}

/**
 * Reference eyebrows: card kicker (12px bold uppercase, muted) and section kicker (11px bold
 * uppercase, action green). Text is uppercased with CSS so screen readers read normal case.
 */
export function Eyebrow({
  tone = "muted",
  size = "default",
  className,
  children,
}: {
  tone?: "muted" | "action" | "inverse";
  /** `section`: the homepage section kicker, 12px with 0.11em tracking in a 24px line box. */
  size?: "default" | "section";
  className?: string;
  children: ReactNode;
}) {
  return (
    <p
      className={cn(
        "font-bold uppercase",
        size === "default" ? "text-eyebrow tracking-[0.08em]" : "text-badge leading-6 tracking-[0.11em]",
        tone === "muted" && "text-ink-faint",
        tone === "action" && "text-action",
        tone === "inverse" && "text-green-100",
        className,
      )}
    >
      {children}
    </p>
  );
}

/** Card header: eyebrow, title (21px regular as rendered), optional trailing action or badge. */
export function CardHeader({
  eyebrow,
  title,
  action,
  titleAs: Title = "h2",
  className,
}: {
  eyebrow?: ReactNode;
  title: ReactNode;
  action?: ReactNode;
  titleAs?: "h2" | "h3" | "h4";
  className?: string;
}) {
  return (
    <div className={cn("flex flex-wrap items-start justify-between gap-x-4 gap-y-2", className)}>
      <div className="min-w-0">
        {eyebrow ? <Eyebrow>{eyebrow}</Eyebrow> : null}
        <Title className={cn("text-title font-normal text-ink [overflow-wrap:anywhere]", eyebrow ? "mt-1.5" : undefined)}>
          {title}
        </Title>
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

/** Section heading used above cards in workspaces ("Assigned work · Laboratory order queue"). */
export function SectionHeading({
  eyebrow,
  title,
  description,
  action,
  as: Heading = "h2",
}: {
  eyebrow?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  as?: "h1" | "h2" | "h3";
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0 max-w-3xl">
        {eyebrow ? <Eyebrow tone="action">{eyebrow}</Eyebrow> : null}
        <Heading className="mt-1.5 text-h2 font-normal tracking-[-0.02em] text-green-950 [overflow-wrap:anywhere] md:text-[1.875rem] md:leading-[2.75rem]">
          {title}
        </Heading>
        {description ? <p className="mt-2 text-text-secondary">{description}</p> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}
