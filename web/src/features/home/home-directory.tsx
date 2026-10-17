"use client";

import { ArrowRight, ChevronRight, FlaskConical } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { groupSpecialties } from "@/features/find-care/specialty";
import { DoctorCard } from "@/components/healthcare/doctor-card";
import { Eyebrow } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { formatNextAvailable } from "@/features/find-care/format";
import { listProviders, listSpecialties, type DirectoryProvider, type SpecialtyCount } from "@/features/find-care/provider-api";

/**
 * Homepage sections backed by the public directory (GET /providers/specialties, GET /providers).
 *
 * Reference: "Start with the right kind of care" (four category cards) and "Explore available care"
 * (three doctor cards marked as fictional). On the live site:
 * - category cards are the specialties approved providers actually list, with provider counts,
 *   plus laboratory requests (a supported patient workflow); nothing is invented. Counts cover the
 *   whole directory (Phase 5D: before, they came from a sample of the first 100 providers);
 * - doctor cards are real approved providers with their real next open time; there is no fee,
 *   because the API has none and payments are out of scope.
 */
const SPECIALTY_CARDS = 3;
const PROVIDER_CARDS = 3;

type Directory =
  | { state: "loading" }
  | { state: "error" }
  | { state: "ready"; specialties: SpecialtyCount[]; providers: DirectoryProvider[]; total: number };

type Featured = { provider: DirectoryProvider; nextAvailable?: string };

/** Most listed specialties, with spelling variants grouped (see find-care/specialty.ts). */
export function topSpecialties(counts: SpecialtyCount[], limit = SPECIALTY_CARDS) {
  return groupSpecialties(counts)
    .map(({ name, count }) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
    .slice(0, limit);
}

function initials(text: string) {
  const words = text.split(/[\s/&-]+/).filter(Boolean);
  return (words.length > 1 ? words[0][0] + words[1][0] : text.slice(0, 2)).toUpperCase();
}

const sectionTitle =
  "mt-[0.5625rem] text-[2.25rem] leading-[1.15] font-normal tracking-[-0.025em] text-green-950";

const categoryCard =
  "group flex h-full flex-col rounded-md border border-border bg-surface p-[1.375rem] transition-colors hover:border-action hover:bg-action-subtle";

export function HomeDirectory() {
  const [directory, setDirectory] = useState<Directory>({ state: "loading" });
  const [featured, setFeatured] = useState<Featured[] | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    // Two requests: specialty counts for the whole directory, and the first providers with their next
    // open time already attached (no availability call per provider).
    Promise.all([listSpecialties(controller.signal), listProviders({ size: PROVIDER_CARDS }, controller.signal)])
      .then(([specialties, page]) => {
        if (controller.signal.aborted) return;
        setDirectory({ state: "ready", specialties, providers: page.content, total: page.totalElements });
        setFeatured(
          page.content.map((provider) => ({
            provider,
            nextAvailable: provider.nextAvailableAt ? formatNextAvailable(provider.nextAvailableAt) : undefined,
          })),
        );
      })
      .catch(() => {
        if (!controller.signal.aborted) setDirectory({ state: "error" });
      });
    return () => controller.abort();
  }, []);

  const specialties = directory.state === "ready" ? topSpecialties(directory.specialties) : [];

  return (
    <>
      <section aria-labelledby="care-types-title" className="mx-auto max-w-page px-4 py-[5.75rem] md:px-6 lg:px-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <Eyebrow tone="action" size="section">Care for everyday needs</Eyebrow>
            <h2 id="care-types-title" className={sectionTitle}>
              Start with the right kind of care
            </h2>
          </div>
          <Link
            href="/services"
            className="inline-flex min-h-11 items-center gap-2 rounded-sm text-body font-bold text-action hover:text-action-hover"
          >
            View all services
            <ArrowRight aria-hidden size={18} strokeWidth={1.8} />
          </Link>
        </div>

        <ul className="mt-[2.125rem] grid gap-4 md:grid-cols-2 xl:grid-cols-4" aria-busy={directory.state === "loading"}>
          {directory.state === "loading"
            ? Array.from({ length: SPECIALTY_CARDS }, (_, index) => (
                <li key={index} className="rounded-md border border-border bg-surface p-5">
                  <Skeleton className="size-12" />
                  <Skeleton className="mt-5 h-5 w-2/3" />
                  <Skeleton className="mt-3 h-4 w-1/2" />
                </li>
              ))
            : specialties.map(({ name, count }) => (
                <li key={name}>
                  <Link href={`/find-care?specialty=${encodeURIComponent(name)}`} className={categoryCard}>
                    <span
                      aria-hidden
                      className="inline-flex size-12 items-center justify-center rounded-sm bg-green-50 text-caption font-bold text-action"
                    >
                      {initials(name)}
                    </span>
                    <span className="mt-3.5 text-[1.125rem] leading-[1.6875rem] font-bold text-ink [overflow-wrap:anywhere]">
                      {name}
                    </span>
                    <span className="mt-1.5 text-data leading-[1.4] text-text-secondary">
                      {count} approved {count === 1 ? "provider" : "providers"}
                    </span>
                    <span aria-hidden className="mt-auto pt-[1.8125rem] text-text-muted"><ChevronRight size={18} strokeWidth={1.8} /></span>
                  </Link>
                </li>
              ))}
          <li>
            <Link href="/services#laboratory" className={categoryCard}>
              <span aria-hidden className="inline-flex size-12 items-center justify-center rounded-sm bg-green-50 text-action">
                <FlaskConical size={20} strokeWidth={1.8} />
              </span>
              <span className="mt-3.5 text-[1.125rem] leading-[1.6875rem] font-bold text-ink">Laboratory requests</span>
              <span className="mt-1.5 text-data leading-[1.4] text-text-secondary">Request tests and follow their progress</span>
              <span aria-hidden className="mt-auto pt-[1.8125rem] text-text-muted"><ChevronRight size={18} strokeWidth={1.8} /></span>
            </Link>
          </li>
        </ul>
        {directory.state === "error" ? (
          <p role="status" className="mt-4 text-data text-text-secondary">
            Specialties could not be loaded right now.{" "}
            <Link href="/find-care" className="font-semibold text-green-800 underline">
              Browse the provider directory
            </Link>
          </p>
        ) : null}
      </section>

      <section aria-labelledby="providers-title" className="mx-auto max-w-page px-4 pb-[5.75rem] md:px-6 lg:px-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <Eyebrow tone="action" size="section">Approved providers</Eyebrow>
            <h2 id="providers-title" className={sectionTitle}>
              Explore available care
            </h2>
          </div>
          <Link
            href="/find-care"
            className="inline-flex min-h-11 items-center gap-2 rounded-sm text-body font-bold text-action hover:text-action-hover"
          >
            See all providers
            {directory.state === "ready" ? <span className="font-normal text-text-secondary">({directory.total})</span> : null}
            <ArrowRight aria-hidden size={18} strokeWidth={1.8} />
          </Link>
        </div>

        <div className="mt-[2.125rem]">
          {directory.state === "error" ? (
            <StateMessage
              kind="error"
              title="Providers could not be loaded"
              description="The provider directory is unavailable right now. Try again shortly."
            />
          ) : directory.state === "ready" && directory.providers.length === 0 ? (
            <StateMessage
              kind="empty"
              title="No providers are listed yet"
              description="Approved providers appear here once a MedNet administrator has reviewed their application."
            />
          ) : (
            <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-busy={featured === null}>
              {featured === null
                ? Array.from({ length: PROVIDER_CARDS }, (_, index) => (
                    <li key={index} className="rounded-md border border-border bg-surface p-5">
                      <Skeleton className="size-14 rounded-full" />
                      <Skeleton className="mt-5 h-5 w-2/3" />
                      <Skeleton className="mt-3 h-4 w-1/2" />
                      <Skeleton className="mt-8 h-11 w-full" />
                    </li>
                  ))
                : featured.map(({ provider, nextAvailable }) => (
                    <li key={provider.id}>
                      <DoctorCard
                        name={provider.displayName}
                        specialty={provider.specialty}
                        nextAvailable={nextAvailable}
                        approved
                        href={`/find-care?provider=${encodeURIComponent(provider.id)}`}
                        actionLabel="See open times"
                      />
                    </li>
                  ))}
            </ul>
          )}
        </div>
      </section>
    </>
  );
}
