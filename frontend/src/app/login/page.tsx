import { AuthForm } from "@/components/auth-form";
import { Suspense } from "react";

export const metadata = {
  title: "Log in — NovelVerse",
};

export default function LoginPage() {
  return (
    <Suspense fallback={<main className="auth-shell" />}>
      <AuthForm mode="login" />
    </Suspense>
  );
}
