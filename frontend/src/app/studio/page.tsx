import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

export const metadata: Metadata = {
  title: "Writer Studio",
  robots: { index: false, follow: false },
};

export default async function StudioPlaceholderPage() {
  const t = await getTranslations("Site");

  return (
    <main className="page-shell error-page">
      <p className="eyebrow">{t("write")}</p>
      <h1>{t("studioComingSoon")}</h1>
      <p>{t("studioComingSoonBody")}</p>
      <Link className="primary-button" href="/browse">{t("browse")}</Link>
    </main>
  );
}
