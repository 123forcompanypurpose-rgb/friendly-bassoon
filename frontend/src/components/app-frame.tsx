"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useState, type FormEvent } from "react";
import {
  ApiError,
  getCurrentUser,
  getGenres,
  logoutAccount,
  type Genre,
  type UserProfile,
} from "@/lib/api/client";
import { ThemeToggle } from "@/components/theme-toggle";

const standaloneRoutes = [
  "/login",
  "/register",
  "/verify-email",
  "/forgot-password",
  "/reset-password",
];

function GenreMenu() {
  const t = useTranslations("Site");
  const [genres, setGenres] = useState<Genre[]>([]);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let active = true;
    getGenres()
      .then((items) => {
        if (active) setGenres(items);
      })
      .catch(() => {
        if (active) setLoadError(true);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <details className="genre-menu">
      <summary>{t("genres")}</summary>
      <div className="genre-menu-list">
        {loadError ? (
          <span role="status">{t("genresUnavailable")}</span>
        ) : genres.length ? (
          genres.map((genre) => (
            <Link
              href={`/browse?genre=${encodeURIComponent(genre.slug ?? "")}`}
              key={genre.id}
            >
              {genre.name}
            </Link>
          ))
        ) : (
          <Link href="/browse">{t("browse")}</Link>
        )}
      </div>
    </details>
  );
}

export function AppFrame({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const t = useTranslations("Site");
  const [user, setUser] = useState<UserProfile | null>(null);
  const [accountLoaded, setAccountLoaded] = useState(false);
  const [accountError, setAccountError] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const isStandalone = standaloneRoutes.some(
    (route) => pathname === route || pathname.startsWith(`${route}/`),
  );

  useEffect(() => {
    const hasSession = document.cookie
      .split(";")
      .some((cookie) => cookie.trim() === "session_present=1");

    if (isStandalone || !hasSession) {
      setAccountLoaded(true);
      return;
    }

    getCurrentUser()
      .then(setUser)
      .catch((error: unknown) => {
        if (!(error instanceof ApiError && error.status === 401)) {
          setAccountError(true);
        }
      })
      .finally(() => setAccountLoaded(true));
  }, [isStandalone, pathname]);

  async function signOut() {
    setSigningOut(true);
    setAccountError(false);
    try {
      await logoutAccount();
      setUser(null);
      router.replace("/");
      router.refresh();
    } catch {
      setAccountError(true);
    } finally {
      setSigningOut(false);
    }
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const query = String(formData.get("q") ?? "").trim();
    router.push(query ? `/browse?q=${encodeURIComponent(query)}` : "/browse");
  }

  if (isStandalone) return children;

  return (
    <div className="site-frame">
      <header className="app-header page-shell">
        <Link aria-label={t("brand")} className="brand" href="/">
          <span aria-hidden="true" className="brand-mark">N</span>
          <span>{t("brand")}</span>
        </Link>
        <nav aria-label={t("explore")} className="desktop-nav">
          <Link className={pathname === "/" ? "active" : ""} href="/">
            {t("home")}
          </Link>
          <Link className={pathname.startsWith("/browse") ? "active" : ""} href="/browse">
            {t("browse")}
          </Link>
          <GenreMenu />
        </nav>
        <ThemeToggle />
        <form aria-label={t("search")} className="header-search" onSubmit={search}>
          <label className="sr-only" htmlFor="site-search">{t("search")}</label>
          <input autoComplete="off" id="site-search" name="q" placeholder={t("search")} />
          <button aria-label={t("search")} type="submit">⌕</button>
        </form>
        <div className="header-actions">
          <button
            aria-label={t("notifications")}
            className="notifications-placeholder"
            disabled
            title={t("notificationsComingSoon")}
            type="button"
          >
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
            </svg>
          </button>
          <Link className="nav-cta write-link" href="/studio">{t("write")}</Link>
          {accountLoaded && user ? (
            <div className="account-menu">
              <span className="account-name">{user.displayName ?? user.username}</span>
              <button
                aria-label={t("logOut")}
                disabled={signingOut}
                onClick={signOut}
                type="button"
              >
                {signingOut ? t("loadingAccount") : t("logOut")}
              </button>
            </div>
          ) : (
            <>
              <Link className="nav-link" href="/login">{t("signIn")}</Link>
              <Link className="nav-cta" href="/register">{t("signUp")}</Link>
            </>
          )}
        </div>
      </header>
      {accountError && (
        <p className="account-error" role="status">{t("accountError")}</p>
      )}
      <div className="app-main">{children}</div>
      <footer className="app-footer page-shell">
        <span>{t("footer")}</span>
        <nav aria-label={t("legal")}>
          <Link href="/legal/terms">{t("terms")}</Link>
          <Link href="/legal/privacy">{t("privacy")}</Link>
          <Link href="/legal/content-policy">{t("contentPolicy")}</Link>
          <Link href="/legal/dmca">{t("dmca")}</Link>
          <Link href="/legal/refund">{t("refund")}</Link>
          <Link href="/legal/contact">{t("contact")}</Link>
        </nav>
      </footer>
      <nav aria-label={t("mobileNavigation")} className="mobile-tabs">
        <Link href="/">{t("home")}</Link>
        <Link href="/browse">{t("browse")}</Link>
        <Link href="/studio">{t("write")}</Link>
      </nav>
    </div>
  );
}
