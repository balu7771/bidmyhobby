"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { User, LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { clearSession } from "@/lib/auth";

export function UserHeader() {
    const [userEmail, setUserEmail] = useState<string | null>(null);
    const router = useRouter();

    useEffect(() => {
        const checkUser = () => {
            const email = localStorage.getItem("userEmail");
            setUserEmail(email);
        };

        checkUser();

        // Listen for a custom event so we can update immediately upon login
        window.addEventListener("userLogin", checkUser);
        // Also listen for storage events in case of other tabs
        window.addEventListener("storage", checkUser);

        return () => {
            window.removeEventListener("userLogin", checkUser);
            window.removeEventListener("storage", checkUser);
        };
    }, []);

    const handleLogout = () => {
        fetch(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api"}/auth/logout`, {
            method: "POST", credentials: "include"
        }).catch(() => {});
        clearSession();
        setUserEmail(null);
        router.push("/");
    };

    return (
        <div className="absolute top-4 right-4 md:top-8 md:right-8 z-50 flex items-center gap-3">
            <div className="glass-panel px-4 py-2 rounded-full flex items-center gap-3 border border-white/10 shadow-lg bg-background/50 backdrop-blur-md">
                <div className="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center">
                    <User className="w-4 h-4 text-blue-400" />
                </div>
                <div className="flex flex-col">
                    <span className="text-xs text-slate-400">Welcome</span>
                    <span className="text-sm font-semibold text-white max-w-[120px] truncate">
                        {userEmail ? userEmail.split('@')[0] : "Guest User"}
                    </span>
                </div>
                {userEmail ? (
                    <button 
                        onClick={handleLogout}
                        className="ml-2 p-1.5 rounded-full hover:bg-white/10 text-slate-400 hover:text-white transition-colors"
                        title="Logout"
                    >
                        <LogOut className="w-4 h-4" />
                    </button>
                ) : (
                    <Link 
                        href="/login"
                        className="ml-2 text-xs font-semibold text-blue-400 hover:text-blue-300 bg-blue-500/10 hover:bg-blue-500/20 px-2 py-1 rounded-md transition-colors"
                    >
                        Log in
                    </Link>
                )}
            </div>
        </div>
    );
}
