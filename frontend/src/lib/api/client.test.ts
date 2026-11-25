import { afterEach, describe, expect, it, vi } from "vitest";
import { createStory, getGenres } from "./client";

describe("browser API requests", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("uses the same-origin API proxy instead of browser localhost", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify([]), { status: 200 }),
    );
    vi.stubGlobal("window", {});
    vi.stubGlobal("fetch", fetchMock);

    await expect(getGenres()).resolves.toEqual([]);

    expect(fetchMock).toHaveBeenCalledWith("/api/v1/genres", {
      next: { revalidate: 60 },
      headers: { Accept: "application/json" },
    });
  });

  it("sends authoring mutations through the authenticated studio proxy", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ id: 21, title: "A Quiet Orbit" }), {
        status: 201,
      }),
    );
    vi.stubGlobal("window", {});
    vi.stubGlobal("fetch", fetchMock);

    await expect(
      createStory({ title: "A Quiet Orbit", genreId: 2 }),
    ).resolves.toMatchObject({ id: 21 });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/studio/stories");
    expect(init).toMatchObject({
      method: "POST",
      credentials: "include",
      cache: "no-store",
      body: JSON.stringify({ title: "A Quiet Orbit", genreId: 2 }),
    });
    const headers = new Headers(init?.headers);
    expect(headers.get("X-Requested-With")).toBe("novelverse");
    expect(headers.get("Content-Type")).toBe("application/json");
  });
});
