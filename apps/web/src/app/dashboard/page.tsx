"use client";

import { motion, AnimatePresence } from "framer-motion";
import { Search, Sparkles, Loader2, MessageCircle } from "lucide-react";
import { useState, useEffect } from "react";
import { AIChatModal } from "@/components/AIChatModal";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

const GENDER_ICONS: Record<string, string> = {
    FEMALE: "♀",
    MALE: "♂",
    OTHER: "⚧",
};

const ETHNICITY_COLORS: Record<string, string> = {
    Indian: "bg-orange-500/20 text-orange-300 border-orange-500/20",
    Asian: "bg-yellow-500/20 text-yellow-300 border-yellow-500/20",
    Hispanic: "bg-pink-500/20 text-pink-300 border-pink-500/20",
    Black: "bg-purple-500/20 text-purple-300 border-purple-500/20",
    White: "bg-sky-500/20 text-sky-300 border-sky-500/20",
};

function ProfileCard({ profile, index, onChat }: { profile: any; index: number; onChat: (p: any) => void }) {
    const imageUrl = profile.imageUrl
        ? `${API_URL}/ai/images/${profile.imageUrl}`
        : null;

    const ethnicityClass = ETHNICITY_COLORS[profile.ethnicity] || "bg-slate-500/20 text-slate-300 border-slate-500/20";

    return (
        <motion.div
            key={`profile-${profile.id || index}`}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: (index % 6) * 0.07 }}
            className="glass-panel overflow-hidden group hover:bg-white/[0.06] transition-all duration-300 border border-white/5 hover:border-indigo-500/20 flex flex-col"
        >
            {/* Cover / avatar strip */}
            <div className="relative h-28 bg-gradient-to-br from-indigo-900/60 via-purple-900/40 to-slate-900/60 overflow-hidden shrink-0">
                <div className="absolute inset-0 bg-gradient-to-t from-[#0B1120] via-transparent to-transparent z-10" />
                {imageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                        src={imageUrl}
                        alt={profile.firstName}
                        className="w-full h-full object-cover object-top opacity-40 group-hover:opacity-60 transition-opacity duration-500 scale-110 group-hover:scale-100"
                        onError={e => { (e.target as HTMLImageElement).style.display = "none"; }}
                    />
                )}

                {/* Age badge */}
                <div className="absolute top-3 right-3 z-20">
                    <span className="px-2 py-0.5 text-xs font-bold bg-black/50 backdrop-blur-sm text-slate-300 rounded-full border border-white/10">
                        {profile.age}y
                    </span>
                </div>
            </div>

            {/* Avatar */}
            <div className="px-5 -mt-8 relative z-20 flex items-end gap-4">
                <div className="w-16 h-16 rounded-2xl overflow-hidden border-2 border-indigo-500/40 bg-indigo-900/60 shrink-0 shadow-xl">
                    {imageUrl ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                            src={imageUrl}
                            alt={profile.firstName}
                            className="w-full h-full object-cover"
                            onError={e => {
                                (e.target as HTMLImageElement).src =
                                    `https://ui-avatars.com/api/?name=${encodeURIComponent(profile.firstName)}&background=6366f1&color=fff&size=128&bold=true`;
                            }}
                        />
                    ) : (
                        <div className="w-full h-full flex items-center justify-center text-2xl font-black text-indigo-300">
                            {profile.firstName?.[0] || "?"}
                        </div>
                    )}
                </div>

                <div className="pb-1 flex-1 min-w-0">
                    <h4 className="font-bold text-white text-lg leading-tight truncate">
                        {profile.firstName} {profile.lastName}
                        <span className="ml-1.5 text-slate-400 text-sm font-normal">
                            {GENDER_ICONS[profile.gender] || ""}
                        </span>
                    </h4>
                    <p className="text-indigo-400 text-xs font-semibold truncate">
                        {profile.mainHobby || "Hobbyist"}
                    </p>
                </div>
            </div>

            {/* Body */}
            <div className="px-5 pt-3 pb-5 flex-1 flex flex-col gap-3">
                {/* Tags */}
                <div className="flex flex-wrap gap-1.5">
                    {profile.ethnicity && (
                        <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${ethnicityClass}`}>
                            {profile.ethnicity}
                        </span>
                    )}
                    {profile.gender && (
                        <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-white/5 border border-white/10 text-slate-400">
                            {profile.gender}
                        </span>
                    )}
                </div>

                {/* Bio */}
                <p className="text-slate-400 text-xs leading-relaxed line-clamp-3 flex-1">
                    {profile.bio || "Passionate hobbyist on BidMyHobby."}
                </p>

                {/* CTA */}
                <button
                    onClick={() => onChat(profile)}
                    className="w-full flex items-center justify-center gap-2 py-2.5 rounded-xl font-semibold text-sm bg-indigo-600/20 hover:bg-indigo-600/40 border border-indigo-500/30 hover:border-indigo-500/60 text-indigo-300 hover:text-white transition-all group/btn"
                >
                    <MessageCircle className="w-4 h-4 group-hover/btn:scale-110 transition-transform" />
                    Chat with {profile.firstName}
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse ml-1" />
                </button>
            </div>
        </motion.div>
    );
}

export default function Dashboard() {
    const [profiles, setProfiles] = useState<any[]>([]);
    const [filtered, setFiltered] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [selectedProfile, setSelectedProfile] = useState<any | null>(null);
    const [visibleCount, setVisibleCount] = useState(6);
    const [search, setSearch] = useState("");

    useEffect(() => {
        const fetchProfiles = async () => {
            try {
                const res = await fetch(`${API_URL}/ai/profiles`);
                if (res.ok) {
                    const data = await res.json();
                    const shuffled = [...data].sort(() => 0.5 - Math.random());
                    setProfiles(shuffled);
                    setFiltered(shuffled);
                }
            } catch (err) {
                console.error("Failed to fetch profiles:", err);
            } finally {
                setIsLoading(false);
            }
        };
        fetchProfiles();
    }, []);

    useEffect(() => {
        if (!search.trim()) { setFiltered(profiles); return; }
        const q = search.toLowerCase();
        setFiltered(profiles.filter(p =>
            [p.firstName, p.lastName, p.ethnicity, p.gender, p.mainHobby, p.bio]
                .filter(Boolean).some((v: string) => v.toLowerCase().includes(q))
        ));
    }, [search, profiles]);

    return (
        <div className="flex flex-col min-h-full pb-20 md:pb-8">
            <div className="px-4 py-8 md:py-12 max-w-7xl mx-auto w-full">

                {/* Header */}
                <div className="mb-10">
                    <h1 className="text-3xl md:text-5xl font-black text-white flex items-center gap-3">
                        AI Profiles <Sparkles className="text-indigo-400 w-8 h-8 md:w-10 md:h-10" />
                    </h1>
                    <p className="text-slate-400 mt-2 text-lg">
                        Discover hobbyists and start an AI-powered conversation.
                    </p>
                </div>

                {/* Search Bar */}
                <motion.div
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.4 }}
                    className="relative max-w-3xl mx-auto mb-12"
                >
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                        <Search className="h-5 h-5 text-slate-400" />
                    </div>
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        className="block w-full pl-12 pr-6 py-4 bg-white/5 border border-white/10 rounded-2xl text-white placeholder-slate-500 focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500/50 transition-all shadow-xl backdrop-blur-md"
                        placeholder="Search by name, hobby, ethnicity…"
                    />
                </motion.div>

                {/* Stats bar */}
                <div className="flex items-center justify-between mb-6">
                    <h3 className="text-lg font-semibold text-white">
                        {search ? `${filtered.length} results` : `${profiles.length} Hobbyists`}
                    </h3>
                    <span className="text-xs text-slate-500 bg-white/5 px-3 py-1.5 rounded-full border border-white/10">
                        Powered by <span className="text-indigo-400 font-bold">OpenAI</span>
                    </span>
                </div>

                {isLoading ? (
                    <div className="flex justify-center py-20">
                        <Loader2 className="w-10 h-10 animate-spin text-indigo-500" />
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-20 text-slate-500">
                        <Sparkles className="w-12 h-12 mx-auto mb-4 opacity-30" />
                        <p>No profiles match your search.</p>
                    </div>
                ) : (
                    <div className="space-y-8">
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                            {filtered.slice(0, visibleCount).map((profile, index) => (
                                <ProfileCard
                                    key={profile.id || index}
                                    profile={profile}
                                    index={index}
                                    onChat={setSelectedProfile}
                                />
                            ))}
                        </div>

                        {visibleCount < filtered.length && (
                            <div className="flex justify-center pt-6">
                                <button
                                    onClick={() => setVisibleCount(prev => prev + 6)}
                                    className="px-8 py-3 bg-white/5 hover:bg-indigo-600/20 border border-white/10 hover:border-indigo-500/30 rounded-full text-white font-medium transition-all hover:scale-105 active:scale-95"
                                >
                                    Load more hobbyists
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Chat Modal */}
            <AnimatePresence>
                {selectedProfile && (
                    <AIChatModal
                        profile={selectedProfile}
                        onClose={() => setSelectedProfile(null)}
                    />
                )}
            </AnimatePresence>
        </div>
    );
}
