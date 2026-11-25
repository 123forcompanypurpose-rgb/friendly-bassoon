import type { Story } from "@/lib/api/client";

export function requireValue<T>(
  value: T | null | undefined,
  field: string,
): T {
  if (value === undefined || value === null) {
    throw new Error(`The API response is missing ${field}.`);
  }
  return value;
}

export function storyHref(story: Story) {
  const id = requireValue(story.id, "story id");
  const slug = requireValue(story.slug, "story slug");
  return `/novel/${id}-${slug}`;
}

export function storyIdFromRoute(value: string) {
  const match = /^(\d+)(?:-.+)?$/.exec(value);
  if (!match) return null;
  const id = Number(match[1]);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

export function jsonLd(value: unknown) {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}
