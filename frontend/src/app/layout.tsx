import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getMessages } from "next-intl/server";
import { AppFrame } from "@/components/app-frame";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",
  ),
  title: {
    default: "NovelVerse — Stories for every night",
    template: "%s — NovelVerse",
  },
  description:
    "Discover your next favourite story and follow every chapter on NovelVerse.",
  openGraph: {
    type: "website",
    siteName: "NovelVerse",
    title: "NovelVerse — Stories for every night",
    description:
      "Discover your next favourite story and follow every chapter on NovelVerse.",
  },
  twitter: {
    card: "summary",
    title: "NovelVerse — Stories for every night",
    description:
      "Discover your next favourite story and follow every chapter on NovelVerse.",
  },
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const messages = await getMessages();

  return (
    <html lang="en" suppressHydrationWarning>
      <body>
        <NextIntlClientProvider locale="en" messages={messages}>
          <AppFrame>{children}</AppFrame>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
