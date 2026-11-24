"use client";

import { useState, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { 
    Tag, Search, ExternalLink, Sparkles, TrendingUp, DollarSign, 
    Percent, ArrowUpRight, CheckCircle2, ShieldCheck, Share2, 
    SlidersHorizontal, ShoppingBag, Globe, Info, Flame
} from "lucide-react";
import { THANKSGIVING_DEALS, ThanksgivingDeal, EXCHANGE_RATE_USD_TO_INR } from "@/lib/thanksgivingDeals";

const CATEGORIES = [
    "All",
    "Laptops & Tech",
    "TVs & Home Theater",
    "Audio & Headphones",
    "Kitchen & Cooking",
    "Beauty & Grooming",
    "Smart Home",
    "Travel & Lifestyle",
    "Smartphones"
];

export default function DealsPage() {
    const [searchQuery, setSearchQuery] = useState("");
    const [selectedCategory, setSelectedCategory] = useState("All");
    const [sortBy, setSortBy] = useState<"savings" | "percent" | "price-asc" | "price-desc" | "india-savings">("savings");
    const [copiedId, setCopiedId] = useState<string | null>(null);

    // Filter & Sort deals
    const filteredDeals = useMemo(() => {
        let deals = THANKSGIVING_DEALS.filter(deal => {
            const matchesCategory = selectedCategory === "All" || deal.category === selectedCategory;
            const q = searchQuery.toLowerCase().trim();
            const matchesQuery = !q || 
                deal.name.toLowerCase().includes(q) || 
                deal.description.toLowerCase().includes(q) || 
                deal.store.toLowerCase().includes(q) ||
                deal.category.toLowerCase().includes(q);
            return matchesCategory && matchesQuery;
        });

        return deals.sort((a, b) => {
            if (sortBy === "savings") return b.usSavings - a.usSavings;
            if (sortBy === "percent") return b.discountPercent - a.discountPercent;
            if (sortBy === "price-asc") return a.usSalePrice - b.usSalePrice;
            if (sortBy === "price-desc") return b.usSalePrice - a.usSalePrice;
            if (sortBy === "india-savings") return b.savingsVsIndiaInr - a.savingsVsIndiaInr;
            return 0;
        });
    }, [searchQuery, selectedCategory, sortBy]);

    const handleCopy = (deal: ThanksgivingDeal) => {
        const text = `${deal.name} - Thanksgiving Deal: $${deal.usSalePrice} (Save $${deal.usSavings})! India MRP: ₹${deal.indiaPriceInr.toLocaleString("en-IN")}. Check it out on BidMyHobby: ${window.location.href}`;
        navigator.clipboard.writeText(text);
        setCopiedId(deal.id);
        setTimeout(() => setCopiedId(null), 2000);
    };

    // Calculate total potential savings across all 10 deals
    const totalDollarSavings = useMemo(() => {
        return THANKSGIVING_DEALS.reduce((acc, d) => acc + d.usSavings, 0);
    }, []);

    return (
        <div className="min-h-screen bg-[#070b14] text-white flex flex-col selection:bg-blue-500 selection:text-white">
            {/* Background glowing effects */}
            <div className="fixed inset-0 pointer-events-none overflow-hidden z-0">
                <div className="absolute -top-40 left-1/4 w-[600px] h-[600px] bg-blue-600/10 rounded-full blur-[140px]" />
                <div className="absolute top-1/3 -right-40 w-[500px] h-[500px] bg-purple-600/10 rounded-full blur-[140px]" />
                <div className="absolute -bottom-20 left-1/3 w-[600px] h-[600px] bg-emerald-600/10 rounded-full blur-[160px]" />
            </div>

            <main className="relative z-10 flex-1 px-4 py-8 md:py-12 max-w-7xl mx-auto w-full">
                {/* ─── Hero Header ─── */}
                <div className="text-center max-w-3xl mx-auto mb-10 md:mb-14">
                    <motion.div 
                        initial={{ opacity: 0, y: -12 }} 
                        animate={{ opacity: 1, y: 0 }} 
                        className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-gradient-to-r from-red-500/20 via-orange-500/20 to-amber-500/20 border border-orange-500/30 text-orange-300 text-xs font-semibold uppercase tracking-wider mb-4 shadow-[0_0_20px_rgba(249,115,22,0.15)]"
                    >
                        <Flame className="w-4 h-4 text-orange-400 animate-pulse" />
                        Thanksgiving & Black Friday Mega Deals
                    </motion.div>

                    <motion.h1 
                        initial={{ opacity: 0, y: -8 }} 
                        animate={{ opacity: 1, y: 0 }} 
                        transition={{ delay: 0.1 }}
                        className="text-3xl md:text-5xl font-black tracking-tight text-white mb-4 leading-tight"
                    >
                        Save <span className="bg-gradient-to-r from-blue-400 via-indigo-300 to-purple-400 bg-clip-text text-transparent">&gt;$100 on Top Holiday Picks</span>
                    </motion.h1>

                    <motion.p 
                        initial={{ opacity: 0, y: -4 }} 
                        animate={{ opacity: 1, y: 0 }} 
                        transition={{ delay: 0.2 }}
                        className="text-slate-400 text-sm md:text-base leading-relaxed"
                    >
                        Curated from popular stores like <strong>Macy&apos;s, Target, Amazon, Best Buy, and eBay</strong>. 
                        We scraped the hottest deals saving anywhere from <strong>$150 to $704</strong>, with live <strong>India INR (₹) retail price comparisons</strong>.
                    </motion.p>

                    {/* Highlights Ticker */}
                    <motion.div 
                        initial={{ opacity: 0, y: 8 }} 
                        animate={{ opacity: 1, y: 0 }} 
                        transition={{ delay: 0.3 }}
                        className="grid grid-cols-2 md:grid-cols-4 gap-3 mt-8"
                    >
                        <div className="glass-panel p-3.5 rounded-2xl border border-white/5 flex flex-col items-center text-center">
                            <span className="text-2xl font-black text-emerald-400 font-mono">${totalDollarSavings.toLocaleString()}</span>
                            <span className="text-[11px] text-slate-400 mt-0.5">Total Potential Savings</span>
                        </div>
                        <div className="glass-panel p-3.5 rounded-2xl border border-white/5 flex flex-col items-center text-center">
                            <span className="text-2xl font-black text-blue-400 font-mono">10 / 10</span>
                            <span className="text-[11px] text-slate-400 mt-0.5">Deals Save &gt;$100</span>
                        </div>
                        <div className="glass-panel p-3.5 rounded-2xl border border-white/5 flex flex-col items-center text-center">
                            <span className="text-2xl font-black text-orange-400 font-mono">70% Max</span>
                            <span className="text-[11px] text-slate-400 mt-0.5">Top Discount (Macy&apos;s)</span>
                        </div>
                        <div className="glass-panel p-3.5 rounded-2xl border border-white/5 flex flex-col items-center text-center">
                            <span className="text-2xl font-black text-purple-400 font-mono">₹39k+</span>
                            <span className="text-[11px] text-slate-400 mt-0.5">Max Savings vs India MRP</span>
                        </div>
                    </motion.div>
                </div>

                {/* ─── Search & Filter Bar ─── */}
                <div className="glass-panel p-4 md:p-5 rounded-2xl border border-white/10 mb-8 space-y-4">
                    <div className="flex flex-col md:flex-row gap-3 items-center justify-between">
                        {/* Search Input */}
                        <div className="relative w-full md:w-96">
                            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input
                                type="text"
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                placeholder="Search products, stores, keywords..."
                                className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white/5 border border-white/10 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500/50 transition-colors"
                            />
                            {searchQuery && (
                                <button 
                                    onClick={() => setSearchQuery("")}
                                    className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-500 hover:text-white"
                                >
                                    Clear
                                </button>
                            )}
                        </div>

                        {/* Sort Selector */}
                        <div className="flex items-center gap-2 w-full md:w-auto">
                            <SlidersHorizontal className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="text-xs text-slate-400 shrink-0">Sort by:</span>
                            <select
                                value={sortBy}
                                onChange={e => setSortBy(e.target.value as any)}
                                className="w-full md:w-auto px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-xs text-slate-200 focus:outline-none focus:border-blue-500/50 cursor-pointer"
                            >
                                <option value="savings" className="bg-slate-900 text-white">Biggest US $ Savings</option>
                                <option value="percent" className="bg-slate-900 text-white">Highest % Discount</option>
                                <option value="india-savings" className="bg-slate-900 text-white">Biggest Savings vs India (₹)</option>
                                <option value="price-asc" className="bg-slate-900 text-white">Price: Low to High</option>
                                <option value="price-desc" className="bg-slate-900 text-white">Price: High to Low</option>
                            </select>
                        </div>
                    </div>

                    {/* Category Pills */}
                    <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none pt-1">
                        {CATEGORIES.map(category => {
                            const isSelected = selectedCategory === category;
                            return (
                                <button
                                    key={category}
                                    onClick={() => setSelectedCategory(category)}
                                    className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-all duration-200 ${
                                        isSelected
                                            ? "bg-blue-600 text-white shadow-[0_0_12px_rgba(37,99,235,0.4)]"
                                            : "bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 border border-white/5"
                                    }`}
                                >
                                    {category}
                                </button>
                            );
                        })}
                    </div>
                </div>

                {/* ─── Deals Grid ─── */}
                {filteredDeals.length === 0 ? (
                    <div className="glass-panel p-12 text-center rounded-2xl border border-white/5 my-8">
                        <ShoppingBag className="w-12 h-12 text-slate-600 mx-auto mb-3" />
                        <h3 className="text-lg font-bold text-white mb-1">No deals match your search</h3>
                        <p className="text-slate-400 text-sm mb-4">Try clearing your filters or search keywords.</p>
                        <button
                            onClick={() => { setSearchQuery(""); setSelectedCategory("All"); }}
                            className="px-4 py-2 rounded-xl bg-blue-600/20 text-blue-400 border border-blue-500/30 text-xs font-semibold hover:bg-blue-600/30 transition-colors"
                        >
                            Reset Filters
                        </button>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        <AnimatePresence>
                            {filteredDeals.map((deal, idx) => (
                                <motion.div
                                    key={deal.id}
                                    layout
                                    initial={{ opacity: 0, y: 16 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    exit={{ opacity: 0, scale: 0.95 }}
                                    transition={{ duration: 0.35, delay: idx * 0.05 }}
                                    className="glass-panel rounded-3xl overflow-hidden border border-white/10 hover:border-blue-500/30 flex flex-col transition-all duration-300 hover:shadow-[0_8px_30px_rgba(0,0,0,0.5)] group"
                                >
                                    {/* Image & Header Overlay */}
                                    <div className="relative h-56 bg-slate-900/80 overflow-hidden shrink-0">
                                        {/* eslint-disable-next-line @next/next/no-img-element */}
                                        <img
                                            src={deal.image}
                                            alt={deal.name}
                                            className="w-full h-full object-cover object-center group-hover:scale-105 transition-transform duration-500"
                                            onError={e => {
                                                (e.target as HTMLImageElement).src = "https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?auto=format&fit=crop&w=800&q=80";
                                            }}
                                        />
                                        <div className="absolute inset-0 bg-gradient-to-t from-[#0B1120] via-black/30 to-black/50" />

                                        {/* Top Store & Badge Bar */}
                                        <div className="absolute top-3 left-3 right-3 flex items-center justify-between gap-2 z-10">
                                            <span className={`px-2.5 py-1 rounded-full text-[11px] font-bold border backdrop-blur-md ${deal.storeBadgeColor}`}>
                                                {deal.store}
                                            </span>

                                            <span className="px-2.5 py-1 rounded-full text-[11px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 backdrop-blur-md flex items-center gap-1 shadow-lg">
                                                <TrendingUp className="w-3 h-3" />
                                                Save ${deal.usSavings.toFixed(0)} ({deal.discountPercent}%)
                                            </span>
                                        </div>

                                        {/* Bottom Tagline on Image */}
                                        <div className="absolute bottom-3 left-3 right-3 z-10">
                                            <span className="px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider bg-orange-500/20 text-orange-300 border border-orange-500/30 backdrop-blur-md">
                                                {deal.badge}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Content Area */}
                                    <div className="p-5 flex-1 flex flex-col justify-between">
                                        <div>
                                            <div className="text-[11px] font-semibold text-blue-400 uppercase tracking-wider mb-1">
                                                {deal.category}
                                            </div>
                                            <h3 className="text-lg font-bold text-white group-hover:text-blue-300 transition-colors line-clamp-2 leading-snug mb-1">
                                                {deal.name}
                                            </h3>
                                            <p className="text-xs text-slate-400 line-clamp-2 mb-4 leading-relaxed">
                                                {deal.description}
                                            </p>

                                            {/* Key Specs Pills */}
                                            <div className="space-y-1.5 mb-5">
                                                {deal.keySpecs.slice(0, 3).map((spec, sIdx) => (
                                                    <div key={sIdx} className="flex items-center gap-2 text-[11px] text-slate-300">
                                                        <CheckCircle2 className="w-3.5 h-3.5 text-blue-400 shrink-0" />
                                                        <span className="truncate">{spec}</span>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>

                                        {/* Pricing Box & India Arbitrage */}
                                        <div className="pt-4 border-t border-white/5 space-y-3">
                                            {/* US Thanksgiving Price */}
                                            <div className="flex items-baseline justify-between">
                                                <div>
                                                    <span className="text-2xl font-black text-white font-mono">
                                                        ${deal.usSalePrice.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                    </span>
                                                    <span className="ml-2 text-xs text-slate-500 line-through font-mono">
                                                        ${deal.usRegularPrice.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                    </span>
                                                </div>
                                                <div className="text-right">
                                                    <span className="text-[10px] font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
                                                        -$ {deal.usSavings.toFixed(0)} US Saved
                                                    </span>
                                                </div>
                                            </div>

                                            {/* India Retail Comparison */}
                                            <div className="p-2.5 rounded-xl bg-white/[0.03] border border-white/5 flex items-center justify-between text-xs">
                                                <div>
                                                    <div className="text-[10px] text-slate-400 flex items-center gap-1">
                                                        <span>🇮🇳 India MRP:</span>
                                                        <strong className="text-slate-200 font-mono">₹{deal.indiaPriceInr.toLocaleString("en-IN")}</strong>
                                                    </div>
                                                    <div className="text-[11px] text-emerald-400 font-semibold mt-0.5">
                                                        Save ~₹{deal.savingsVsIndiaInr.toLocaleString("en-IN")} vs India!
                                                    </div>
                                                </div>
                                                <span className="text-[10px] text-slate-500 bg-black/40 px-1.5 py-0.5 rounded">
                                                    @ ₹{EXCHANGE_RATE_USD_TO_INR}/$
                                                </span>
                                            </div>

                                            {/* Action Buttons */}
                                            <div className="flex items-center gap-2 pt-2">
                                                <a
                                                    href={deal.dealUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition-all shadow-[0_0_16px_rgba(37,99,235,0.3)] hover:shadow-[0_0_24px_rgba(37,99,235,0.5)]"
                                                >
                                                    View Deal on {deal.store}
                                                    <ExternalLink className="w-3.5 h-3.5" />
                                                </a>

                                                <button
                                                    onClick={() => handleCopy(deal)}
                                                    title="Copy Deal Information"
                                                    className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 hover:text-white transition-colors"
                                                >
                                                    {copiedId === deal.id ? (
                                                        <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                                                    ) : (
                                                        <Share2 className="w-4 h-4" />
                                                    )}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </motion.div>
                            ))}
                        </AnimatePresence>
                    </div>
                )}

                {/* ─── Footer Banner for Platform Engagement ─── */}
                <div className="mt-16 glass-panel p-6 md:p-8 rounded-3xl border border-white/10 text-center max-w-4xl mx-auto bg-gradient-to-b from-white/[0.04] to-transparent">
                    <Sparkles className="w-8 h-8 text-blue-400 mx-auto mb-3" />
                    <h3 className="text-xl md:text-2xl font-black text-white mb-2">
                        Turn Your Hobby Items into Real Value on BidMyHobby
                    </h3>
                    <p className="text-slate-400 text-xs md:text-sm max-w-xl mx-auto mb-6">
                        Whether you are hunting holiday deals, collecting vintage artwork, or selling premium gear, 
                        BidMyHobby provides instant AI valuation, AI enthusiast matching, and live community bidding.
                    </p>
                    <div className="flex flex-wrap items-center justify-center gap-3">
                        <a
                            href="/upload"
                            className="px-6 py-3 rounded-xl bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-semibold text-sm transition-all shadow-lg shadow-blue-500/25"
                        >
                            Upload an Item
                        </a>
                        <a
                            href="/dashboard"
                            className="px-6 py-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-300 hover:text-white font-semibold text-sm transition-all"
                        >
                            Explore AI Profiles
                        </a>
                    </div>
                </div>
            </main>
        </div>
    );
}
