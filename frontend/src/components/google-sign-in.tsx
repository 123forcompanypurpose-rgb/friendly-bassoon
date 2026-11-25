"use client";

import Script from "next/script";
import { useCallback, useRef, useState } from "react";
import { useTranslations } from "next-intl";

type GoogleCredential = {
  credential: string;
};

type GoogleId = {
  initialize: (options: {
    client_id: string;
    callback: (response: GoogleCredential) => void;
  }) => void;
  renderButton: (
    element: HTMLElement,
    options: { theme: "filled_black"; size: "large"; shape: "pill"; width: number },
  ) => void;
};

declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

export function GoogleSignIn({
  onCredential,
}: {
  onCredential: (credential: string) => void;
}) {
  const t = useTranslations("Auth");
  const buttonRoot = useRef<HTMLDivElement>(null);
  const [loadError, setLoadError] = useState(false);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  const initialize = useCallback(() => {
    const google = window.google;
    if (!google || !buttonRoot.current || !clientId) {
      setLoadError(true);
      return;
    }
    google.accounts.id.initialize({
      client_id: clientId,
      callback: (response) => {
        if (response.credential) onCredential(response.credential);
      },
    });
    google.accounts.id.renderButton(buttonRoot.current, {
      theme: "filled_black",
      size: "large",
      shape: "pill",
      width: Math.min(buttonRoot.current.clientWidth, 360),
    });
  }, [clientId, onCredential]);

  if (!clientId) {
    return (
      <div className="google-signin">
        <button className="google-unavailable" disabled type="button">
          {t("google")}
        </button>
        <p className="google-hint">{t("googleUnavailable")}</p>
      </div>
    );
  }

  return (
    <div className="google-signin">
      <Script
        onError={() => setLoadError(true)}
        onLoad={initialize}
        src="https://accounts.google.com/gsi/client"
        strategy="afterInteractive"
      />
      <div aria-label={t("google")} ref={buttonRoot} />
      {loadError && <p className="form-message form-error">{t("googleError")}</p>}
    </div>
  );
}
