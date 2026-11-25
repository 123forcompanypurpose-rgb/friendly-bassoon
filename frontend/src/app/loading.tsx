import { getTranslations } from "next-intl/server";

export default async function Loading() {
  const t = await getTranslations("Common");
  return (
    <main aria-busy="true" className="page-shell loading-page">
      <p className="eyebrow">{t("loading")}</p>
      <div className="skeleton skeleton-title" />
      <div className="skeleton-grid">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="skeleton skeleton-card" key={index} />
        ))}
      </div>
    </main>
  );
}
