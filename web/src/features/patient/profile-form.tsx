"use client";

import { useState, type FormEvent } from "react";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { TextArea, TextInput } from "@/components/ui/inputs";
import { Skeleton } from "@/components/ui/skeleton";
import { StateMessage } from "@/components/ui/state-message";
import { careDay } from "@/features/find-care/format";
import { LIMITS, patientApi, type PatientProfile } from "./patient-api";
import { usePatient } from "./patient-workspace";
import { actionError } from "@/features/workspace/use-resource";
import { NotificationPreferencesPanel } from "@/features/workspace/notification-preferences";

const empty: PatientProfile = { fullName: "", dateOfBirth: "", phoneNumber: "", address: "" };

/**
 * Patient profile (GET/PUT /patients/me/profile; 404 until first saved). A complete profile is
 * required before booking (AppointmentService returns 409 otherwise). Same fields, limits and
 * messages as the original page; validation now also runs before the request.
 */
export function PatientProfileForm() {
  const { profile, session } = usePatient();
  if (profile.state === "loading") {
    return (
      <Card aria-busy>
        <span role="status" className="sr-only">
          Loading your profile
        </span>
        <Skeleton className="h-6 w-40" />
        <Skeleton className="mt-6 h-11 w-full" />
        <Skeleton className="mt-4 h-11 w-full" />
        <Skeleton className="mt-4 h-11 w-full" />
      </Card>
    );
  }
  if (profile.state === "error") {
    return (
      <StateMessage
        kind="error"
        title="Your profile is unavailable"
        description="Your profile could not be loaded. Reload the page to try again."
      />
    );
  }
  return (
    <ProfileEditor initial={profile.state === "ready" ? profile.profile : empty} isNew={profile.state === "missing"} email={session.email} />
  );
}

function ProfileEditor({ initial, isNew, email }: { initial: PatientProfile; isNew: boolean; email: string }) {
  const { setProfile } = usePatient();
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<Partial<Record<keyof PatientProfile, string>>>({});
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ tone: "success" | "critical"; text: string } | null>(null);
  const today = careDay(new Date());
  const set = (key: keyof PatientProfile) => (event: { target: { value: string } }) => setValues({ ...values, [key]: event.target.value });

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: typeof errors = {
      fullName: values.fullName.trim() ? undefined : "Enter your full name.",
      dateOfBirth: !values.dateOfBirth
        ? "Enter your date of birth."
        : values.dateOfBirth > today
          ? "Your date of birth cannot be in the future."
          : undefined,
      phoneNumber: values.phoneNumber.trim() ? undefined : "Enter a phone number.",
      address: values.address.trim() ? undefined : "Enter your address.",
    };
    setErrors(next);
    setResult(null);
    if (Object.values(next).some(Boolean)) {
      const form = event.currentTarget;
      requestAnimationFrame(() => form.querySelector<HTMLElement>("[aria-invalid=true]")?.focus());
      return;
    }
    setBusy(true);
    try {
      const saved = await patientApi.saveProfile({
        fullName: values.fullName.trim(),
        dateOfBirth: values.dateOfBirth,
        phoneNumber: values.phoneNumber.trim(),
        address: values.address.trim(),
      });
      const clean = { fullName: saved.fullName, dateOfBirth: saved.dateOfBirth, phoneNumber: saved.phoneNumber, address: saved.address };
      setValues(clean);
      setProfile(clean);
      setResult({ tone: "success", text: "Your profile was saved." });
    } catch (error) {
      setResult({ tone: "critical", text: actionError(error, "Your profile could not be saved.") });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_19rem]">
      <Card>
        <CardHeader eyebrow="Patient profile" title="Your details" />
        <p className="mt-1.5 text-data text-text-secondary">
          These details support your patient account and are needed before you request an appointment. Do not include
          clinical notes here.
        </p>
        {isNew ? (
          <Alert tone="warning" title="Complete your profile to request appointments" className="mt-4" />
        ) : null}
        <form noValidate onSubmit={save} className="mt-5 grid gap-4 md:grid-cols-2">
          <TextInput label="Full name" autoComplete="name" required maxLength={LIMITS.fullName} value={values.fullName} error={errors.fullName} onChange={set("fullName")} />
          <TextInput label="Date of birth" type="date" autoComplete="bday" required max={today} value={values.dateOfBirth} error={errors.dateOfBirth} onChange={set("dateOfBirth")} />
          <TextInput label="Phone number" type="tel" autoComplete="tel" required maxLength={LIMITS.phoneNumber} value={values.phoneNumber} error={errors.phoneNumber} onChange={set("phoneNumber")} />
          <TextInput label="Email" type="email" value={email} readOnly description="Your sign-in email. It cannot be changed here." />
          <TextArea
            label="Address"
            autoComplete="street-address"
            required
            rows={3}
            maxLength={LIMITS.address}
            value={values.address}
            error={errors.address}
            onChange={set("address")}
            fieldClassName="md:col-span-2"
          />
          {result ? (
            <Alert
              tone={result.tone}
              live={result.tone === "critical" ? "assertive" : "polite"}
              title={result.text}
              className="md:col-span-2"
            />
          ) : null}
          <div className="md:col-span-2">
            <Button type="submit" loading={busy}>
              Save profile
            </Button>
          </div>
        </form>
      </Card>
      <Card>
        <CardHeader eyebrow="Privacy" title="Who sees these details" titleAs="h3" />
        <p className="mt-1.5 text-data text-text-secondary">
          Doctors you request an appointment with see your full name. Clinical information is kept in your records, where
          you choose which doctors can see it.
        </p>
      </Card>
      <NotificationPreferencesPanel />
    </div>
  );
}
