import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ReaderExperience } from "@/components/reader-experience";
import { ApiError, getChapter, getStory, getStoryChapters } from "@/lib/api/client";
import { jsonLd, requireValue } from "@/lib/content";

const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"
).replace(/\/+$/, "");

async function loadChapter(id: number) {
  try {
    return await getChapter(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    throw error;
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ chapterId: string }>;
}): Promise<Metadata> {
  const { chapterId: rawId } = await params;
  const id = Number(rawId);
  if (!Number.isSafeInteger(id) || id < 1) notFound();
  const chapter = await loadChapter(id);
  const story = await getStory(requireValue(chapter.storyId, "chapter story id"));
  const title = requireValue(chapter.title, "chapter title");
  const storyTitle = requireValue(story.title, "story title");

  return {
    title: `${title} — ${storyTitle}`,
    description: `${title}, chapter ${chapter.number ?? ""} of ${storyTitle}.`,
    alternates: { canonical: `${siteUrl}/read/${id}` },
    robots: story.maturity === "MATURE" ? { index: false, follow: false } : undefined,
    openGraph: {
      type: "article",
      title: `${title} — ${storyTitle}`,
      description: story.blurb?.replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim(),
      url: `${siteUrl}/read/${id}`,
      images: story.coverUrl ? [{ url: story.coverUrl }] : undefined,
    },
  };
}

export default async function ReadChapterPage({
  params,
}: {
  params: Promise<{ chapterId: string }>;
}) {
  const { chapterId: rawId } = await params;
  const id = Number(rawId);
  if (!Number.isSafeInteger(id) || id < 1) notFound();
  const chapter = await loadChapter(id);
  const storyId = requireValue(chapter.storyId, "chapter story id");
  const [story, chapterPage] = await Promise.all([
    getStory(storyId),
    getStoryChapters(storyId),
  ]);
  const title = requireValue(chapter.title, "chapter title");
  const storyTitle = requireValue(story.title, "story title");
  const chapterJsonLd = {
    "@context": "https://schema.org",
    "@type": "Chapter",
    name: title,
    position: chapter.number,
    isPartOf: {
      "@type": "Book",
      name: storyTitle,
      author: story.author?.displayName ?? story.author?.username,
      url: `${siteUrl}/novel/${storyId}-${story.slug}`,
    },
    url: `${siteUrl}/read/${id}`,
    description: `${title} from ${storyTitle}`,
  };

  return (
    <>
      <script
        dangerouslySetInnerHTML={{ __html: jsonLd(chapterJsonLd) }}
        type="application/ld+json"
      />
      <ReaderExperience
        chapter={chapter}
        chapters={chapterPage.content ?? []}
        story={story}
      />
    </>
  );
}
