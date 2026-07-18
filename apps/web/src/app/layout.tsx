import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Navigation } from "@/components/navigation";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
    title: {
        template: "%s | BidMyHobby",
        default: "BidMyHobby - The Ultimate Marketplace for Hobbyists",
    },
    description: "Discover, buy, and sell items for your favorite hobbies. AI-powered search, expert character profiles, and a vibrant community.",
};

export default function RootLayout({
    children,
}: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="en">
            <body className={`${inter.className} bg-background text-foreground antialiased min-h-screen flex flex-col md:flex-row pb-16 md:pb-0`}>
                <Navigation />
                <main className="flex-1 overflow-y-auto hidden-scrollbar relative bg-[url('/bg-noise.png')] bg-repeat bg-[length:100px_100px]">
                    <div className="absolute inset-0 bg-gradient-to-br from-blue-900/10 via-background to-background -z-10" />
                    {children}
                </main>
            </body>
        </html>
    );
}
