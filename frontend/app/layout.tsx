import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Base Breakouts — multi-year base breakout scanner",
  description:
    "A daily ranked shortlist of stocks breaking out of multi-year bases, with the evidence and an honest, walk-forward-validated track record. Analytics, not advice.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
