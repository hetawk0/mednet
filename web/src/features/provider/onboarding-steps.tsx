import { Circle, CircleAlert, CircleCheck, CircleDot } from "lucide-react";
import { cn } from "@/lib/cn";

/**
 * The four stages of provider sign-up. "Review" reflects `provider_applications.status`; MedNet records an
 * administrator's approval but does not independently verify credentials (OQ-09), so no step says "verified".
 */
export type OnboardingStepId = "account" | "email" | "details" | "review";
export type OnboardingStepState = "complete" | "current" | "upcoming" | "attention";

const STEPS: Array<{ id: OnboardingStepId; label: string }> = [
  { id: "account", label: "Create your account" },
  { id: "email", label: "Confirm your email" },
  { id: "details", label: "Submit your professional details" },
  { id: "review", label: "Administrator review" },
];

const SPOKEN: Record<OnboardingStepState, string> = {
  complete: "completed",
  current: "current step",
  upcoming: "not started",
  attention: "needs attention",
};

const ICONS = {
  complete: { Icon: CircleCheck, className: "text-action" },
  current: { Icon: CircleDot, className: "text-green-800" },
  upcoming: { Icon: Circle, className: "text-text-muted" },
  attention: { Icon: CircleAlert, className: "text-critical" },
} as const;

/** States for every step, given the step in progress and, optionally, a review outcome needing action. */
export function onboardingStates(
  current: OnboardingStepId | "done",
  reviewAttention = false,
): Record<OnboardingStepId, OnboardingStepState> {
  const index = current === "done" ? STEPS.length : STEPS.findIndex((step) => step.id === current);
  const states = Object.fromEntries(
    STEPS.map((step, position) => [step.id, position < index ? "complete" : position === index ? "current" : "upcoming"]),
  ) as Record<OnboardingStepId, OnboardingStepState>;
  if (reviewAttention) states.review = "attention";
  return states;
}

export function OnboardingSteps({
  states,
  detail,
  className,
}: {
  states: Record<OnboardingStepId, OnboardingStepState>;
  /** Short status text shown under a step, e.g. { review: "Not approved" }. */
  detail?: Partial<Record<OnboardingStepId, string>>;
  className?: string;
}) {
  return (
    <ol aria-label="Provider sign-up steps" className={cn("flex flex-col gap-3", className)}>
      {STEPS.map((step, position) => {
        const state = states[step.id];
        const { Icon, className: iconClass } = ICONS[state];
        return (
          <li key={step.id} aria-current={state === "current" ? "step" : undefined} className="flex items-start gap-3">
            <Icon aria-hidden size={20} strokeWidth={1.8} className={cn("mt-0.5 shrink-0", iconClass)} />
            <span className="flex flex-col">
              <span className={cn("text-data", state === "current" || state === "attention" ? "font-semibold text-ink" : "text-text-secondary")}>
                <span className="sr-only">Step {position + 1}: </span>
                {step.label}
                <span className="sr-only">, {SPOKEN[state]}</span>
              </span>
              {detail?.[step.id] ? <span className="text-caption text-text-muted">{detail[step.id]}</span> : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
