import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";

const legalPages = {
  terms: { title: "termsTitle", body: "termsBody" },
  privacy: { title: "privacyTitle", body: "privacyBody" },
  "content-policy": {
    title: "contentPolicyTitle",
    body: "contentPolicyBody",
  },
  dmca: { title: "dmcaTitle", body: "dmcaBody" },
  refund: { title: "refundTitle", body: "refundBody" },
  contact: { title: "contactTitle", body: "contactBody" },
} as const;

type LegalSlug = keyof typeof legalPages;

function isLegalSlug(value: string): value is LegalSlug {
  return Object.prototype.hasOwnProperty.call(legalPages, value);
}

export function generateStaticParams() {
  return Object.keys(legalPages).map((slug) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const { slug } = await params;
  if (!isLegalSlug(slug)) notFound();
  const t = await getTranslations("Legal");
  return {
    title: t(legalPages[slug].title),
    robots: { index: false, follow: false },
  };
}

export default async function LegalPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  if (!isLegalSlug(slug)) notFound();
  const t = await getTranslations("Legal");
  const page = legalPages[slug];

  return (
    <main className="page-shell legal-page">
      <section className="legal-card glass">
        <p className="legal-draft-notice">{t("draftNotice")}</p>
        <h1>{t(page.title)}</h1>
        <p>{t(page.body)}</p>
      </section>
    </main>
  );
}
