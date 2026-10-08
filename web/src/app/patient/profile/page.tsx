import type { Metadata } from "next";
import { PatientProfile } from "@/components/patient-profile";

export const metadata: Metadata = {
  title: "Patient profile",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default function PatientProfilePage() {
  return <PatientProfile />;
}
