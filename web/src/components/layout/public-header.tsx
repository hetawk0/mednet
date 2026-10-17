"use client";

import { Menu, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { PUBLIC_HEADER_LINKS } from "./public-nav";
import { Wordmark } from "./wordmark";

/**
 * Reference: Navigation/Public header, measured at 1440px — 81px bar on 95% white with a soft
 * bottom rule, links 15/22.5 semibold 28px apart starting 42px after the wordmark, a padded
 * "Sign in" text button 10px before "Book an appointment". Like the reference, the links show
 * from 1200px; below that the reference hides them without a replacement, so the menu toggle is
 * a documented interpretation that keeps navigation (and, on phones, Sign in) reachable.
 */
export function PublicHeader() {
  const pathname = usePathname();
  const [openPath, setOpenPath] = useState<string | null>(null);
  // Closing on navigation: the menu is only open for the path it was opened on.
  const menuOpen = openPath === pathname;

  return (
    <header className="border-b border-line-soft bg-surface/95">
      <div className="mx-auto flex h-[4.3125rem] max-w-page items-center px-4 md:h-[5.0625rem] md:px-6 lg:px-8">
        <Wordmark />

        <nav aria-label="Main" className="ml-[2.625rem] hidden lg:block">
          <ul className="flex items-center gap-7">
            {PUBLIC_HEADER_LINKS.map((link) => (
              <li key={link.label}>
                <Link
                  href={link.href}
                  aria-current={pathname === link.href ? "page" : undefined}
                  className="block rounded-sm py-2 text-button leading-[1.5] font-semibold text-text-secondary hover:text-action aria-[current=page]:text-action"
                >
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div className="ml-auto flex items-center gap-2.5">
          <Link
            href="/sign-in"
            className="hidden min-h-11 items-center rounded-sm px-5 text-button font-bold text-green-800 hover:bg-action-subtle md:inline-flex"
          >
            Sign in
          </Link>
          <Link
            href="/find-care"
            className="inline-flex min-h-10 items-center rounded-sm bg-action px-3 text-caption font-bold text-white hover:bg-action-hover md:min-h-11 md:px-5 md:text-button"
          >
            Book an appointment
          </Link>
          <button
            type="button"
            className="inline-flex size-11 items-center justify-center rounded-sm border border-border text-ink lg:hidden"
            aria-expanded={menuOpen}
            aria-controls="public-mobile-menu"
            aria-label={menuOpen ? "Close menu" : "Open menu"}
            onClick={() => setOpenPath(menuOpen ? null : pathname)}
          >
            {menuOpen ? <X size={20} strokeWidth={1.8} aria-hidden /> : <Menu size={20} strokeWidth={1.8} aria-hidden />}
          </button>
        </div>
      </div>

      <nav
        id="public-mobile-menu"
        aria-label="Main"
        hidden={!menuOpen}
        className="border-t border-line-soft px-4 pb-4 md:px-6 lg:hidden"
      >
        <ul className="flex flex-col">
          {PUBLIC_HEADER_LINKS.map((link) => (
            <li key={link.label}>
              <Link
                href={link.href}
                aria-current={pathname === link.href ? "page" : undefined}
                className="flex min-h-11 items-center border-b border-line-soft text-body font-semibold text-ink aria-[current=page]:text-action"
              >
                {link.label}
              </Link>
            </li>
          ))}
          {/* Sign in is in the bar itself from 768px. */}
          <li className="md:hidden">
            <Link
              href="/sign-in"
              className="flex min-h-11 items-center border-b border-line-soft text-body font-semibold text-ink"
            >
              Sign in
            </Link>
          </li>
        </ul>
      </nav>
    </header>
  );
}
