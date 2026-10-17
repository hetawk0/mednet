import Link from "next/link";
import { PUBLIC_FOOTER_COLUMNS } from "./public-nav";
import { Wordmark } from "./wordmark";

/**
 * Reference: public footer, measured at 1440/1024/768/390px. Brand column plus three link columns
 * (1.5fr + 3×1fr, 55px gaps) from 1200px; 1.4fr + 2×1fr with "Support and policies" wrapping
 * under the brand on tablets; brand row then two columns on phones (30px gap). The divider sits
 * inside the content width. Bottom padding is 56px rather than the reference's 95px, which only
 * clears the prototype's floating screen switcher (DESIGN_SYSTEM.md §14).
 */
export function PublicFooter() {
  return (
    <footer className="border-t border-border bg-surface pt-[3.4375rem] pb-14">
      <div className="mx-auto max-w-page px-4 md:px-6 lg:px-8">
        <div className="grid grid-cols-2 gap-x-[1.875rem] gap-y-[1.875rem] md:grid-cols-[1.4fr_1fr_1fr] md:gap-[3.4375rem] lg:grid-cols-[1.5fr_repeat(3,1fr)]">
          <div className="col-span-2 md:col-span-1">
            {/* The reference sets the wordmark in a 37.5px line box. */}
            <Wordmark className="py-[0.390625rem]" />
            <p className="mt-[1.125rem] text-body text-text-secondary">Professional healthcare access for Liberia.</p>
            <p className="mt-[0.5625rem] text-[0.8rem] leading-[1.2rem] text-text-muted">
              MedNet does not replace emergency services.
            </p>
          </div>
          {PUBLIC_FOOTER_COLUMNS.map((column) => (
            <nav key={column.title} aria-label={column.title}>
              <h2 className="mb-[0.9375rem] text-caption leading-[1.5] font-bold text-ink">{column.title}</h2>
              <ul className="flex flex-col gap-[0.5625rem]">
                {column.links.map((link) => (
                  <li key={link.label}>
                    <Link
                      href={link.href}
                      className="block w-fit rounded-sm py-0.5 text-caption leading-5 font-medium text-text-secondary hover:text-action"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
        <div className="mt-[2.3125rem] flex flex-col gap-1.5 border-t border-border pt-5 text-badge text-text-muted md:flex-row md:justify-between">
          <span>&copy; {new Date().getFullYear()} MedNet.</span>
          <span>English &middot; Liberia</span>
        </div>
      </div>
    </footer>
  );
}
