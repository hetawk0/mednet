import type { Metadata } from "next";
import { GoogleSignIn } from "@/components/google-signin";

export const metadata: Metadata = {
  title: "Sign in",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default async function SignInPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string | string[] }>;
}) {
  const params = await searchParams;
  const googleError = params.error === "google";
  return <GoogleSignIn googleError={googleError} />;
}
