import type { Metadata } from "next";
import { PatientDashboard } from "@/components/patient-dashboard";

export const metadata: Metadata = {
  title: "Patient dashboard",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default function PatientPage() {
  return <PatientDashboard />;
}
