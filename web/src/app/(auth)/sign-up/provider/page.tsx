import type { Metadata } from "next";
import { AuthShell } from "@/features/auth/auth-shell";
import { ProviderSignUpPanel } from "@/features/auth/provider-sign-up-panel";
import { OnboardingSteps, onboardingStates } from "@/features/provider/onboarding-steps";

export const metadata: Metadata = {
  title: "Sign up as a healthcare provider",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default function ProviderSignUpPage() {
  return (
    <AuthShell
      eyebrow="For healthcare providers"
      title="Join MedNet as a healthcare provider."
      description={
        <>
          <p>
            Create your account, confirm your email, then submit your professional details. Scheduling and patient work
            open after a MedNet administrator approves your application.
          </p>
          <OnboardingSteps states={onboardingStates("account")} className="mt-6" />
        </>
      }
    >
      <ProviderSignUpPanel />
    </AuthShell>
  );
}
