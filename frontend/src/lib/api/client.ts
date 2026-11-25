import type { components } from "./schema";

function apiBase() {
  if (typeof window !== "undefined") return "/api/v1";
  return (
    process.env.API_URL ??
    process.env.API_INTERNAL_URL ??
    process.env.NEXT_PUBLIC_API_URL ??
    "http://127.0.0.1:8080/api/v1"
  );
}

export type HomeResponse = components["schemas"]["HomeResponse"];
export type Genre = components["schemas"]["GenreResponse"];
export type Story = components["schemas"]["StoryResponse"];
export type Chapter = components["schemas"]["ChapterResponse"];
export type ChapterSummary = components["schemas"]["ChapterSummary"];
export type StoryPage = components["schemas"]["PageResponseStoryResponse"];
export type ChapterPage = components["schemas"]["PageResponseChapterSummary"];
export type UserProfile = components["schemas"]["MyProfile"];
export type SessionResponse = components["schemas"]["SessionResponse"];
export type MessageResponse = components["schemas"]["MessageResponse"];
export type RegisterRequest = components["schemas"]["RegisterRequest"];
export type LoginRequest = components["schemas"]["LoginRequest"];
export type GoogleLoginRequest = components["schemas"]["GoogleLoginRequest"];

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function responseError(response: Response): Promise<ApiError> {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  if (typeof payload === "object" && payload !== null) {
    const body = payload as { message?: unknown; code?: unknown };
    if (typeof body.message === "string") {
      return new ApiError(
        body.message,
        response.status,
        typeof body.code === "string" ? body.code : undefined,
      );
    }
  }
  return new ApiError(
    `The request failed with status ${response.status}.`,
    response.status,
  );
}

async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw await responseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function apiUrl(path: string) {
  return `${apiBase().replace(/\/+$/, "")}${path}`;
}

export async function getPublicJson<T>(
  path: string,
  revalidate = 60,
): Promise<T> {
  const response = await fetch(apiUrl(path), {
    next: { revalidate },
    headers: { Accept: "application/json" },
  });
  return decode<T>(response);
}

async function performApiRequest<T>(
  path: string,
  init: RequestInit,
  canRefresh: boolean,
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    headers.set("X-Requested-With", "novelverse");
  }

  const response = await fetch(apiUrl(path), {
    ...init,
    method,
    headers,
    credentials: "include",
    cache: "no-store",
  });
  if (
    canRefresh &&
    response.status === 401 &&
    typeof window !== "undefined" &&
    !path.startsWith("/auth/")
  ) {
    const refresh = await fetch(apiUrl("/auth/refresh"), {
      method: "POST",
      credentials: "include",
      headers: {
        Accept: "application/json",
        "X-Requested-With": "novelverse",
      },
    });
    if (refresh.ok) {
      return performApiRequest<T>(path, init, false);
    }
  }
  return decode<T>(response);
}

export function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  return performApiRequest(path, init, true);
}

export async function getServerJson<T>(
  path: string,
  cookieHeader: string,
): Promise<T> {
  const response = await fetch(apiUrl(path), {
    cache: "no-store",
    headers: {
      Accept: "application/json",
      Cookie: cookieHeader,
    },
  });
  return decode<T>(response);
}

export function getHome() {
  return getPublicJson<HomeResponse>("/home");
}

export function getGenres() {
  return getPublicJson<Genre[]>("/genres");
}

export function getStories(params: {
  genre?: string;
  page?: number;
  sort?: "updated" | "newest" | "popular" | "top-rated";
  size?: number;
}) {
  const query = new URLSearchParams();
  if (params.genre) query.set("genre", params.genre);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? 20));
  query.set("sort", params.sort ?? "updated");
  return getPublicJson<StoryPage>(`/stories?${query.toString()}`);
}

export function getStory(id: number) {
  return getPublicJson<Story>(`/stories/${id}`);
}

export function getStoryChapters(storyId: number, page = 0) {
  return getPublicJson<ChapterPage>(
    `/stories/${storyId}/chapters?page=${page}&size=50`,
  );
}

export function getChapter(id: number) {
  return getPublicJson<Chapter>(`/chapters/${id}`);
}

export function registerAccount(payload: RegisterRequest) {
  return apiRequest<MessageResponse>("/auth/register", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function loginAccount(payload: LoginRequest) {
  return apiRequest<SessionResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function loginWithGoogle(payload: GoogleLoginRequest) {
  return apiRequest<SessionResponse>("/auth/google", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function verifyEmail(token: string) {
  return apiRequest<void>("/auth/verify-email", {
    method: "POST",
    body: JSON.stringify({ token }),
  });
}

export function resendVerification(email: string) {
  return apiRequest<MessageResponse>("/auth/resend-verification", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export function requestPasswordReset(email: string) {
  return apiRequest<MessageResponse>("/auth/forgot-password", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export function resetPassword(token: string, newPassword: string) {
  return apiRequest<void>("/auth/reset-password", {
    method: "POST",
    body: JSON.stringify({ token, newPassword }),
  });
}

export function logoutAccount() {
  return apiRequest<void>("/auth/logout", { method: "POST" });
}

export function getCurrentUser() {
  return apiRequest<UserProfile>("/me");
}

export function recordChapterView(chapterId: number) {
  return apiRequest<void>(`/chapters/${chapterId}/view`, { method: "POST" });
}
