import { afterEach, describe, expect, it, vi } from "vitest";
import { getApiHealth } from "./health";

describe("getApiHealth", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it("returns the backend status when the API responds", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ status: "UP" }), { status: 200 }),
      ),
    );

    await expect(getApiHealth()).resolves.toEqual({
      available: true,
      status: "UP",
    });
  });

  it("returns an explicit unavailable status when the API cannot be reached", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));

    await expect(getApiHealth()).resolves.toEqual({
      available: false,
      status: "UNAVAILABLE",
    });
  });
});
