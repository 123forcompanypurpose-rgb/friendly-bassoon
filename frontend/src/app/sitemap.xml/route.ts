import { getSitemapCount, siteUrl, escapeXml } from "@/lib/sitemap";

export const dynamic = "force-dynamic";

export async function GET() {
  const sitemapCount = await getSitemapCount();
  const sitemaps = Array.from({ length: sitemapCount }, (_, index) => {
    const url = `${siteUrl}/sitemaps/${index}`;
    return `<sitemap><loc>${escapeXml(url)}</loc></sitemap>`;
  });
  const xml =
    '<?xml version="1.0" encoding="UTF-8"?>' +
    '<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">' +
    sitemaps.join("") +
    "</sitemapindex>";
  return new Response(xml, {
    headers: {
      "Cache-Control": "public, s-maxage=3600, stale-while-revalidate=86400",
      "Content-Type": "application/xml; charset=utf-8",
    },
  });
}
