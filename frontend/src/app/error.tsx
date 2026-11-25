"use client";

import { useTranslations } from "next-intl";

export default function AppError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("Common");
  return (
    <main className="page-shell error-page">
      <p className="eyebrow">{t("errorTitle")}</p>
      <h1>{t("errorBody")}</h1>
      <button className="primary-button" onClick={reset} type="button">
        {t("retry")}
      </button>
      {error.digest && (
        <p className="error-digest">
          {t("errorReference", { digest: error.digest })}
        </p>
      )}
    </main>
  );
}
