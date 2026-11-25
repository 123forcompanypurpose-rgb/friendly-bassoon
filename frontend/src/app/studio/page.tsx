import type { Metadata } from "next";
import { StudioWorkspace } from "@/components/studio-workspace";

export const metadata: Metadata = {
  title: "Writer Studio",
  robots: { index: false, follow: false },
};

export default function StudioPage() {
  return <StudioWorkspace />;
}
