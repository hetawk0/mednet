import type { Metadata } from "next";
import { AdminPortal } from "@/components/admin-portal";

export const metadata: Metadata = {
  title: "Administration",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default function AdminPage() {
  return <AdminPortal />;
}