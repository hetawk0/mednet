import type { Metadata } from "next";
import { PendingPage } from "@/features/public/pending-page";

export const metadata: Metadata = { title: "Joining MedNet as a provider", robots: { index: false } };

/** Linked from the public header/footer; content pending approval (DESIGN_SYSTEM.md §14). */
export default function Page() {
  return <PendingPage eyebrow="For providers" title="Joining MedNet as a provider" />;
}
