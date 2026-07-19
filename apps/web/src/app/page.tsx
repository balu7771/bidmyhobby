"use client";

import { motion, AnimatePresence } from "framer-motion";
import { ArrowRight, TrendingUp, Gavel, Users, Box, ChevronLeft, ChevronRight, X, Upload, Loader2, Check, Trash2, Phone, Mail, Globe, Instagram } from "lucide-react";
import Link from "next/link";
import { useState, useEffect, Suspense } from "react";
import { useSearchParams } from "next/navigation";

/** Masks phone numbers and emails for public display.
 *  Creators see unmasked identities of their own item's bidders. */
function maskIdentity(identity: string | null | undefined): string {
    if (!identity) return "Anonymous";
    if (/^\+?[0-9\s\-]{7,}$/.test(identity.trim())) {
        const digits = identity.replace(/[^0-9]/g, "");
        if (digits.length >= 6) {
            const visible = digits.slice(-4);
            return `+${"*".repeat(digits.length - 4)}${visible}`;
        }
        return "*".repeat(identity.length);
    }
    const atIdx = identity.indexOf("@");
    if (atIdx > 0) {
        const username = identity.slice(0, atIdx);
        const domain = identity.slice(atIdx);
        if (username.length <= 2) return `${username}***${domain}`;
        return `${username.slice(0, 2)}${"*".repeat(Math.min(username.length - 2, 5))}${domain}`;
    }
    return identity;
}

/** Formats a price in the given currency.
 *  INR uses Indian lakh formatting (e.g. ₹1,25,000).
 *  GBP and USD use standard Western formatting. */
function formatCurrency(amount: number | null | undefined, currency: string | null | undefined): string {
    if (amount == null || isNaN(amount)) return "—";
    const cur = (currency || "INR").toUpperCase();
    if (cur === "INR") {
        const formatted = amount.toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
        return `₹${formatted}`;
    }
    if (cur === "GBP") return `£${amount.toLocaleString("en-GB", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    return `$${amount.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

const CURRENCY_SYMBOLS: Record<string, string> = { INR: "₹", GBP: "£", USD: "$" };

function biddingFloor(item: any): number {
    const prices = [item?.basePrice, item?.suggestedPrice]
        .map(Number)
        .filter(price => Number.isFinite(price) && price >= 0);
    return prices.length ? Math.min(...prices) : 0;
}

/** Parses contact fields appended to a business card description by the backend.
 *  Format: "Some description\nPhone: ****1234\nEmail: foo@bar.com\n..." */
function parseBusinessContact(description: string | null | undefined): {
    intro: string; phone?: string; email?: string; website?: string; instagram?: string;
} {
    if (!description) return { intro: "" };
    const lines = description.split("\n");
    const result: Record<string, string> = {};
    const introLines: string[] = [];
    for (const line of lines) {
        const m = line.match(/^(Phone|Email|Website|Instagram):\s*(.+)$/);
        if (m) result[m[1].toLowerCase()] = m[2].trim();
        else introLines.push(line);
    }
    return { intro: introLines.join("\n").trim(), ...result } as any;
}

/** Extracts the S3 filename from a full S3 URL or relative key and returns
 *  a backend-proxied URL so images load without needing a public S3 bucket. */
function getCatalogImageUrl(urlOrKey: string | null | undefined): string {
    if (!urlOrKey) return "";
    const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
    // Full S3 URL: https://bucket.s3.region.amazonaws.com/images/file.png
    if (urlOrKey.startsWith("http")) {
        const match = urlOrKey.match(/amazonaws\.com\/(.+)$/);
        if (match) {
            const key = decodeURIComponent(match[1]); // "images/filename.png"
            const filename = key.startsWith("images/") ? key.slice("images/".length) : key;
            return `${apiUrl}/catalog/images/${encodeURIComponent(filename)}`;
        }
        return urlOrKey; // unknown URL, use as-is
    }
    // Relative key: "images/filename.png" or just "filename.png"
    const filename = urlOrKey.startsWith("images/") ? urlOrKey.slice("images/".length) : urlOrKey;
    return `${apiUrl}/catalog/images/${encodeURIComponent(filename)}`;
}

function ItemCard({ item, index, onItemClick, userEmail }: { item: any, index: number, onItemClick: (item: any) => void, userEmail: string | null }) {
    const [currentImageIndex, setCurrentImageIndex] = useState(0);
    const images = item.imageUrls && item.imageUrls.length > 0 ? item.imageUrls : (item.imageUrl ? [item.imageUrl] : []);

    const nextImage = (e: React.MouseEvent) => {
        e.stopPropagation();
        setCurrentImageIndex((prev) => (prev + 1) % images.length);
    };

    const prevImage = (e: React.MouseEvent) => {
        e.stopPropagation();
        setCurrentImageIndex((prev) => (prev === 0 ? images.length - 1 : prev - 1));
    };

    return (
        <motion.div
            key={item.itemId || index}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: index * 0.1 }}
            className="glass-panel group overflow-hidden flex flex-col min-w-[280px] md:min-w-0"
        >
            <div 
                className="h-48 relative overflow-hidden bg-white/5 border-b border-white/5 cursor-pointer"
                onClick={() => onItemClick(item)}
            >
                <div className="absolute inset-0 bg-gradient-to-t from-background via-black/20 to-transparent z-10" />
                {images.length > 0 && (
                    <img
                        src={getCatalogImageUrl(images[currentImageIndex])}
                        alt={item.name}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 ease-in-out"
                    />
                )}

                {images.length > 1 && (
                    <>
                        <button 
                            onClick={prevImage}
                            className="absolute left-2 top-1/2 -translate-y-1/2 z-30 p-1.5 bg-black/50 hover:bg-blue-600 text-white rounded-full transition-colors opacity-0 group-hover:opacity-100 shadow-lg"
                        >
                            <ChevronLeft className="w-4 h-4" />
                        </button>
                        <button 
                            onClick={nextImage}
                            className="absolute right-2 top-1/2 -translate-y-1/2 z-30 p-1.5 bg-black/50 hover:bg-blue-600 text-white rounded-full transition-colors opacity-0 group-hover:opacity-100 shadow-lg"
                        >
                            <ChevronRight className="w-4 h-4" />
                        </button>
                        <div className="absolute bottom-2 right-2 z-20">
                            <span className="px-2 py-1 bg-black/60 backdrop-blur-md rounded-md text-[10px] text-white">
                                {currentImageIndex + 1} / {images.length}
                            </span>
                        </div>
                    </>
                )}
                
                <div className="absolute top-2 right-2 z-20 flex flex-col items-end gap-1">
                    <span className={`px-2 py-1 text-[10px] uppercase font-bold rounded-md ${item.status === 'ACTIVE' ? 'bg-emerald-500/20 text-emerald-300' : 'bg-yellow-500/20 text-yellow-300'}`}>
                        {item.status}
                    </span>
                </div>
                <div className="absolute top-2 left-2 z-20">
                    <span className="px-2 py-1 bg-black/50 backdrop-blur-md text-[10px] uppercase font-bold text-slate-300 rounded-md shadow-lg border border-white/10">
                        {item.category || 'HOBBY'}
                    </span>
                </div>
            </div>
            
            <div className="p-5 flex-1 flex flex-col justify-between">
                <div>
                    <h3 className="text-lg font-bold text-white mb-2 line-clamp-1">{item.name}</h3>
                    <p className="text-slate-400 text-xs mb-4 line-clamp-2">{item.description}</p>
                </div>
                <div className="flex items-center justify-between text-sm font-medium border-t border-white/5 pt-3 mt-auto">
                    {item.category === 'BUSINESS' ? (
                        <span className="text-indigo-300 text-xs font-semibold">Contact for details</span>
                    ) : (
                        <>
                            <span className="text-slate-300 text-xs">A.I Estimated</span>
                            <span className="font-mono text-emerald-400 font-bold text-base">
                                {item.suggestedPrice ? formatCurrency(item.suggestedPrice, item.currency) : 'Pricing...'}
                            </span>
                        </>
                    )}
                </div>
                <div className="flex items-center justify-between mt-2">
                    {item.category !== 'BUSINESS' && (
                        <span className="text-slate-500 text-[10px]">Seller Asking: {formatCurrency(item.basePrice, item.currency)}</span>
                    )}
                    <span className="text-slate-400 text-[10px] ml-auto">By: {maskIdentity(item.email)}</span>
                </div>

                {/* Login to Bid CTA — shown on card for unauthenticated users on non-BUSINESS items */}
                {!userEmail && item.category !== 'BUSINESS' && item.status === 'ACTIVE' && (
                    <div className="mt-3 pt-3 border-t border-white/5">
                        <Link
                            href={`/login?returnTo=/`}
                            onClick={e => e.stopPropagation()}
                            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-blue-600/80 to-indigo-600/80 hover:from-blue-500 hover:to-indigo-500 transition-all shadow-md"
                        >
                            <Gavel className="w-4 h-4" />
                            Login to Place Bid
                        </Link>
                    </div>
                )}
            </div>
        </motion.div>
    );
}

function HomeContent() {
    const [realItems, setRealItems] = useState<any[]>([]);
    const [selectedItem, setSelectedItem] = useState<any | null>(null);
    const [userEmail, setUserEmail] = useState<string | null>(null);
    const searchParams = useSearchParams();
    const showMyItems = searchParams.get("myItems") === "1";
    
    // Modal State
    const [modalImageIndex, setModalImageIndex] = useState(0);
    const [bids, setBids] = useState<any[]>([]);
    const [newBidAmount, setNewBidAmount] = useState<string>("");
    const [isLoading, setIsLoading] = useState(false);
    const [bidError, setBidError] = useState<string | null>(null);
    const [bidSuccess, setBidSuccess] = useState<string | null>(null);
    const [isApproving, setIsApproving] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [isFetchingItems, setIsFetchingItems] = useState(true);
    const [fetchError, setFetchError] = useState<string | null>(null);

    const ADMIN_SUFFIX = "9353035490";
    const isAdmin = userEmail ? userEmail.replace(/[^0-9]/g, "").endsWith(ADMIN_SUFFIX) : false;
    const isItemCreator = !!(userEmail && selectedItem && selectedItem.email === userEmail);
    const canDelete = isItemCreator || isAdmin;

    const loadItems = async () => {
        setIsFetchingItems(true);
        setFetchError(null);
        const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
        const MAX_RETRIES = 5;
        const BASE_DELAY_MS = 1500;

        for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                const res = await fetch(`${apiUrl}/catalog/items`);
                if (res.ok) {
                    const data = await res.json();
                    setRealItems(data);
                    setIsFetchingItems(false);
                    return;
                }
            } catch (err) {
                console.warn(`Catalog fetch attempt ${attempt + 1} failed:`, err);
            }
            if (attempt < MAX_RETRIES - 1) {
                // Exponential backoff: 1.5s, 3s, 6s, 12s
                await new Promise(resolve => setTimeout(resolve, BASE_DELAY_MS * Math.pow(2, attempt)));
            }
        }
        setFetchError("Could not load items. The catalog service may still be starting up.");
        setIsFetchingItems(false);
    };

    useEffect(() => {
        setUserEmail(localStorage.getItem("userEmail"));
        loadItems();
    }, []);

    useEffect(() => {
        if (selectedItem) {
            setModalImageIndex(0);
            fetchBids(selectedItem.itemId);
        }
    }, [selectedItem]);

    const fetchBids = async (itemId: string) => {
        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/auction/bids/get`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ itemId })
            });
            if (res.ok) {
                const data = await res.json();
                setBids(data);
            }
        } catch (err) {
            console.error("Failed to fetch bids:", err);
        }
    };

    const handlePlaceBid = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!userEmail) return;

        const bidVal = parseFloat(newBidAmount);
        const minBid = biddingFloor(selectedItem);
        const cur = selectedItem?.currency || "INR";

        if (isNaN(bidVal) || bidVal <= 0) {
            setBidError("Please enter a valid bid amount."); return;
        }
        if (bidVal < minBid) {
            setBidError(`Your bid must be at least ${formatCurrency(minBid, cur)}, the lower of the AI estimate and seller asking price.`); return;
        }
        setIsLoading(true);
        setBidError(null);
        setBidSuccess(null);
        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/auction/bids`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    itemId: selectedItem.itemId,
                    bidderEmail: userEmail,
                    bidAmount: bidVal,
                    currency: cur
                })
            });
            if (!res.ok) {
                const errText = await res.text();
                throw new Error(errText || "Failed to place bid");
            }
            setBidSuccess(`Bid of ${formatCurrency(bidVal, cur)} placed successfully!`);
            setNewBidAmount("");
            fetchBids(selectedItem.itemId);

            // Notify seller via WhatsApp if their identity is a phone number
            const sellerEmail = selectedItem?.email || "";
            if (sellerEmail.startsWith("+") || /^\d+$/.test(sellerEmail)) {
                fetch(`${apiUrl}/ai/whatsapp/notify-bid`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        sellerPhone: sellerEmail,
                        itemName: selectedItem.name,
                        bidAmount: bidVal.toString(),
                        currency: cur
                    })
                }).catch(() => {}); // fire-and-forget
            }
        } catch (err: any) {
            setBidError(err.message);
        } finally {
            setIsLoading(false);
        }
    };

    const handleApproveWinner = async (itemId: string, sellerEmail: string) => {
        setIsApproving(true);
        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/auction/bids/complete`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    itemId,
                    itemName: selectedItem?.name,
                    sellerEmail
                })
            });
            if (!res.ok) throw new Error("Failed to approve winner");
            setSelectedItem({ ...selectedItem, status: 'SALE_IN_PROGRESS' });
            setRealItems(prev => prev.map(item => item.itemId === itemId ? { ...item, status: 'SALE_IN_PROGRESS' } : item));
        } catch (err: any) {
            console.error("Error approving winner:", err);
            alert("Error approving winner: " + err.message);
        } finally {
            setIsApproving(false);
        }
    };

    const handleDeleteItem = async (itemId: string) => {
        if (!userEmail) return;
        if (!confirm("Are you sure you want to delete this item? This cannot be undone.")) return;
        setIsDeleting(true);
        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/catalog/items/${itemId}?email=${encodeURIComponent(userEmail)}`, {
                method: "DELETE",
            });
            if (!res.ok) throw new Error(await res.text());
            setRealItems(prev => prev.filter(i => i.itemId !== itemId));
            setSelectedItem(null);
        } catch (err: any) {
            alert("Delete failed: " + err.message);
        } finally {
            setIsDeleting(false);
        }
    };

    // Calculate dynamic data
    const categoryCounts = realItems.reduce((acc, item) => {
        const cat = item.category || 'HOBBY';
        acc[cat] = (acc[cat] || 0) + 1;
        return acc;
    }, {} as Record<string, number>);

    const trendingHobbiesData = (Object.entries(categoryCounts) as [string, number][])
        .map(([name, count]) => {
            const firstItem = realItems.find(i => (i.category || 'HOBBY') === name);
            const image = firstItem?.imageUrls?.[0] || firstItem?.imageUrl || '';
            return {
                id: name,
                name: name,
                description: `Explore ${count} items in ${name}`,
                image: image,
                members: count * 12, // Mock metric
                trendingScore: Math.floor(Math.random() * 50) + 10,
                count
            };
        })
        .sort((a, b) => b.count - a.count)
        .slice(0, 4);

    // Filtered items for My Items view
    const displayedItems = showMyItems && userEmail
        ? realItems.filter(i => i.email === userEmail)
        : realItems;

    const liveAuctionsData = displayedItems.filter(item => item.status === 'ACTIVE' && item.category !== 'BUSINESS');

    const modalImages = selectedItem?.imageUrls?.length > 0 ? selectedItem.imageUrls : (selectedItem?.imageUrl ? [selectedItem.imageUrl] : []);

    return (
        <div className="flex flex-col min-h-full pb-20 md:pb-8">
            {/* Hero Section */}
            <section className="relative pt-12 pb-10 md:pt-16 md:pb-12 px-4 overflow-hidden">
                <div className="max-w-5xl mx-auto relative z-10 text-center">
                    <motion.div
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.6 }}
                        className="mb-4 inline-flex items-center gap-2 px-4 py-1.5 rounded-full glass-panel text-sm text-blue-300 border-blue-500/20"
                    >
                        <span className="relative flex h-2 w-2">
                            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                            <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500"></span>
                        </span>
                        BidMyHobby 2.0 is Live
                    </motion.div>

                    <motion.h1
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.6, delay: 0.1 }}
                        className="text-4xl md:text-5xl font-black mb-4 tracking-tight text-white leading-tight"
                    >
                        Your Passion, <span className="text-gradient">Our Marketplace.</span>
                    </motion.h1>

                    <motion.p
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.6, delay: 0.2 }}
                        className="text-base md:text-lg text-slate-400 mb-8 max-w-2xl mx-auto leading-relaxed"
                    >
                        The premium destination for collectors and enthusiasts. AI-powered matching, expert profiles, and a community that shares your obsessions.
                    </motion.p>

                    <motion.div
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.6, delay: 0.3 }}
                        className="flex flex-col sm:flex-row items-center justify-center gap-4"
                    >
                        <Link 
                            href="/upload"
                            className="w-full sm:w-auto px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-all shadow-lg shadow-emerald-900/40 flex items-center justify-center gap-2 group"
                        >
                            <Upload className="w-5 h-5 group-hover:-translate-y-0.5 transition-transform" />
                            Upload your hobby or business idea (visiting card)
                        </Link>
                        <button 
                            onClick={() => {
                                const el = document.getElementById('recently-uploaded') || document.getElementById('trending-hobbies');
                                el?.scrollIntoView({ behavior: 'smooth' });
                            }}
                            className="w-full sm:w-auto px-6 py-3 glass-panel hover:bg-white/10 text-white font-semibold rounded-xl transition-all flex items-center justify-center gap-2 group"
                        >
                            Start Exploring
                            <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
                        </button>
                    </motion.div>
                </div>

                <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-blue-600/20 blur-[120px] rounded-full -z-10 pointer-events-none" />
            </section>

            {/* Items Section */}
            {displayedItems.length > 0 && (
                <section id="recently-uploaded" className="px-4 py-8 md:py-12 max-w-7xl mx-auto w-full border-t border-white/5">
                    <div className="flex items-center justify-between mb-10">
                        <div>
                            <h2 className="text-3xl font-bold flex items-center gap-3">
                                <Box className="text-emerald-500 w-8 h-8" />
                                {showMyItems ? "My Uploads" : "Recently Uploaded"}
                            </h2>
                            <p className="text-slate-400 mt-2">
                                {showMyItems
                                    ? `${displayedItems.length} item${displayedItems.length !== 1 ? "s" : ""} you've uploaded.`
                                    : "Real items freshly uploaded and moderated by AI."}
                            </p>
                        </div>
                        <div className="flex items-center gap-3">
                            {showMyItems && (
                                <Link href="/" className="text-sm text-slate-400 hover:text-white transition-colors">← All items</Link>
                            )}
                            <button
                                onClick={() => loadItems()}
                                title="Refresh catalog"
                                className="p-2 glass-panel hover:bg-white/10 text-slate-400 hover:text-white rounded-lg transition-all"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
                            </button>
                        </div>
                    </div>

                    <div className="flex overflow-x-auto hide-scrollbar snap-x snap-mandatory pb-6 -mx-4 px-4 md:grid md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {displayedItems.map((item, index) => (
                            <div key={item.itemId || index} className="snap-center shrink-0 w-[85vw] md:w-auto md:shrink-none">
                                <ItemCard item={item} index={index} onItemClick={setSelectedItem} userEmail={userEmail} />
                            </div>
                        ))}
                    </div>

                    {showMyItems && displayedItems.length === 0 && (
                        <div className="text-center py-16 text-slate-500">
                            <Upload className="w-12 h-12 mx-auto mb-4 opacity-30" />
                            <p className="text-lg mb-2">No uploads yet</p>
                            <Link href="/upload" className="text-blue-400 hover:text-blue-300 transition-colors text-sm">Upload your first hobby item →</Link>
                        </div>
                    )}
                </section>
            )}

            {/* Loading State */}
            {isFetchingItems && (
                <section id="recently-uploaded" className="px-4 py-12 max-w-7xl mx-auto w-full border-t border-white/5">
                    <div className="flex items-center gap-3 mb-8">
                        <Box className="text-emerald-500 w-8 h-8" />
                        <h2 className="text-3xl font-bold">Recently Uploaded</h2>
                    </div>
                    <div className="flex flex-col items-center justify-center py-20 gap-5">
                        <div className="relative w-14 h-14">
                            <div className="absolute inset-0 rounded-full border-4 border-white/10" />
                            <div className="absolute inset-0 rounded-full border-4 border-t-emerald-500 animate-spin" />
                        </div>
                        <p className="text-slate-400 text-sm animate-pulse">Loading catalog items…</p>
                    </div>
                    {/* Skeleton cards */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mt-4 opacity-30">
                        {[0,1,2,3].map(i => (
                            <div key={i} className="glass-panel overflow-hidden animate-pulse">
                                <div className="h-48 bg-white/5" />
                                <div className="p-5 space-y-3">
                                    <div className="h-4 bg-white/10 rounded w-3/4" />
                                    <div className="h-3 bg-white/10 rounded w-full" />
                                    <div className="h-3 bg-white/10 rounded w-2/3" />
                                </div>
                            </div>
                        ))}
                    </div>
                </section>
            )}

            {/* Error / Empty State */}
            {!isFetchingItems && fetchError && (
                <section className="px-4 py-12 max-w-7xl mx-auto w-full border-t border-white/5">
                    <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
                        <div className="w-16 h-16 rounded-full bg-red-500/10 flex items-center justify-center border border-red-500/20 mb-2">
                            <Box className="w-8 h-8 text-red-400 opacity-60" />
                        </div>
                        <p className="text-slate-300 font-medium">{fetchError}</p>
                        <p className="text-slate-500 text-sm max-w-md">The backend may still be warming up. Please wait a moment and try again.</p>
                        <button
                            onClick={() => loadItems()}
                            className="mt-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-all shadow-lg flex items-center gap-2"
                        >
                            <Loader2 className="w-4 h-4" />
                            Retry Loading Items
                        </button>
                    </div>
                </section>
            )}

            {/* Live Auctions */}
            {liveAuctionsData.length > 0 && (
                <section className="px-4 py-8 md:py-16 max-w-7xl mx-auto w-full border-t border-white/5 relative">
                    <div className="absolute left-1/4 bottom-0 w-[500px] h-[500px] bg-indigo-600/10 blur-[100px] rounded-full -z-10 pointer-events-none" />

                    <div className="flex items-center justify-between mb-10">
                        <div>
                            <h2 className="text-3xl font-bold flex items-center gap-3">
                                <Gavel className="text-blue-500 w-8 h-8" />
                                Live Auctions
                            </h2>
                            <p className="text-slate-400 mt-2">Place your bids on these active hobby items.</p>
                        </div>
                    </div>

                    <div className="flex overflow-x-auto hide-scrollbar snap-x snap-mandatory pb-6 -mx-4 px-4 md:grid md:grid-cols-3 gap-6">
                        {liveAuctionsData.map((auction, index) => (
                            <motion.div
                                key={auction.itemId}
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ duration: 0.5, delay: 0.2 + index * 0.1 }}
                                onClick={() => setSelectedItem(auction)}
                                className="snap-center shrink-0 w-[85vw] md:w-auto glass-panel p-4 flex gap-5 group hover:bg-white/10 transition-colors cursor-pointer"
                            >
                                <div className="w-24 h-24 rounded-lg overflow-hidden shrink-0 relative bg-black/40">
                                    {(auction.imageUrls?.[0] || auction.imageUrl) && (
                                        <img
                                            src={getCatalogImageUrl(auction.imageUrls?.[0] || auction.imageUrl)}
                                            alt={auction.name}
                                            className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500"
                                        />
                                    )}
                                </div>
                                <div className="flex flex-col justify-between flex-1">
                                    <div>
                                        <span className="text-[10px] uppercase font-bold text-blue-500 tracking-wider">
                                            {auction.category || 'HOBBY'}
                                        </span>
                                        <h4 className="font-semibold text-white mt-1 line-clamp-2">{auction.name}</h4>
                                    </div>
                                    <div className="flex items-end justify-between mt-2 flex-wrap gap-2">
                                        <div>
                                            <span className="text-xs text-slate-400 block mb-0.5">Starting From</span>
                                            <span className="font-mono text-emerald-400 font-medium">{formatCurrency(auction.basePrice, auction.currency)}</span>
                                        </div>
                                        <div className="text-right">
                                            <span className="text-xs text-emerald-400 font-medium flex items-center justify-end gap-1">
                                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                                                Active
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </motion.div>
                        ))}
                    </div>
                </section>
            )}

            {/* Trending Hobbies (Dynamic) */}
            {trendingHobbiesData.length > 0 && (
                <section id="trending-hobbies" className="px-4 py-8 md:py-12 max-w-7xl mx-auto w-full border-t border-white/5">
                    <div className="flex items-center justify-between mb-10">
                        <div>
                            <h2 className="text-3xl font-bold flex items-center gap-3">
                                <TrendingUp className="text-blue-500 w-8 h-8" />
                                Trending Hobbies
                            </h2>
                            <p className="text-slate-400 mt-2">Discover what the community is obsessing over based on recent uploads.</p>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {trendingHobbiesData.map((hobby, index) => (
                            <motion.div
                                key={hobby.id}
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ duration: 0.5, delay: index * 0.1 }}
                                className="glass-panel group overflow-hidden cursor-pointer"
                            >
                                <div className="h-48 relative overflow-hidden bg-black/40">
                                    <div className="absolute inset-0 bg-gradient-to-t from-background via-black/20 to-transparent z-10" />
                                    {hobby.image && (
                                        <img
                                            src={getCatalogImageUrl(hobby.image)}
                                            alt={hobby.name}
                                            className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-700 ease-in-out"
                                        />
                                    )}
                                    <div className="absolute bottom-4 left-4 z-20">
                                        <h3 className="text-xl font-bold text-white uppercase">{hobby.name}</h3>
                                    </div>
                                </div>
                                <div className="p-5">
                                    <p className="text-slate-400 text-sm mb-4 line-clamp-2">{hobby.description}</p>
                                    <div className="flex items-center justify-between text-xs font-medium">
                                        <span className="flex items-center gap-1 text-slate-300">
                                            <Users className="w-4 h-4 text-blue-400" />
                                            {hobby.members} members
                                        </span>
                                        <span className="bg-blue-500/20 text-blue-300 px-2 py-1 rounded-md">
                                            +{hobby.trendingScore}%
                                        </span>
                                    </div>
                                </div>
                            </motion.div>
                        ))}
                    </div>
                </section>
            )}

            {/* Connect with Founder */}
            <section className="px-4 py-16 border-t border-white/5 mt-10 relative overflow-hidden">
                <div className="absolute inset-0 bg-pink-900/5 -z-10" />
                <div className="max-w-4xl mx-auto text-center flex flex-col items-center">
                    <div className="w-16 h-16 rounded-full bg-pink-600/20 flex items-center justify-center mb-4 border border-pink-500/20">
                        <Instagram className="w-8 h-8 text-pink-400" />
                    </div>
                    <h2 className="text-3xl font-bold text-white mb-4">Built for Collectors, by a Collector</h2>
                    <p className="text-slate-400 mb-8 max-w-2xl text-center leading-relaxed text-lg">
                        BidMyHobby is actively being built right now. I&apos;m shipping fresh features and improvements every single weekend. Stick around as we create the ultimate community for your passions!
                    </p>
                    <a 
                        href="https://www.instagram.com/balu.reelz/" 
                        target="_blank" 
                        rel="noopener noreferrer"
                        className="px-8 py-4 bg-gradient-to-r from-[#833ab4] via-[#fd1d1d] to-[#fcb045] hover:opacity-90 text-white rounded-xl flex items-center gap-3 font-semibold transition-all shadow-lg shadow-pink-900/30 hover:-translate-y-1"
                    >
                        <Instagram className="w-6 h-6" />
                        Follow on Instagram
                    </a>
                </div>
            </section>

            {/* Item Details & Bidding Modal */}
            <AnimatePresence>
                {selectedItem && (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 backdrop-blur-sm p-4 md:p-8"
                        onClick={() => setSelectedItem(null)}
                    >
                        <button
                            className="absolute top-4 right-4 md:top-6 md:right-6 p-2 bg-white/10 hover:bg-white/20 rounded-full text-white transition-colors cursor-pointer z-[60]"
                            onClick={() => setSelectedItem(null)}
                        >
                            <X className="w-6 h-6" />
                        </button>
                        
                        <motion.div
                            initial={{ scale: 0.95, opacity: 0, y: 20 }}
                            animate={{ scale: 1, opacity: 1, y: 0 }}
                            exit={{ scale: 0.95, opacity: 0, y: 20 }}
                            className="flex flex-col md:flex-row bg-[#0B1120] border border-white/10 rounded-2xl overflow-hidden max-w-6xl w-full max-h-[90vh] shadow-2xl relative"
                            onClick={(e) => e.stopPropagation()}
                        >
                            {/* Image Side Carousel */}
                            <div className="w-full md:w-1/2 bg-black/50 flex flex-col relative group">
                                <div className="flex-1 flex items-center justify-center p-4 min-h-[40vh] relative">
                                    {modalImages.length > 0 ? (
                                        <img 
                                            src={getCatalogImageUrl(modalImages[modalImageIndex])} 
                                            alt={selectedItem.name}
                                            className="max-w-full max-h-[60vh] object-contain rounded-xl transition-opacity duration-300"
                                        />
                                    ) : (
                                        <div className="text-slate-500">No Image Available</div>
                                    )}

                                    {modalImages.length > 1 && (
                                        <>
                                            <button 
                                                onClick={() => setModalImageIndex((prev) => (prev === 0 ? modalImages.length - 1 : prev - 1))}
                                                className="absolute left-4 top-1/2 -translate-y-1/2 p-2 bg-black/60 hover:bg-blue-600 text-white rounded-full transition-all opacity-0 group-hover:opacity-100 shadow-xl"
                                            >
                                                <ChevronLeft className="w-6 h-6" />
                                            </button>
                                            <button 
                                                onClick={() => setModalImageIndex((prev) => (prev + 1) % modalImages.length)}
                                                className="absolute right-4 top-1/2 -translate-y-1/2 p-2 bg-black/60 hover:bg-blue-600 text-white rounded-full transition-all opacity-0 group-hover:opacity-100 shadow-xl"
                                            >
                                                <ChevronRight className="w-6 h-6" />
                                            </button>
                                            <div className="absolute bottom-4 right-4 bg-black/70 backdrop-blur-md px-3 py-1 rounded-full text-xs font-bold text-white border border-white/10">
                                                {modalImageIndex + 1} / {modalImages.length}
                                            </div>
                                        </>
                                    )}
                                </div>
                            </div>
                            
                            {/* Details & Bidding Side */}
                            <div className="w-full md:w-1/2 p-6 md:p-8 overflow-y-auto custom-scrollbar flex flex-col bg-background/50">
                                <div className="mb-6">
                                    <div className="flex items-center gap-3 mb-4">
                                        <span className={`px-2 py-1 text-[10px] uppercase font-bold rounded-md ${selectedItem.status === 'ACTIVE' ? 'bg-emerald-500/20 text-emerald-300' : 'bg-yellow-500/20 text-yellow-300'}`}>
                                            {selectedItem.status}
                                        </span>
                                        <span className="px-2 py-1 bg-white/10 text-[10px] uppercase font-bold text-slate-300 rounded-md border border-white/10">
                                            {selectedItem.category || 'HOBBY'}
                                        </span>
                                    </div>
                                    <h2 className="text-2xl md:text-3xl font-bold text-white mb-2 leading-tight">
                                        {selectedItem.name}
                                    </h2>
                                    <p className="text-sm text-slate-400">By: {maskIdentity(selectedItem.email)}</p>
                                </div>
                                
                                <div className="space-y-6 flex-1">
                                    {/* Description / Contact Block */}
                                    {selectedItem.category === 'BUSINESS' ? (() => {
                                        const contact = parseBusinessContact(selectedItem.description);
                                        return (
                                            <div className="bg-gradient-to-br from-indigo-500/10 to-purple-500/5 border border-indigo-500/20 rounded-xl p-5 shadow-inner space-y-4">
                                                <h3 className="text-indigo-400 font-semibold flex items-center gap-2 text-sm uppercase tracking-wider">
                                                    <span className="relative flex h-2 w-2">
                                                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-indigo-400 opacity-75"></span>
                                                        <span className="relative inline-flex rounded-full h-2 w-2 bg-indigo-500"></span>
                                                    </span>
                                                    Business Contact Details
                                                </h3>
                                                {contact.intro && (
                                                    <p className="text-slate-300 text-sm leading-relaxed">{contact.intro}</p>
                                                )}
                                                <div className="grid grid-cols-1 gap-2">
                                                    {contact.phone && (
                                                        <div className="flex items-center gap-3 bg-white/5 px-4 py-2.5 rounded-lg border border-white/5">
                                                            <Phone className="w-4 h-4 text-indigo-400 shrink-0" />
                                                            <span className="text-white text-sm font-mono">{contact.phone}</span>
                                                        </div>
                                                    )}
                                                    {contact.email && (
                                                        <div className="flex items-center gap-3 bg-white/5 px-4 py-2.5 rounded-lg border border-white/5">
                                                            <Mail className="w-4 h-4 text-indigo-400 shrink-0" />
                                                            <span className="text-slate-300 text-sm break-all">{contact.email}</span>
                                                        </div>
                                                    )}
                                                    {contact.website && (
                                                        <a href={contact.website.startsWith('http') ? contact.website : `https://${contact.website}`}
                                                            target="_blank" rel="noopener noreferrer"
                                                            className="flex items-center gap-3 bg-white/5 px-4 py-2.5 rounded-lg border border-white/5 hover:border-indigo-500/30 hover:bg-indigo-500/10 transition-colors group">
                                                            <Globe className="w-4 h-4 text-indigo-400 shrink-0" />
                                                            <span className="text-indigo-300 text-sm group-hover:text-indigo-200 truncate">{contact.website}</span>
                                                        </a>
                                                    )}
                                                    {contact.instagram && (
                                                        <a href={`https://instagram.com/${contact.instagram.replace('@','')}`}
                                                            target="_blank" rel="noopener noreferrer"
                                                            className="flex items-center gap-3 bg-white/5 px-4 py-2.5 rounded-lg border border-white/5 hover:border-pink-500/30 hover:bg-pink-500/10 transition-colors group">
                                                            <Instagram className="w-4 h-4 text-pink-400 shrink-0" />
                                                            <span className="text-pink-300 text-sm group-hover:text-pink-200">@{contact.instagram.replace('@','')}</span>
                                                        </a>
                                                    )}
                                                    {!contact.phone && !contact.email && !contact.website && !contact.instagram && (
                                                        <p className="text-slate-500 text-sm italic">Contact details being extracted…</p>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })() : (
                                        <div className="bg-gradient-to-br from-blue-500/10 to-indigo-500/5 border border-blue-500/20 rounded-xl p-5 shadow-inner">
                                            <h3 className="text-blue-400 font-semibold mb-3 flex items-center gap-2 text-sm uppercase tracking-wider">
                                                <span className="relative flex h-2 w-2">
                                                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                                                    <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500"></span>
                                                </span>
                                                AI Analysis & Description
                                            </h3>
                                            <div className="prose prose-invert prose-sm max-w-none text-slate-300 leading-relaxed whitespace-pre-wrap">
                                                {selectedItem.description || "The AI is currently analyzing this item. Check back soon for detailed insights."}
                                            </div>
                                        </div>
                                    )}
                                    
                                    {/* Price & Specs — hidden for business/visiting cards */}
                                    {selectedItem.category !== 'BUSINESS' && (
                                        <div className="space-y-3">
                                          <div className="grid grid-cols-2 gap-4">
                                            <div className="bg-white/5 border border-white/5 p-4 rounded-xl flex flex-col justify-center">
                                                <span className="text-slate-400 text-xs block mb-1">A.I Estimated Value</span>
                                                <span className="text-emerald-400 font-mono text-xl font-bold">
                                                    {selectedItem.suggestedPrice ? formatCurrency(selectedItem.suggestedPrice, selectedItem.currency) : '—'}
                                                </span>
                                            </div>
                                            <div className="bg-white/5 border border-white/5 p-4 rounded-xl flex flex-col justify-center">
                                                <span className="text-slate-400 text-xs block mb-1">Seller Asking Price</span>
                                                <span className="text-white font-mono text-xl font-bold">
                                                    {formatCurrency(selectedItem.basePrice, selectedItem.currency)}
                                                </span>
                                            </div>
                                          </div>
                                          <div className="rounded-xl border border-blue-500/20 bg-blue-500/10 px-4 py-3 text-sm text-blue-200">
                                            Bidding starts at <strong>{formatCurrency(biddingFloor(selectedItem), selectedItem.currency)}</strong>, the lower of these two prices.
                                          </div>
                                        </div>
                                    )}

                                    {/* Additional Details */}
                                    <div className="flex gap-4 flex-wrap">
                                        {selectedItem.size && (
                                            <div className="flex items-center gap-2 text-sm text-slate-400 bg-white/5 px-4 py-3 rounded-xl border border-white/5">
                                                <Box className="w-4 h-4 text-slate-500" />
                                                <span className="font-medium text-slate-300">Dimensions:</span> {selectedItem.size}
                                            </div>
                                        )}
                                        
                                        {selectedItem.city && (
                                            <div className="flex items-center gap-2 text-sm text-slate-400 bg-white/5 px-4 py-3 rounded-xl border border-white/5">
                                                <span>📍</span>
                                                <span className="font-medium text-slate-300">Location:</span> {selectedItem.city}
                                            </div>
                                        )}
                                    </div>

                                    {/* --- AUCTION & BIDDING SECTION --- */}
                                    {selectedItem.category !== 'BUSINESS' && (
                                        <div className="border-t border-white/10 pt-6 mt-6">
                                            <h3 className="text-xl font-bold text-white mb-4 flex items-center gap-2">
                                                <Gavel className="w-5 h-5 text-blue-400" />
                                                Live Bids
                                            </h3>
                                            {/* List of current bids */}
                                            <div className="space-y-3 mb-6 max-h-48 overflow-y-auto custom-scrollbar pr-2">
                                                {bids.length === 0 ? (
                                                    <p className="text-slate-500 text-sm italic bg-white/5 p-4 rounded-lg text-center">No bids placed yet. Be the first!</p>
                                                ) : (
                                                    bids.map((bid, i) => (
                                                        <div key={bid.bidId || i} className="flex items-center justify-between bg-white/5 p-3 rounded-lg border border-white/10">
                                                            <div>
                                                                <span className="text-sm font-medium text-slate-200 block">
                                                                    {isItemCreator ? bid.bidderEmail : maskIdentity(bid.bidderEmail)}
                                                                </span>
                                                                <span className="text-xs text-slate-500">{new Date(bid.timestamp).toLocaleString()}</span>
                                                            </div>
                                                            <div className="flex items-center gap-4">
                                                                <span className="font-mono font-bold text-emerald-400">
                                                                    {formatCurrency(bid.bidAmount, bid.currency || selectedItem?.currency)}
                                                                </span>
                                                                {/* Show Approve button if logged in user is the creator and item is ACTIVE */}
                                                                {canDelete && selectedItem.status === 'ACTIVE' && (
                                                                    <>
                                                                        <button 
                                                                            onClick={() => handleDeleteItem(selectedItem.itemId)}
                                                                            disabled={isDeleting}
                                                                            className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-xs font-bold text-white rounded-md transition-colors flex items-center gap-1 disabled:opacity-50"
                                                                        >
                                                                            {isDeleting ? <Loader2 className="w-3 h-3 animate-spin" /> : <Trash2 className="w-3 h-3" />}
                                                                            Delete
                                                                        </button>
                                                                        <button 
                                                                            onClick={() => handleApproveWinner(selectedItem.itemId, selectedItem.email)}
                                                                            disabled={isApproving}
                                                                            className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-xs font-bold text-white rounded-md transition-colors flex items-center gap-1 disabled:opacity-50"
                                                                        >
                                                                            {isApproving ? <Loader2 className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
                                                                            Approve
                                                                        </button>
                                                                    </>
                                                                )}
                                                            </div>
                                                        </div>
                                                    ))
                                                )}
                                            </div>

                                            {/* Bidding Controls */}
                                            {selectedItem.status === 'SALE_IN_PROGRESS' ? (
                                                <div className="bg-yellow-500/10 border border-yellow-500/30 p-4 rounded-xl text-center">
                                                    <p className="text-yellow-400 font-semibold mb-1">Sale in Progress</p>
                                                    <p className="text-sm text-yellow-500/80">Bidding is closed as the creator has accepted a bid.</p>
                                                </div>
                                            ) : isItemCreator ? (
                                                <div className="bg-blue-500/10 border border-blue-500/30 p-4 rounded-xl text-center">
                                                    <p className="text-blue-400 font-medium text-sm">You are the creator of this item.</p>
                                                    <p className="text-xs text-slate-400 mt-1">Review the bids above and approve a winner. They will be notified via WhatsApp.</p>
                                                </div>
                                            ) : userEmail ? (
                                                <form onSubmit={handlePlaceBid} className="space-y-4">
                                                    {bidError && <p className="text-red-400 text-xs">{bidError}</p>}
                                                    {bidSuccess && <p className="text-emerald-400 text-xs">{bidSuccess}</p>}
                                                    <div className="flex gap-3">
                                                        <div className="relative flex-1">
                                                            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                                                <span className="text-slate-400 font-mono font-bold">
                                                                    {CURRENCY_SYMBOLS[selectedItem.currency] || selectedItem.currency || '₹'}
                                                                </span>
                                                            </div>
                                                            <input 
                                                                type="number" 
                                                                step="0.01" 
                                                                required
                                                                min={biddingFloor(selectedItem)}
                                                                value={newBidAmount}
                                                                onChange={(e) => setNewBidAmount(e.target.value)}
                                                                placeholder={`Min ${formatCurrency(biddingFloor(selectedItem), selectedItem.currency)}`}
                                                                className="w-full pl-10 pr-4 py-3 bg-black/50 border border-white/10 rounded-xl text-white focus:ring-2 focus:ring-blue-500/50 outline-none"
                                                            />
                                                        </div>
                                                        <button 
                                                            type="submit"
                                                            disabled={isLoading}
                                                            className="px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-xl transition-colors shadow-lg shadow-emerald-900/30 flex items-center gap-2 disabled:opacity-50"
                                                        >
                                                            {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : "Place Bid"}
                                                        </button>
                                                    </div>
                                                </form>
                                            ) : (
                                                <div className="bg-white/5 border border-white/10 p-4 rounded-xl text-center">
                                                    <p className="text-slate-400 text-sm mb-3">You must be logged in to place a bid.</p>
                                                    <Link href="/login" className="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-bold rounded-lg inline-block transition-colors">
                                                        Log In to Bid
                                                    </Link>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>

                                {/* Delete Button for creator / admin */}
                                {canDelete && (
                                    <div className="mt-6 pt-4 border-t border-white/5">
                                        <button
                                            onClick={() => handleDeleteItem(selectedItem.itemId)}
                                            disabled={isDeleting}
                                            className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-xl text-sm font-semibold text-red-400 border border-red-500/30 hover:bg-red-500/10 transition-colors disabled:opacity-50"
                                        >
                                            {isDeleting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
                                            {isAdmin && !isItemCreator ? "Delete (Admin)" : "Delete My Item"}
                                        </button>
                                    </div>
                                )}
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
}

export default function Home() {
    return (
        <Suspense fallback={
            <div className="min-h-screen flex items-center justify-center">
                <span className="w-8 h-8 border-2 border-blue-500/30 border-t-blue-500 rounded-full animate-spin" />
            </div>
        }>
            <HomeContent />
        </Suspense>
    );
}
