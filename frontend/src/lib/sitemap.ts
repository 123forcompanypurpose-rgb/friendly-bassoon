import { getStories, type Story } from "@/lib/api/client";
import { storyHref } from "@/lib/content";

export const siteUrl = (
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"
).replace(/\/+$/, "");
export const pageSize = 50;
export const maxUrlsPerSitemap = 5000;
const staticUrlCount = 2;
export const pagesPerSitemap = Math.floor(
  (maxUrlsPerSitemap - staticUrlCount) / pageSize,
);

export async function getSitemapCount() {
  const firstPage = await getStories({ page: 0, size: pageSize });
  return Math.max(
    1,
    Math.ceil((firstPage.totalPages ?? 0) / pagesPerSitemap),
  );
}

export async function getSitemapEntries(index: number) {
  const firstPageNumber = index * pagesPerSitemap;
  const first = await getStories({
    page: firstPageNumber,
    size: pageSize,
  });
  const pageCount = Math.min(
    pagesPerSitemap,
    Math.max(0, (first.totalPages ?? 0) - firstPageNumber),
  );
  const pages = await Promise.all(
    Array.from({ length: pageCount }, (_, offset) =>
      offset === 0
        ? Promise.resolve(first)
        : getStories({
            page: firstPageNumber + offset,
            size: pageSize,
          }),
    ),
  );
  const stories = pages.flatMap((page) => page.content ?? []);
  const staticEntries =
    index === 0
      ? [
          { url: siteUrl, lastModified: undefined },
          { url: `${siteUrl}/browse`, lastModified: undefined },
        ]
      : [];
  return [
    ...staticEntries,
    ...stories.map((story: Story) => ({
      url: `${siteUrl}${storyHref(story)}`,
      lastModified: story.updatedAt ?? story.publishedAt,
    })),
  ];
}

export function escapeXml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}
