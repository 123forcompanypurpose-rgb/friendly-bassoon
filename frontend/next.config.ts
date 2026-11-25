import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");
const apiUrl = (
  process.env.API_URL ??
  process.env.API_INTERNAL_URL ??
  "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/, "");
const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${apiUrl}/:path*`,
      },
    ];
  },
};

export default withNextIntl(nextConfig);
