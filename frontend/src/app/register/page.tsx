import { AuthForm } from "@/components/auth-form";
import { Suspense } from "react";

export const metadata = {
  title: "Create an account — NovelVerse",
};

export default function RegisterPage() {
  return (
    <Suspense fallback={<main className="auth-shell" />}>
      <AuthForm mode="register" />
    </Suspense>
  );
}
