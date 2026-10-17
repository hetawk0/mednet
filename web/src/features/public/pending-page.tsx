import { PageIntro } from "@/components/layout/page-intro";
import { ButtonLink } from "@/components/ui/button";

/**
 * Placeholder for public pages linked from the reference header and footer whose content is not
 * approved yet. It states that plainly and offers working routes instead; it must not carry
 * invented policy, legal, clinical or contact details (AGENTS.md).
 */
export function PendingPage({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <main id="main-content">
      <PageIntro
        eyebrow={eyebrow}
        title={title}
        actions={
          <>
            <ButtonLink href="/find-care">Find a doctor</ButtonLink>
            <ButtonLink href="/about" variant="secondary">
              How MedNet works
            </ButtonLink>
          </>
        }
      >
        <p>This page is being prepared. Its content has not been approved for publication yet.</p>
      </PageIntro>
    </main>
  );
}
