import Image from "next/image";
import Link from "next/link";
import { cn } from "@/lib/cn";

/**
 * Official MedNet artwork, cropped and resized from the masters in public/images by
 * scripts/brand-assets.mjs. Dimensions are the derived files' pixel sizes.
 */
const LOGOS = {
  /** Full-colour logo for light surfaces. */
  dark: { src: "/brand/mednet-logo.webp", width: 490, height: 120 },
  /** Reversed logo for the dark green sidebar. */
  light: { src: "/brand/mednet-logo-reversed.webp", width: 490, height: 120 },
} as const;
const MARK = { src: "/brand/mednet-mark.webp", width: 146, height: 96 };

const HEIGHTS = {
  /** Public header: 28px on phones (room for Book and the menu button at 320px), 40px from 768px. */
  header: "h-7 md:h-10",
  /** 32px: compact headers. */
  sm: "h-8",
  /** 32px on phones, 40px from 768px. */
  md: "h-8 md:h-10",
  /** 40px everywhere (the expanded sidebar only appears from 1200px). */
  lg: "h-10",
} as const;

type WordmarkProps = {
  /** `dark` (full-colour logo) for light surfaces, `light` (reversed logo) for the green sidebar. */
  tone?: "dark" | "light";
  /** Collapsed sidebar rail shows the official favicon mark only. */
  compact?: boolean;
  size?: keyof typeof HEIGHTS;
  /** Load immediately (headers); below-the-fold uses such as the footer pass false. */
  priority?: boolean;
  href?: string;
  className?: string;
};

export function Wordmark({
  tone = "dark",
  compact = false,
  size = "md",
  priority = true,
  href = "/",
  className,
}: WordmarkProps) {
  const logo = LOGOS[tone];
  return (
    // The link carries the accessible name; the artwork inside it is decorative.
    <Link href={href} aria-label="MedNet home" className={cn("inline-flex shrink-0 items-center rounded-sm", className)}>
      {compact ? (
        // The mark's dark teal stroke disappears on the green sidebar, so it sits on a white tile.
        <span className="grid size-10 place-items-center rounded-sm bg-white">
          <Image src={MARK.src} alt="" width={MARK.width} height={MARK.height} unoptimized priority={priority} className="h-6 w-auto" />
        </span>
      ) : (
        <Image
          src={logo.src}
          alt=""
          width={logo.width}
          height={logo.height}
          unoptimized
          priority={priority}
          className={cn("w-auto max-w-none", HEIGHTS[size])}
        />
      )}
    </Link>
  );
}
