"use client";

import Link from "next/link";
import { useEffect, useState, type CSSProperties } from "react";
import { useTranslations } from "next-intl";
import {
  recordChapterView,
  type Chapter,
  type ChapterSummary,
  type Story,
} from "@/lib/api/client";
import { requireValue, storyHref } from "@/lib/content";
import {
  DEFAULT_READER_PREFERENCES,
  parseReaderPreferences,
  type ReaderPreferences,
} from "@/lib/reader-preferences";

const preferenceKey = "novelverse-reader-settings";
const fontSizes = [14, 17, 20, 23, 26];
const lineSpacings = [1.5, 1.75, 2];
const widths = ["narrow", "medium", "wide"] as const;
const themes = ["midnight", "lavender", "sepia"] as const;
const fonts = ["serif", "sans"] as const;
type ReaderStyle = CSSProperties & {
  "--reader-font-size": string;
  "--reader-line-height": string;
};

function isOneOf<const Options extends readonly string[]>(
  value: string,
  options: Options,
): value is Options[number] {
  return options.some((option) => option === value);
}

export function ReaderExperience({
  story,
  chapter,
  chapters,
}: {
  story: Story;
  chapter: Chapter;
  chapters: ChapterSummary[];
}) {
  const t = useTranslations("Reader");
  const [settings, setSettings] = useState(DEFAULT_READER_PREFERENCES);
  const [viewNotice, setViewNotice] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [storageNotice, setStorageNotice] = useState(false);

  useEffect(() => {
    setViewNotice(false);
    try {
      setSettings(
        parseReaderPreferences(window.localStorage.getItem(preferenceKey)),
      );
    } catch {
      setStorageNotice(true);
    }
    const chapterId = requireValue(chapter.id, "chapter id");
    let active = true;
    recordChapterView(chapterId).catch(() => {
      if (active) setViewNotice(true);
    });
    return () => {
      active = false;
    };
  }, [chapter.id]);

  function updateSettings(next: Partial<ReaderPreferences>) {
    const updated = { ...settings, ...next };
    setSettings(updated);
    try {
      window.localStorage.setItem(preferenceKey, JSON.stringify(updated));
    } catch {
      setStorageNotice(true);
    }
  }

  const fontIndex = fontSizes.indexOf(settings.fontSize);
  const lineIndex = lineSpacings.indexOf(settings.lineSpacing);
  const readerStyle: ReaderStyle = {
    "--reader-font-size": `${settings.fontSize}px`,
    "--reader-line-height": String(settings.lineSpacing),
  };
  const chapterId = requireValue(chapter.id, "chapter id");
  const storyTitle = requireValue(story.title, "story title");
  const chapterTitle = requireValue(chapter.title, "chapter title");

  return (
    <main
      className={`page-shell reader-page reader-${settings.theme} reader-width-${settings.width}`}
      style={readerStyle}
    >
      <header className="reader-heading">
        <Link className="reader-back" href={storyHref(story)}>
          ← {t("backToStory")}
        </Link>
        <p className="eyebrow">{storyTitle}</p>
        <h1>{chapterTitle}</h1>
        <p className="reader-meta">
          {t("minutes", { count: chapter.readingTimeMinutes ?? 1 })}
        </p>
      </header>

      <details className="reader-toc glass">
        <summary>{t("contents")}</summary>
        <ol>
          {chapters.map((item) => (
            <li key={item.id}>
              <Link
                aria-current={item.id === chapterId ? "page" : undefined}
                href={`/read/${requireValue(item.id, "chapter id")}`}
              >
                {item.number}. {item.title}
              </Link>
            </li>
          ))}
        </ol>
      </details>

      <article
        className={`reader-article reader-font-${settings.fontFamily}`}
        dangerouslySetInnerHTML={{ __html: chapter.contentHtml ?? "" }}
      />
      {chapter.authorNote && (
        <aside className="author-note glass">
          <p className="eyebrow">{t("note")}</p>
          <p>{chapter.authorNote}</p>
        </aside>
      )}

      <nav aria-label={t("contents")} className="reader-navigation">
        {chapter.previousChapterId ? (
          <Link className="nav-link" href={`/read/${chapter.previousChapterId}`}>
            ← {t("previous")}
          </Link>
        ) : <span />}
        <button
          aria-expanded={settingsOpen}
          className="nav-link"
          onClick={() => setSettingsOpen((open) => !open)}
          type="button"
        >
          Aa · {t("settings")}
        </button>
        {chapter.nextChapterId ? (
          <Link className="nav-link" href={`/read/${chapter.nextChapterId}`}>
            {t("next")} →
          </Link>
        ) : <span />}
      </nav>

      {settingsOpen && (
        <section aria-label={t("settings")} className="reader-settings glass">
          <label>
            {t("fontSize")}
            <span className="setting-stepper">
              <button
                aria-label={`${t("fontSize")} -`}
                disabled={fontIndex <= 0}
                onClick={() => updateSettings({ fontSize: fontSizes[fontIndex - 1] })}
                type="button"
              >
                −
              </button>
              <output>{settings.fontSize}px</output>
              <button
                aria-label={`${t("fontSize")} +`}
                disabled={fontIndex >= fontSizes.length - 1}
                onClick={() => updateSettings({ fontSize: fontSizes[fontIndex + 1] })}
                type="button"
              >
                +
              </button>
            </span>
          </label>
          <label>
            {t("font")}
            <select
              onChange={(event) => {
                const value = event.currentTarget.value;
                if (isOneOf(value, fonts)) updateSettings({ fontFamily: value });
              }}
              value={settings.fontFamily}
            >
              <option value="serif">{t("serif")}</option>
              <option value="sans">{t("sans")}</option>
            </select>
          </label>
          <label>
            {t("spacing")}
            <span className="setting-stepper">
              <button
                aria-label={`${t("spacing")} -`}
                disabled={lineIndex <= 0}
                onClick={() => updateSettings({ lineSpacing: lineSpacings[lineIndex - 1] })}
                type="button"
              >
                −
              </button>
              <output>{settings.lineSpacing}</output>
              <button
                aria-label={`${t("spacing")} +`}
                disabled={lineIndex >= lineSpacings.length - 1}
                onClick={() => updateSettings({ lineSpacing: lineSpacings[lineIndex + 1] })}
                type="button"
              >
                +
              </button>
            </span>
          </label>
          <label>
            {t("width")}
            <select
              onChange={(event) => {
                const value = event.currentTarget.value;
                if (isOneOf(value, widths)) updateSettings({ width: value });
              }}
              value={settings.width}
            >
              {widths.map((width) => (
                <option key={width} value={width}>{t(width)}</option>
              ))}
            </select>
          </label>
          <label>
            {t("theme")}
            <select
              onChange={(event) => {
                const value = event.currentTarget.value;
                if (isOneOf(value, themes)) updateSettings({ theme: value });
              }}
              value={settings.theme}
            >
              <option value="midnight">{t("midnight")}</option>
              <option value="lavender">{t("lavender")}</option>
              <option value="sepia">{t("sepia")}</option>
            </select>
          </label>
        </section>
      )}
      {viewNotice && (
        <p className="reader-view-notice" role="status">
          {t("viewError")}
        </p>
      )}
      {storageNotice && (
        <p className="reader-view-notice" role="status">
          {t("settingsStorageError")}
        </p>
      )}
    </main>
  );
}
