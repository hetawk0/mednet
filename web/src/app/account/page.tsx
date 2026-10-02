import type { Metadata } from "next";
import { UserAccountPortal } from "@/components/google-signin";

export const metadata: Metadata = {
  title: "Account",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default function AccountPage() {
  return <UserAccountPortal />;
}