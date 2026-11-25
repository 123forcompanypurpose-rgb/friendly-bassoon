"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useCallback, useState, type FormEvent } from "react";
import { GoogleSignIn } from "@/components/google-sign-in";
import {
  loginAccount,
  loginWithGoogle,
  registerAccount,
} from "@/lib/api/client";

type AuthFormProps = {
  mode: "login" | "register";
};

function safeDestination(next: string | null) {
  if (!next?.startsWith("/") || next.startsWith("//") || next.includes("\\")) {
    return "/";
  }
  const target = new URL(next, window.location.origin);
  return target.origin === window.location.origin
    ? `${target.pathname}${target.search}${target.hash}`
    : "/";
}

export function AuthForm({ mode }: AuthFormProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const t = useTranslations("Auth");
  const siteT = useTranslations("Site");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const isRegister = mode === "register";

  const completeGoogleSignIn = useCallback(
    async (credential: string) => {
      setError("");
      setIsSubmitting(true);
      try {
        await loginWithGoogle({
          idToken: credential,
          acceptedTerms: !isRegister || acceptedTerms,
        });
        router.replace(safeDestination(searchParams.get("next")));
        router.refresh();
      } catch (cause) {
        setError(
          cause instanceof Error ? cause.message : t("loginError"),
        );
      } finally {
        setIsSubmitting(false);
      }
    },
    [acceptedTerms, isRegister, router, searchParams, t],
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setNotice("");
    setIsSubmitting(true);

    const formData = new FormData(event.currentTarget);
    try {
      if (isRegister) {
        await registerAccount({
          email: String(formData.get("email") ?? ""),
          username: String(formData.get("username") ?? ""),
          displayName: String(formData.get("displayName") ?? ""),
          password: String(formData.get("password") ?? ""),
          acceptedTerms: formData.get("acceptedTerms") === "on",
        });
        setNotice(t("registerNotice"));
      } else {
        await loginAccount({
          identifier: String(formData.get("identifier") ?? ""),
          password: String(formData.get("password") ?? ""),
        });
        router.replace(safeDestination(searchParams.get("next")));
        router.refresh();
      }
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : isRegister
            ? t("registerError")
            : t("loginError"),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="auth-shell">
      <div aria-hidden="true" className="ambient ambient-one" />
      <div aria-hidden="true" className="ambient ambient-two" />
      <Link aria-label={siteT("brand")} className="brand auth-brand" href="/">
        <span aria-hidden="true" className="brand-mark">
          N
        </span>
        <span>{siteT("brand")}</span>
      </Link>

      <section aria-labelledby="auth-title" className="auth-card glass">
        <p className="eyebrow">
          {isRegister ? t("registerEyebrow") : t("loginEyebrow")}
        </p>
        <h1 id="auth-title">
          {isRegister ? t("registerTitle") : t("loginTitle")}
        </h1>
        <p className="auth-intro">
          {isRegister
            ? t("registerIntro")
            : t("loginIntro")}
        </p>

        <form className="auth-form" onSubmit={submit}>
          {isRegister ? (
            <>
              <label>
                {t("email")}
                <input
                  autoComplete="email"
                  name="email"
                  required
                  type="email"
                />
              </label>
              <label>
                {t("username")}
                <input
                  autoComplete="username"
                  maxLength={20}
                  minLength={3}
                  name="username"
                  pattern="[a-z0-9_]{3,20}"
                  required
                  title={t("usernameHint")}
                />
              </label>
              <label>
                {t("displayName")} <span className="optional-label">{t("optional")}</span>
                <input
                  autoComplete="nickname"
                  maxLength={60}
                  name="displayName"
                  type="text"
                />
              </label>
            </>
          ) : (
            <label>
              {t("identifier")}
              <input
                autoComplete="username"
                name="identifier"
                required
                type="text"
              />
            </label>
          )}

          <label>
            {t("password")}
            <input
              autoComplete={isRegister ? "new-password" : "current-password"}
              maxLength={72}
              minLength={8}
              name="password"
              required
              type="password"
            />
          </label>

          {isRegister && (
            <label className="terms-label">
              <input
                checked={acceptedTerms}
                name="acceptedTerms"
                onChange={(event) => setAcceptedTerms(event.target.checked)}
                required
                type="checkbox"
              />
              <span>
                {t("termsStart")}{" "}
                <Link href="/legal/terms">{t("termsOfService")}</Link> {t("termsAnd")}{" "}
                <Link href="/legal/privacy">{t("privacyPolicy")}</Link>.
              </span>
            </label>
          )}

          {error && (
            <p className="form-message form-error" role="alert">
              {error}
            </p>
          )}
          {notice && (
            <p className="form-message form-success" role="status">
              {notice}
            </p>
          )}

          <button
            className="primary-button auth-submit"
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting
              ? t("submitting")
              : isRegister
                ? t("register")
                : t("login")}
          </button>
        </form>

        {(!isRegister || acceptedTerms) && (
          <GoogleSignIn onCredential={completeGoogleSignIn} />
        )}

        <p className="auth-switch">
          {isRegister ? t("existingAccount") : t("newAccount")}{" "}
          <Link href={isRegister ? "/login" : "/register"}>
            {isRegister ? t("switchToLogin") : t("switchToRegister")}
          </Link>
        </p>
        {!isRegister && (
          <Link className="back-link" href="/forgot-password">
            {t("forgotLink")}
          </Link>
        )}
        <Link className="back-link" href="/">
          ← {t("back")}
        </Link>
      </section>
    </main>
  );
}
