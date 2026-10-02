import type { Metadata } from "next";
import { GoogleSignIn } from "@/components/google-signin";

export const metadata: Metadata = {
  title: "Sign in",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default function SignInPage() {
  return <GoogleSignIn />;
}