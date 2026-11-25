import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { getStory, getStoryChapters } from "@/lib/api/client";
import { jsonLd, requireValue, storyHref, storyIdFromRoute } from "@/lib/content";
import { ApiError } from "@/lib/api/client";

const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"
).replace(/\/+$/, "");

async function loadStory(route: string) {
  const id = storyIdFromRoute(route);
  if (!id) notFound();
  try {
    return await getStory(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ story: string }>;
}): Promise<Metadata> {
  const { story: route } = await params;
  const story = await loadStory(route);
  const url = `${siteUrl}/novel/${requireValue(story.id, "story id")}-${requireValue(story.slug, "story slug")}`;
  const title = requireValue(story.title, "story title");
  const description =
    story.blurb?.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim() ?? title;
  const isMature = story.maturity === "MATURE";

  return {
    title,
    description,
    alternates: { canonical: url },
    robots: isMature ? { index: false, follow: false } : undefined,
    openGraph: {
      type: "book",
      title,
      description,
      url,
      images: story.coverUrl ? [{ url: story.coverUrl }] : undefined,
    },
    twitter: {
      card: story.coverUrl ? "summary_large_image" : "summary",
      title,
      description,
      images: story.coverUrl ? [story.coverUrl] : undefined,
    },
  };
}

export default async function StoryPage({
  params,
}: {
  params: Promise<{ story: string }>;
}) {
  const { story: route } = await params;
  const story = await loadStory(route);
  const storyId = requireValue(story.id, "story id");
  const canonicalPath = storyHref(story);
  if (route !== canonicalPath.slice("/novel/".length)) {
    permanentRedirect(canonicalPath);
  }
  const title = requireValue(story.title, "story title");
  const author = requireValue(story.author, "story author");
  const authorName = requireValue(
    author.displayName ?? author.username,
    "author display name",
  );
  const [chapterPage, t] = await Promise.all([
    getStoryChapters(storyId),
    getTranslations("Story"),
  ]);
  const chapters = chapterPage.content ?? [];
  const url = `${siteUrl}${canonicalPath}`;
  const bookJsonLd = {
    "@context": "https://schema.org",
    "@type": "Book",
    name: title,
    author: {
      "@type": "Person",
      name: authorName,
    },
    genre: story.genre?.name,
    description: story.blurb?.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim(),
    url,
    image: story.coverUrl ?? undefined,
    inLanguage: story.language ?? "en",
  };

  return (
    <main className="page-shell story-page">
      <script
        dangerouslySetInnerHTML={{ __html: jsonLd(bookJsonLd) }}
        type="application/ld+json"
      />
      <section className="story-hero glass">
        <div className="story-detail-cover">
          {story.coverUrl ? (
            <Image
              alt={`${title} cover`}
              fill
              priority
              sizes="(max-width: 700px) 60vw, 260px"
              src={story.coverUrl}
              unoptimized
            />
          ) : (
            <span aria-hidden="true">{title.slice(0, 1)}</span>
          )}
        </div>
        <div className="story-detail-copy">
          <p className="eyebrow">{story.genre?.name ?? ""}</p>
          <h1>{title}</h1>
          <p className="story-author">{t("by", { author: authorName })}</p>
          <div className="story-tags">
            {(story.tags ?? []).map((tag) => (
              <span className="genre-chip glass" key={tag.id}>{tag.name}</span>
            ))}
            {story.maturity === "MATURE" && <span className="mature-chip">{t("mature")}</span>}
          </div>
          <div className="story-stat-row">
            <span>{story.chapterCount ?? 0} {t("chapters")}</span>
            <span>{(story.wordCount ?? 0).toLocaleString()} {t("words")}</span>
            <span>{(story.viewCount ?? 0).toLocaleString()} {t("views")}</span>
          </div>
          {chapters[0]?.id ? (
            <Link className="primary-button" href={`/read/${chapters[0].id}`}>
              {t("startReading")} <span aria-hidden="true">→</span>
            </Link>
          ) : (
            <span className="empty-inline">{t("noChapters")}</span>
          )}
        </div>
      </section>

      <section className="story-description glass">
        <h2>{title}</h2>
        <p className="story-blurb">{story.blurb}</p>
      </section>

      <section aria-labelledby="chapter-list-title" className="chapter-list-section">
        <div className="section-heading">
          <h2 id="chapter-list-title">{t("chapterList")}</h2>
          <span>{chapters.length}</span>
        </div>
        {chapters.length ? (
          <ol className="chapter-list">
            {chapters.map((chapter) => (
              <li className="glass" key={chapter.id}>
                <Link href={`/read/${requireValue(chapter.id, "chapter id")}`}>
                  <span>{String(chapter.number ?? 0).padStart(2, "0")}</span>
                  <strong>{chapter.title}</strong>
                  <small>{chapter.wordCount ?? 0} {t("words").toLowerCase()}</small>
                </Link>
              </li>
            ))}
          </ol>
        ) : (
          <p className="empty-state glass">{t("noChapters")}</p>
        )}
      </section>
    </main>
  );
}
