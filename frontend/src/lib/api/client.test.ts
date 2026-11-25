import { afterEach, describe, expect, it, vi } from "vitest";
import { getGenres } from "./client";

describe("browser API requests", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("uses the same-origin API proxy instead of browser localhost", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
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
});
