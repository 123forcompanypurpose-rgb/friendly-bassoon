import { NextResponse, type NextRequest } from "next/server";

const protectedPrefixes = ["/studio", "/library", "/settings", "/admin"];

export function middleware(request: NextRequest) {
  const isProtected = protectedPrefixes.some(
    (prefix) =>
      request.nextUrl.pathname === prefix ||
      request.nextUrl.pathname.startsWith(`${prefix}/`),
  );
  if (!isProtected) return NextResponse.next();

  const hasSession =
    request.cookies.has("access_token") ||
    request.cookies.has("refresh_token");
  if (hasSession) return NextResponse.next();

  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set(
    "next",
    `${request.nextUrl.pathname}${request.nextUrl.search}`,
  );
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/studio/:path*", "/library/:path*", "/settings/:path*", "/admin/:path*"],
};
