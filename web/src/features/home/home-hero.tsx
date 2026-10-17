import { ArrowRight, CalendarDays, Clock, Search, ShieldCheck, Stethoscope } from "lucide-react";
import Image from "next/image";
import { DIRECTORY_FILTER_MAX } from "@/features/find-care/provider-api";

/**
 * Reference: Homepage hero (measured at 1440/1024/768/390px: 105° brand gradient; from 1200px a
 * 650px band with the text column (70px padding) and the 435×500 arched photo centred vertically;
 * 46px display on phones, clamp(50px, 5vw, 72px) above, 0.99 line height, −0.045em tracking;
 * 20/31 lede; a white search bar with a "Find a doctor" button; three trust markers; a floating
 * availability card on the photo's bottom edge).
 *
 * Differences from the reference, all documented in DESIGN_SYSTEM.md §14:
 * - The search bar has one field. The API filters by name or specialty only; it has no location
 *   or date filter, so the reference's "Location" and "Availability" fields are not shown.
 * - "Verified providers" reads "Approved providers" (credential verification is unresolved, OQ-09).
 * - The floating card explains what the directory shows instead of a fictional next appointment.
 * The form is a plain GET to /find-care so it works before (and without) JavaScript.
 */
export function HomeHero() {
  return (
    <section
      aria-labelledby="home-title"
      className="relative overflow-hidden bg-[linear-gradient(105deg,#f9faf8_0%,#f2f6f3_56%,#e7f2ed_100%)]"
    >
      <div className="mx-auto grid max-w-page gap-5 px-4 pt-[4.875rem] pb-5 md:gap-[4.75rem] md:pb-0 md:px-6 md:pt-[6.25rem] lg:min-h-[40.625rem] lg:grid-cols-[minmax(0,1fr)_minmax(0,20rem)] lg:items-center lg:gap-12 lg:px-8 lg:py-0 xl:grid-cols-[56.875rem_27.1875rem] xl:gap-[4.75rem]">
        <div className="min-w-0 lg:py-[4.375rem]">
          <p className="flex items-center gap-[0.5625rem] text-badge font-bold tracking-[0.11em] text-action uppercase">
            <span aria-hidden className="h-0.5 w-[1.4375rem] bg-action" />
            Healthcare, made clearer
          </p>
          <h1
            id="home-title"
            className="mt-[1.3125rem] max-w-[40.625rem] text-[2.875rem] leading-[0.99] font-semibold tracking-[-0.045em] text-green-950 md:text-[clamp(3.125rem,5vw,4.5rem)]"
          >
            Trusted care, wherever you are.
          </h1>
          <p className="mt-[1.625rem] max-w-[37.5rem] text-[1.125rem] leading-[1.55] text-text-secondary md:text-[1.25rem] md:leading-[1.9375rem]">
            Find approved doctors, request appointments in person or by text consultation, and keep track of your
            care in one place.
          </p>

          <form
            action="/find-care"
            method="get"
            role="search"
            aria-label="Find a doctor"
            className="mt-[1.875rem] flex flex-col gap-3 rounded-md border border-border bg-surface p-3 shadow-overlay md:flex-row md:items-center md:pl-5 xl:max-w-[56.875rem]"
          >
            {/* The input's own outline is replaced by a ring on the whole field. */}
            <label className="flex min-h-[3.25rem] min-w-0 flex-1 cursor-text items-center gap-3 rounded-sm px-2 has-[input:focus-visible]:outline-3 has-[input:focus-visible]:outline-offset-2 has-[input:focus-visible]:outline-action">

              <Search aria-hidden size={20} strokeWidth={1.8} className="shrink-0 text-green-800" />
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="text-caption text-text-secondary">What care do you need?</span>
                <input
                  type="search"
                  name="search"
                  maxLength={DIRECTORY_FILTER_MAX}
                  placeholder="Doctor name or specialty"
                  autoComplete="off"
                  className="w-full min-w-0 bg-transparent text-body font-semibold text-ink outline-none placeholder:text-ink focus-visible:outline-none"
                />
              </span>
            </label>
            <button
              type="submit"
              className="inline-flex min-h-[3.25rem] shrink-0 items-center justify-center gap-[0.5625rem] rounded-sm bg-action px-5 text-button font-bold text-white shadow-card hover:bg-action-hover active:bg-green-900"
            >
              Find a doctor
              <ArrowRight aria-hidden size={18} strokeWidth={1.8} />
            </button>
          </form>

          <ul className="mt-[1.6875rem] flex flex-col gap-2 text-data leading-[1.5] text-text-secondary md:flex-row md:flex-wrap md:gap-x-6">
            <li className="inline-flex items-center gap-[0.4375rem]">
              <ShieldCheck aria-hidden size={17} strokeWidth={1.8} className="text-action" />
              Privacy-minded care
            </li>
            <li className="inline-flex items-center gap-[0.4375rem]">
              <Stethoscope aria-hidden size={17} strokeWidth={1.8} className="text-action" />
              Approved providers
            </li>
            <li className="inline-flex items-center gap-[0.4375rem]">
              <Clock aria-hidden size={17} strokeWidth={1.8} className="text-action" />
              Clear appointment times
            </li>
          </ul>
        </div>

        <div className="relative mx-auto w-full max-w-[19.375rem] md:max-w-[27.1875rem] lg:mx-0 lg:max-w-none">
          <div
            aria-hidden
            className="absolute -top-8 -right-24 size-[30rem] rounded-full bg-green-100/70 lg:-top-10 lg:-right-40"
          />
          <div className="relative aspect-[310/390] w-full md:aspect-[435/500] overflow-hidden rounded-[13rem_13rem_1.375rem_1.375rem] shadow-[0_28px_60px_rgba(20,70,52,0.14)]">
            <Image
              src="/images/hero-care.jpg"
              alt="Two healthcare workers reviewing information together"
              fill
              priority
              sizes="(min-width: 1200px) 435px, (min-width: 768px) 352px, 90vw"
              className="object-cover object-[58%_center]"
            />
          </div>
          <div className="absolute bottom-0 left-0 flex w-[min(17.8125rem,92%)] items-center gap-3 rounded-md bg-surface/95 p-4 shadow-overlay lg:-left-2.5">
            <span className="inline-flex size-[2.6875rem] shrink-0 items-center justify-center rounded-sm bg-green-50 text-green-800">
              <CalendarDays aria-hidden size={20} strokeWidth={1.8} />
            </span>
            <p className="min-w-0 text-badge text-text-muted">
              Open times
              <strong className="block text-button leading-[1.5] font-bold text-ink">Shown before you request</strong>
              In person or text consultation
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
