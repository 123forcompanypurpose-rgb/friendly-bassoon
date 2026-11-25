"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";

type Theme = "dark" | "light" | "sepia";

const themes: Theme[] = ["dark", "light", "sepia"];

function isTheme(value: string | null): value is Theme {
  return themes.some((theme) => theme === value);
}

export function ThemeToggle() {
  const t = useTranslations("Reader");
  const siteT = useTranslations("Site");
  const [theme, setTheme] = useState<Theme>("dark");
  const [storageError, setStorageError] = useState(false);

  useEffect(() => {
    try {
      const storedTheme = window.localStorage.getItem("novelverse-theme");
      if (isTheme(storedTheme)) {
        setTheme(storedTheme);
        document.documentElement.dataset.theme = storedTheme;
      }
    } catch {
      setStorageError(true);
    }
  }, []);

  function chooseTheme(nextTheme: Theme) {
    setTheme(nextTheme);
    document.documentElement.dataset.theme = nextTheme;
    try {
      window.localStorage.setItem("novelverse-theme", nextTheme);
    } catch {
      setStorageError(true);
    }
  }

  const nextTheme = themes[(themes.indexOf(theme) + 1) % themes.length];
  const label =
    nextTheme === "light"
      ? t("lavender")
      : nextTheme === "sepia"
        ? t("sepia")
        : t("midnight");

  return (
    <>
      <button
        aria-label={`${t("theme")}: ${label}`}
        className="theme-toggle"
        onClick={() => chooseTheme(nextTheme)}
        type="button"
      >
        <span aria-hidden="true">{theme === "light" ? "☼" : "◐"}</span>
        <span>{label}</span>
      </button>
      {storageError && (
        <span className="sr-only" role="status">
          {siteT("themeStorageError")}
        </span>
      )}
    </>
  );
}
