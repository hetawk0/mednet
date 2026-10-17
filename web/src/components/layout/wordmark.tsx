import Link from "next/link";
import { cn } from "@/lib/cn";

type WordmarkProps = {
  /** `dark` text for light surfaces, `light` text for the green sidebar. */
  tone?: "dark" | "light";
  /** Collapsed sidebar rail shows the initial only. */
  compact?: boolean;
  href?: string;
  className?: string;
};

export function Wordmark({
  tone = "dark",
  compact = false,
  href = "/",
  className,
}: WordmarkProps) {
  return (
    <Link
      href={href}
      aria-label="MedNet home"
      className={cn(
        "inline-flex items-center rounded-sm text-[1.5625rem] leading-none font-bold tracking-[-0.04em]",
        tone === "dark" ? "text-green-900" : "text-white",
        className,
      )}
    >
      {compact ? "M" : "MedNet"}
    </Link>
  );
}
