import { describe, expect, it } from "vitest";
import {
  DEFAULT_READER_PREFERENCES,
  parseReaderPreferences,
} from "./reader-preferences";

describe("parseReaderPreferences", () => {
  it("uses defaults for absent or malformed local settings", () => {
    expect(parseReaderPreferences(null)).toEqual(DEFAULT_READER_PREFERENCES);
    expect(parseReaderPreferences("{")).toEqual(DEFAULT_READER_PREFERENCES);
  });

  it("keeps valid settings and replaces unsupported values", () => {
    expect(
      parseReaderPreferences(
        JSON.stringify({
          fontSize: 26,
          fontFamily: "comic",
          lineSpacing: 9,
          width: "wide",
          theme: "sepia",
        }),
      ),
    ).toEqual({
      ...DEFAULT_READER_PREFERENCES,
      fontSize: 26,
      width: "wide",
      theme: "sepia",
    });
  });
});
