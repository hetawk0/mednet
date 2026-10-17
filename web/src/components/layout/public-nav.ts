/**
 * Public navigation from the reference header and footer. Every entry has a route: pages whose
 * content is not approved yet (For providers, Help, Contact, Privacy, Accessibility, Terms) are
 * placeholder pages that say so (features/public/pending-page.tsx), so no link leads nowhere.
 */
export type PublicLink = { label: string; href: string };

export const PUBLIC_HEADER_LINKS: PublicLink[] = [
  { label: "Find care", href: "/find-care" },
  { label: "How it works", href: "/about" },
  { label: "For providers", href: "/for-providers" },
  { label: "Help", href: "/help" },
];

export const PUBLIC_FOOTER_COLUMNS: Array<{ title: string; links: PublicLink[] }> = [
  {
    title: "Get care",
    links: [
      { label: "Find a doctor", href: "/find-care" },
      { label: "How it works", href: "/about" },
      { label: "Laboratory services", href: "/services#laboratory" },
    ],
  },
  {
    title: "Company",
    links: [
      { label: "For providers", href: "/for-providers" },
      { label: "About MedNet", href: "/about" },
      { label: "Contact", href: "/contact" },
    ],
  },
  {
    title: "Support and policies",
    links: [
      { label: "Help center", href: "/help" },
      { label: "Privacy", href: "/privacy" },
      { label: "Accessibility", href: "/accessibility" },
      { label: "Terms of service", href: "/terms" },
    ],
  },
];
