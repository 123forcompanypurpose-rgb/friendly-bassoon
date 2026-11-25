import {
  escapeXml,
  getSitemapCount,
  getSitemapEntries,
} from "@/lib/sitemap";

export const dynamic = "force-dynamic";

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const index = Number(id);
  if (!Number.isSafeInteger(index) || index < 0) {
    return new Response("Invalid sitemap index.", { status: 404 });
  }
  const sitemapCount = await getSitemapCount();
  if (index >= sitemapCount) {
    return new Response("Sitemap not found.", { status: 404 });
  }
  const entries = await getSitemapEntries(index);
  const urls = entries.map(({ url, lastModified }) => {
    const lastMod = lastModified
      ? `<lastmod>${escapeXml(lastModified)}</lastmod>`
      : "";
    return `<url><loc>${escapeXml(url)}</loc>${lastMod}</url>`;
  });
  const xml =
    '<?xml version="1.0" encoding="UTF-8"?>' +
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' +
    urls.join("") +
    "</urlset>";
  return new Response(xml, {
    headers: {
      "Cache-Control": "public, s-maxage=3600, stale-while-revalidate=86400",
      "Content-Type": "application/xml; charset=utf-8",
    },
  });
}
