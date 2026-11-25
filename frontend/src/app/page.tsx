import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { StoryShelf } from "@/components/story-card";
import { getHome } from "@/lib/api/client";
import { requireValue } from "@/lib/content";

export const metadata: Metadata = {
  alternates: { canonical: "/" },
};

export default async function HomePage() {
  const [data, t] = await Promise.all([getHome(), getTranslations("Home")]);
  const featured = data.featured ?? [];
  const trending = data.trending ?? [];
  const newReleases = data.newReleases ?? [];
  const recentlyUpdated = data.recentlyUpdated ?? [];
  const completed = data.completed ?? [];
  const genres = data.genres ?? [];

  return (
    <main className="page-shell home-page">
      <div aria-hidden="true" className="ambient ambient-one" />
      <div aria-hidden="true" className="ambient ambient-two" />

      <section aria-labelledby="welcome-title" className="hero">
        <p className="eyebrow">{t("eyebrow")}</p>
        <h1 id="welcome-title">
          {t("title")}
          <br />
          <span>{t("titleEmphasis")}</span>
        </h1>
        <p className="hero-copy">{t("intro")}</p>
        <Link className="primary-button" href="/browse">
          {t("browse")} <span aria-hidden="true">→</span>
        </Link>
      </section>

      {featured.length > 0 && (
        <StoryShelf title={t("featured")} stories={featured} />
      )}
      <StoryShelf title={t("trending")} stories={trending} empty={t("empty")} />
      <StoryShelf title={t("newReleases")} stories={newReleases} />
      <StoryShelf title={t("recentlyUpdated")} stories={recentlyUpdated} />
      <StoryShelf title={t("completed")} stories={completed} />
      <section aria-labelledby="genre-title" className="genre-section">
        <div className="section-heading">
          <h2 id="genre-title">{t("genres")}</h2>
        </div>
        <div className="genre-chips">
          {genres.map((genre) => (
            <Link
              className="genre-chip glass"
              href={`/browse?genre=${encodeURIComponent(requireValue(genre.slug, "genre slug"))}`}
              key={requireValue(genre.id, "genre id")}
            >
              {requireValue(genre.name, "genre name")}
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
