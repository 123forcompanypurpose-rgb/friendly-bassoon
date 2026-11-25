import { revalidatePath } from "next/cache";
import { NextRequest, NextResponse } from "next/server";

export async function POST(request: NextRequest) {
  const secret = process.env.REVALIDATE_SECRET;
  const suppliedSecret = request.headers.get("x-revalidate-secret");

  if (!secret || suppliedSecret !== secret) {
    return NextResponse.json({ message: "Invalid revalidation secret." }, { status: 401 });
  }

  const body = await request.json().catch(() => null) as
    | { paths?: unknown }
    | null;
  const paths = body?.paths;

  if (!Array.isArray(paths) || paths.length === 0) {
    return NextResponse.json({ message: "A non-empty paths array is required." }, { status: 400 });
  }

  const validPaths = paths.filter(
    (path): path is string =>
      typeof path === "string" && path.startsWith("/") && path.length <= 200,
  );

  if (validPaths.length === 0) {
    return NextResponse.json({ message: "No valid paths were provided." }, { status: 400 });
  }

  for (const path of validPaths) {
    revalidatePath(path);
  }

  return NextResponse.json({ revalidated: validPaths });
}
