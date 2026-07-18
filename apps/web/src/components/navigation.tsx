"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Home, Search, Upload, Menu, X, LogOut, Package, MessageCircle } from "lucide-react";
import { useState, useEffect } from "react";
import { cn } from "@/lib/utils";

const navItems = [
    { name: "Home", href: "/", icon: Home },
    { name: "AI Profiles", href: "/dashboard", icon: Search },
    { name: "Upload", href: "/upload", icon: Upload },
];

/** Masks a phone/email for display in nav. */
function maskForNav(identity: string): string {
    if (!identity) return "Guest";
    if (identity.startsWith("+") || /^\d{7,}$/.test(identity)) {
        const digits = identity.replace(/\D/g, "");
        return `+${"•".repeat(Math.max(0, digits.length - 4))}${digits.slice(-4)}`;
    }
    const atIdx = identity.indexOf("@");
    if (atIdx > 0) {
        const u = identity.slice(0, atIdx);
        return `${u.slice(0, 2)}${"•".repeat(Math.min(u.length - 2, 4))}${identity.slice(atIdx)}`;
    }
    return identity.slice(0, 12);
}

export function Navigation() {
    const pathname = usePathname();
    const router = useRouter();
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const [userEmail, setUserEmail] = useState<string | null>(null);

    // Read user from localStorage (also listens for the custom login/logout events)
    useEffect(() => {
        const read = () => setUserEmail(localStorage.getItem("userEmail"));
        read();
        window.addEventListener("userLogin", read);
        window.addEventListener("userLogout", read);
        return () => {
            window.removeEventListener("userLogin", read);
            window.removeEventListener("userLogout", read);
        };
    }, []);

    const handleLogout = () => {
        localStorage.removeItem("userEmail");
        window.dispatchEvent(new Event("userLogout"));
        setIsMobileMenuOpen(false);
        router.push("/login");
    };

    const UserBadge = () =>
        userEmail ? (
            <div className="mt-auto px-3 pb-4 pt-3 border-t border-white/5">
                {/* WhatsApp identity */}
                <div className="flex items-center gap-2.5 px-3 py-2 mb-2 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                    <MessageCircle className="w-4 h-4 text-emerald-400 shrink-0" />
                    <div className="min-w-0">
                        <p className="text-[10px] text-emerald-400 font-semibold uppercase tracking-wider">Logged in</p>
                        <p className="text-white text-xs font-mono truncate">{maskForNav(userEmail)}</p>
                    </div>
                </div>
                {/* My Items shortcut */}
                <Link
                    href="/?myItems=1"
                    className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-white/5 transition-colors mb-1"
                >
                    <Package className="w-4 h-4" /> My Items
                </Link>
                {/* Logout */}
                <button
                    onClick={handleLogout}
                    className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-slate-500 hover:text-red-400 hover:bg-red-500/5 transition-colors"
                >
                    <LogOut className="w-4 h-4" /> Log Out
                </button>
            </div>
        ) : (
            <div className="mt-auto px-3 pb-4 pt-3 border-t border-white/5">
                <Link
                    href="/login"
                    className="flex items-center justify-center gap-2 w-full px-4 py-2.5 rounded-xl text-sm font-semibold bg-emerald-600/20 hover:bg-emerald-600/30 border border-emerald-500/30 text-emerald-300 transition-all"
                >
                    <MessageCircle className="w-4 h-4" /> Login with WhatsApp
                </Link>
                <p className="text-center text-[10px] text-slate-600 mt-2">© {new Date().getFullYear()} BidMyHobby</p>
            </div>
        );

    return (
        <>
            {/* ─── Desktop Sidebar ─── */}
            <aside className="hidden md:flex flex-col w-64 glass-nav h-screen sticky top-0 px-4 py-8">
                <div className="mb-10 pl-2">
                    <Link href="/">
                        <h1 className="text-2xl font-black tracking-tight text-white hover:opacity-80 transition-opacity">
                            BidMy<span className="text-blue-500">Hobby</span>
                        </h1>
                        <p className="text-[10px] text-slate-500 mt-0.5 pl-0.5">Auction. Connect. Discover.</p>
                    </Link>
                </div>

                <nav className="flex-1 space-y-1.5">
                    {navItems.map((item) => {
                        const isActive = pathname === item.href;
                        return (
                            <Link
                                key={item.href}
                                href={item.href}
                                className={cn(
                                    "flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200",
                                    isActive
                                        ? "bg-blue-600/20 text-blue-300 border border-blue-500/30 shadow-[0_0_12px_rgba(59,130,246,0.08)]"
                                        : "text-slate-400 hover:text-white hover:bg-white/5"
                                )}
                            >
                                <item.icon className={cn("w-5 h-5", isActive ? "text-blue-400" : "")} />
                                {item.name}
                                {isActive && <span className="ml-auto w-1.5 h-1.5 rounded-full bg-blue-400" />}
                            </Link>
                        );
                    })}
                </nav>

                <UserBadge />
            </aside>

            {/* ─── Mobile Top Bar ─── */}
            <header className="md:hidden glass-nav sticky top-0 z-50 px-4 py-3.5 flex items-center justify-between">
                <Link href="/">
                    <h1 className="text-xl font-black text-white">
                        BidMy<span className="text-blue-500">Hobby</span>
                    </h1>
                </Link>

                <div className="flex items-center gap-3">
                    {userEmail && (
                        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs font-mono">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                            {maskForNav(userEmail)}
                        </div>
                    )}
                    <button
                        onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                        className="p-2 text-slate-300 hover:text-white rounded-lg hover:bg-white/5 transition-colors"
                    >
                        {isMobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
                    </button>
                </div>
            </header>

            {/* ─── Mobile Dropdown Menu ─── */}
            {isMobileMenuOpen && (
                <div className="md:hidden fixed inset-0 z-40 bg-background/95 backdrop-blur-xl pt-20 px-4 flex flex-col">
                    <nav className="flex flex-col space-y-3">
                        {navItems.map((item) => {
                            const isActive = pathname === item.href;
                            return (
                                <Link
                                    key={item.href}
                                    href={item.href}
                                    onClick={() => setIsMobileMenuOpen(false)}
                                    className={cn(
                                        "flex items-center gap-4 px-6 py-4 rounded-2xl text-lg font-medium transition-all duration-200",
                                        isActive
                                            ? "bg-blue-600/20 text-blue-300 border border-blue-500/30"
                                            : "text-slate-300 hover:text-white hover:bg-white/5"
                                    )}
                                >
                                    <item.icon className="w-6 h-6" />
                                    {item.name}
                                </Link>
                            );
                        })}
                    </nav>

                    <div className="mt-auto pb-8 space-y-3">
                        {userEmail ? (
                            <>
                                <div className="flex items-center gap-3 px-6 py-3 rounded-2xl bg-emerald-500/10 border border-emerald-500/20">
                                    <MessageCircle className="w-5 h-5 text-emerald-400" />
                                    <div>
                                        <p className="text-xs text-emerald-400 font-semibold">Logged in</p>
                                        <p className="text-white text-sm font-mono">{maskForNav(userEmail)}</p>
                                    </div>
                                </div>
                                <Link href="/?myItems=1" onClick={() => setIsMobileMenuOpen(false)}
                                    className="flex items-center gap-3 px-6 py-3 rounded-2xl text-slate-300 hover:bg-white/5 transition-colors">
                                    <Package className="w-5 h-5" /> My Items
                                </Link>
                                <button onClick={handleLogout}
                                    className="w-full flex items-center gap-3 px-6 py-3 rounded-2xl text-red-400 hover:bg-red-500/10 transition-colors">
                                    <LogOut className="w-5 h-5" /> Log Out
                                </button>
                            </>
                        ) : (
                            <Link href="/login" onClick={() => setIsMobileMenuOpen(false)}
                                className="flex items-center justify-center gap-2 w-full px-6 py-4 rounded-2xl font-semibold bg-emerald-600/20 border border-emerald-500/30 text-emerald-300">
                                <MessageCircle className="w-5 h-5" /> Login with WhatsApp
                            </Link>
                        )}
                    </div>
                </div>
            )}

            {/* ─── Mobile Bottom Tab Bar ─── */}
            <div className="md:hidden fixed bottom-0 left-0 right-0 glass-nav z-30 pb-safe">
                <nav className="flex items-center justify-around py-2 px-2">
                    {navItems.map((item) => {
                        const isActive = pathname === item.href;
                        return (
                            <Link
                                key={item.href}
                                href={item.href}
                                className={cn(
                                    "flex flex-col items-center gap-1 p-2 rounded-xl text-[10px] font-medium transition-colors min-w-[60px]",
                                    isActive ? "text-blue-400 bg-blue-500/10" : "text-slate-500 hover:text-slate-300"
                                )}
                            >
                                <item.icon className={cn("w-5 h-5 mb-0.5", isActive ? "text-blue-400" : "")} />
                                {item.name}
                            </Link>
                        );
                    })}
                    {/* Auth icon in bottom bar */}
                    {userEmail ? (
                        <button
                            onClick={handleLogout}
                            className="flex flex-col items-center gap-1 p-2 rounded-xl text-[10px] font-medium text-slate-500 hover:text-red-400 min-w-[60px] transition-colors"
                        >
                            <LogOut className="w-5 h-5 mb-0.5" />
                            Logout
                        </button>
                    ) : (
                        <Link href="/login"
                            className="flex flex-col items-center gap-1 p-2 rounded-xl text-[10px] font-medium text-emerald-500 min-w-[60px]">
                            <MessageCircle className="w-5 h-5 mb-0.5" />
                            Login
                        </Link>
                    )}
                </nav>
            </div>
        </>
    );
}
