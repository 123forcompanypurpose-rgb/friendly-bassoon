import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { StoryCard } from "@/components/story-card";
import { getGenres, getStories } from "@/lib/api/client";
import { requireValue } from "@/lib/content";

type BrowseParams = {
  genre?: string;
  sort?: string;
  page?: string;
  q?: string;
};

const sorts = ["updated", "newest", "popular", "top-rated"] as const;
type StorySort = (typeof sorts)[number];

export const metadata: Metadata = {
  title: "Browse stories",
  description: "Explore stories by genre and find your next favourite.",
  alternates: { canonical: "/browse" },
};

function parsePage(value?: string) {
  const page = Number(value ?? 0);
  return Number.isSafeInteger(page) && page >= 0 ? page : 0;
}

function isSort(value: string | undefined): value is StorySort {
  return sorts.some((sort) => sort === value);
}

export default async function BrowsePage({
  searchParams,
}: {
  searchParams: Promise<BrowseParams>;
}) {
  const params = await searchParams;
  const genre = params.genre?.trim() || undefined;
  const sort: StorySort = isSort(params.sort) ? params.sort : "updated";
  const page = parsePage(params.page);
  const query = params.q?.trim().toLocaleLowerCase() ?? "";
  const [results, genres, t] = await Promise.all([
    getStories({ genre, sort, page, size: 20 }),
    getGenres(),
    getTranslations("Browse"),
  ]);
  const allStories = results.content ?? [];
  const stories = query
    ? allStories.filter((story) => {
        const searchable = [
          story.title,
          story.blurb,
          story.author?.displayName,
          story.author?.username,
          story.genre?.name,
          ...(story.tags ?? []).map((tag) => tag.name),
        ]
          .filter(Boolean)
          .join(" ")
          .toLocaleLowerCase();
        return searchable.includes(query);
      })
    : allStories;
  const totalPages = results.totalPages ?? 0;

  function pageHref(nextPage: number) {
    const next = new URLSearchParams();
    if (genre) next.set("genre", genre);
    if (sort !== "updated") next.set("sort", sort);
    if (query) next.set("q", query);
    next.set("page", String(nextPage));
    return `/browse?${next.toString()}`;
  }

  return (
    <main className="page-shell browse-page">
      <p className="eyebrow">{t("eyebrow")}</p>
      <h1 className="page-title">{t("title")}</h1>
      <form action="/browse" className="browse-filters glass">
        <label>
          <span>{t("genre")}</span>
          <select defaultValue={genre ?? ""} name="genre">
            <option value="">{t("allGenres")}</option>
            {genres.map((item) => (
              <option
                key={requireValue(item.id, "genre id")}
                value={requireValue(item.slug, "genre slug")}
              >
                {requireValue(item.name, "genre name")}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>{t("sort")}</span>
          <select defaultValue={sort} name="sort">
            <option value="updated">{t("updated")}</option>
            <option value="newest">{t("newest")}</option>
            <option value="popular">{t("popular")}</option>
            <option value="top-rated">{t("topRated")}</option>
          </select>
        </label>
        <label className="browse-search">
          <span>{t("filterLoadedPage")}</span>
          <input defaultValue={params.q ?? ""} name="q" type="search" />
        </label>
        <button className="primary-button filter-button" type="submit">
          {t("sort")}
        </button>
      </form>

      <div aria-live="polite" className="results-caption">
        {query
          ? t("pageMatches", { count: stories.length })
          : t("results", { count: results.totalElements ?? stories.length })}
      </div>
      {stories.length ? (
        <div className="story-grid browse-grid">
          {stories.map((story) => (
            <StoryCard
              key={requireValue(story.id, "story id")}
              story={story}
            />
          ))}
        </div>
      ) : (
        <p className="empty-state glass">{t("empty")}</p>
      )}

      {totalPages > 1 && (
        <nav aria-label={t("pagination")} className="pagination">
          {page > 0 ? (
            <Link className="nav-link" href={pageHref(page - 1)}>
              {t("previous")}
            </Link>
          ) : (
            <span aria-disabled="true" className="nav-link disabled">
              {t("previous")}
            </span>
          )}
          <span>{t("page", { page: page + 1, total: totalPages })}</span>
          {page + 1 < totalPages ? (
            <Link className="nav-link" href={pageHref(page + 1)}>
              {t("next")}
            </Link>
          ) : (
            <span aria-disabled="true" className="nav-link disabled">
              {t("next")}
            </span>
          )}
        </nav>
      )}
    </main>
  );
}
