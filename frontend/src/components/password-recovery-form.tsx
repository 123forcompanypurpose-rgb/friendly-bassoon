"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import {
  resendVerification,
  requestPasswordReset,
  resetPassword,
  verifyEmail,
} from "@/lib/api/client";

export function PasswordRecoveryForm({
  mode,
  token,
}: {
  mode: "forgot" | "reset";
  token?: string;
}) {
  const t = useTranslations("Recovery");
  const siteT = useTranslations("Site");
  const [success, setSuccess] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setSubmitting(true);
    const formData = new FormData(event.currentTarget);
    try {
      if (mode === "forgot") {
        await requestPasswordReset(String(formData.get("email") ?? ""));
        setSuccess(t("successForgot"));
      } else {
        if (!token) throw new Error(t("error"));
        await resetPassword(
          token,
          String(formData.get("newPassword") ?? ""),
        );
        setSuccess(t("successReset"));
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t("error"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-shell">
      <div aria-hidden="true" className="ambient ambient-one" />
      <div aria-hidden="true" className="ambient ambient-two" />
      <Link aria-label={siteT("brand")} className="brand auth-brand" href="/">
        <span aria-hidden="true" className="brand-mark">N</span>
        <span>{siteT("brand")}</span>
      </Link>
      <section className="auth-card glass">
        <p className="eyebrow">
          {mode === "forgot" ? t("forgotTitle") : t("resetTitle")}
        </p>
        <h1>{mode === "forgot" ? t("forgotTitle") : t("resetTitle")}</h1>
        <p className="auth-intro">
          {mode === "forgot" ? t("forgotIntro") : t("resetIntro")}
        </p>
        <form className="auth-form" onSubmit={submit}>
          {mode === "forgot" ? (
            <label>
              {t("email")}
              <input autoComplete="email" name="email" required type="email" />
            </label>
          ) : (
            <label>
              {t("newPassword")}
              <input
                autoComplete="new-password"
                minLength={8}
                name="newPassword"
                required
                type="password"
              />
            </label>
          )}
          {(error || (mode === "reset" && !token)) && (
            <p className="form-message form-error" role="alert">
              {error || t("error")}
            </p>
          )}
          {success && <p className="form-message form-success" role="status">{success}</p>}
          <button className="primary-button auth-submit" disabled={submitting} type="submit">
            {submitting
              ? t("submitting")
              : mode === "forgot"
                ? t("submitForgot")
                : t("submitReset")}
          </button>
        </form>
        <Link className="back-link" href="/login">{t("backToLogin")}</Link>
      </section>
    </main>
  );
}

export function VerifyEmailStatus({ token }: { token: string }) {
  const t = useTranslations("Recovery");
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    token ? "loading" : "error",
  );
  const [resending, setResending] = useState(false);
  const [resendError, setResendError] = useState(false);
  const [resendSuccess, setResendSuccess] = useState(false);

  useEffect(() => {
    if (!token) return;
    let active = true;
    verifyEmail(token)
      .then(() => {
        if (active) setStatus("success");
      })
      .catch(() => {
        if (active) setStatus("error");
      });
    return () => {
      active = false;
    };
  }, [token]);

  async function resend(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setResendError(false);
    setResendSuccess(false);
    setResending(true);
    const formData = new FormData(event.currentTarget);
    try {
      await resendVerification(String(formData.get("email") ?? ""));
      setResendSuccess(true);
    } catch {
      setResendError(true);
    } finally {
      setResending(false);
    }
  }

  return (
    <main className="auth-shell">
      <div aria-hidden="true" className="ambient ambient-one" />
      <div aria-hidden="true" className="ambient ambient-two" />
      <section aria-live="polite" className="auth-card glass">
        <p className="eyebrow">{t("verifyTitle")}</p>
        <h1>{t("verifyTitle")}</h1>
        <p className={`form-message ${status === "success" ? "form-success" : status === "error" ? "form-error" : ""}`}>
          {status === "loading"
            ? t("verifyWorking")
            : status === "success"
              ? t("verifySuccess")
              : t("verifyError")}
        </p>
        {status === "error" && (
          <form className="auth-form" onSubmit={resend}>
            <h2>{t("resendTitle")}</h2>
            <label>
              {t("resendEmail")}
              <input autoComplete="email" name="email" required type="email" />
            </label>
            {resendError && (
              <p className="form-message form-error" role="alert">
                {t("resendError")}
              </p>
            )}
            {resendSuccess && (
              <p className="form-message form-success" role="status">
                {t("resendSuccess")}
              </p>
            )}
            <button
              className="primary-button auth-submit"
              disabled={resending}
              type="submit"
            >
              {resending ? t("submitting") : t("resendButton")}
            </button>
          </form>
        )}
        {status !== "loading" && (
          <Link className="back-link" href="/login">{t("backToLogin")}</Link>
        )}
      </section>
    </main>
  );
}
