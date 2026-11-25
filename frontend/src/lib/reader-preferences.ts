export type ReaderPreferences = {
  fontSize: number;
  fontFamily: "serif" | "sans";
  lineSpacing: number;
  width: "narrow" | "medium" | "wide";
  theme: "midnight" | "lavender" | "sepia";
};

export const DEFAULT_READER_PREFERENCES: ReaderPreferences = {
  fontSize: 20,
  fontFamily: "serif",
  lineSpacing: 1.75,
  width: "medium",
  theme: "midnight",
};

const fontSizes = [14, 17, 20, 23, 26] as const;
const lineSpacings = [1.5, 1.75, 2] as const;
const families = ["serif", "sans"] as const;
const widths = ["narrow", "medium", "wide"] as const;
const themes = ["midnight", "lavender", "sepia"] as const;

function isOneOf<const T extends readonly string[]>(
  value: unknown,
  options: T,
): value is T[number] {
  return typeof value === "string" && options.some((option) => option === value);
}

export function parseReaderPreferences(
  serialized: string | null,
): ReaderPreferences {
  if (!serialized) return DEFAULT_READER_PREFERENCES;
  try {
    const value: unknown = JSON.parse(serialized);
    if (typeof value !== "object" || value === null) {
      return DEFAULT_READER_PREFERENCES;
    }
    const candidate = value as Record<string, unknown>;
    return {
      fontSize:
        fontSizes.find((size) => size === candidate.fontSize) ??
        DEFAULT_READER_PREFERENCES.fontSize,
      fontFamily: isOneOf(candidate.fontFamily, families)
        ? candidate.fontFamily
        : DEFAULT_READER_PREFERENCES.fontFamily,
      lineSpacing:
        lineSpacings.find((spacing) => spacing === candidate.lineSpacing) ??
        DEFAULT_READER_PREFERENCES.lineSpacing,
      width: isOneOf(candidate.width, widths)
        ? candidate.width
        : DEFAULT_READER_PREFERENCES.width,
      theme: isOneOf(candidate.theme, themes)
        ? candidate.theme
        : DEFAULT_READER_PREFERENCES.theme,
    };
  } catch {
    return DEFAULT_READER_PREFERENCES;
  }
}
