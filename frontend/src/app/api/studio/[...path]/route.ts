import { revalidatePath } from "next/cache";
import { NextRequest, NextResponse } from "next/server";

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

const apiBase = (
  process.env.API_URL ??
  process.env.API_INTERNAL_URL ??
  "http://127.0.0.1:8080/api/v1"
).replace(/\/+$/, "");

async function forward(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  if (
    path.length === 0 ||
    path.some((segment) => !/^[a-zA-Z0-9_-]+$/.test(segment))
  ) {
    return NextResponse.json({ message: "Invalid studio API path." }, { status: 400 });
  }

  const headers = new Headers();
  for (const name of ["accept", "content-type", "cookie", "x-requested-with"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const url = `${apiBase}/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`;
  const response = await fetch(url, {
    method: request.method,
    headers,
    body:
      request.method === "GET" || request.method === "HEAD"
        ? undefined
        : await request.arrayBuffer(),
    cache: "no-store",
    redirect: "manual",
  });

  if (response.ok && request.method !== "GET" && request.method !== "HEAD") {
    revalidatePath("/studio");
    revalidatePath("/", "page");
    revalidatePath("/browse", "page");
    if (path[0] === "stories") {
      revalidatePath("/novel/[story]", "page");
      revalidatePath("/read/[chapterId]", "page");
    }
  }

  const responseHeaders = new Headers();
  const contentType = response.headers.get("content-type");
  if (contentType) responseHeaders.set("content-type", contentType);
  for (const cookie of response.headers.getSetCookie()) {
    responseHeaders.append("set-cookie", cookie);
  }
  const body = response.status === 204 ? null : await response.arrayBuffer();
  return new NextResponse(body, {
    status: response.status,
    headers: responseHeaders,
  });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const DELETE = forward;
