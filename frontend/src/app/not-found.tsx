import Link from "next/link";
import { getTranslations } from "next-intl/server";

export default async function NotFound() {
  const t = await getTranslations("Common");
  return (
    <main className="page-shell error-page">
      <p className="eyebrow">{t("notFoundTitle")}</p>
      <h1>{t("notFoundBody")}</h1>
      <Link className="primary-button" href="/browse">{t("retry")}</Link>
    </main>
  );
}
