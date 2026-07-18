"use client";

import { motion, AnimatePresence } from "framer-motion";
import { UploadCloud, X, Send, Image as ImageIcon, AlertCircle, Loader2, ShieldCheck, Briefcase, Tag } from "lucide-react";
import { useState, useCallback, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";

const MAX_FILE_SIZE = 13 * 1024 * 1024; // 13MB
const MAX_FILES = 4;

type ItemType = "HOBBY" | "BUSINESS";
type Currency = "INR" | "GBP" | "USD";

const CURRENCY_CONFIG: Record<Currency, { symbol: string; placeholder: string; label: string; flag: string }> = {
    INR: { symbol: "₹", placeholder: "e.g. 2,500", label: "INR", flag: "🇮🇳" },
    GBP: { symbol: "£", placeholder: "e.g. 150.00", label: "GBP", flag: "🇬🇧" },
    USD: { symbol: "$", placeholder: "e.g. 180.00", label: "USD", flag: "🇺🇸" },
};

function detectDefaultCurrency(phone: string | null): Currency {
    if (!phone) return "INR";
    const digits = phone.replace(/[^0-9]/g, "");
    if (phone.startsWith("+44") || digits.startsWith("44")) return "GBP";
    if (phone.startsWith("+1") || digits.startsWith("1")) return "USD";
    return "INR"; // default for +91 and others
}

export default function UploadPage() {
    const router = useRouter();
    const [email, setEmail] = useState<string | null>(null);

    // Item type toggle
    const [itemType, setItemType] = useState<ItemType>("HOBBY");

    // Shared fields
    const [files, setFiles] = useState<File[]>([]);
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [city, setCity] = useState("");

    // Hobby-only fields
    const [basePrice, setBasePrice] = useState("");
    const [currency, setCurrency] = useState<Currency>("INR");
    const [size, setSize] = useState("");

    // Business-only fields
    const [contactInfo, setContactInfo] = useState("");

    const [error, setError] = useState<string | null>(null);
    const [isUploading, setIsUploading] = useState(false);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [retryCount, setRetryCount] = useState(0);
    const [isRetrying, setIsRetrying] = useState(false);
    const MAX_RETRIES = 2;

    useEffect(() => {
        const storedEmail = localStorage.getItem("userEmail");
        if (!storedEmail) {
            router.push("/login");
        } else {
            setEmail(storedEmail);
            setCurrency(detectDefaultCurrency(storedEmail));
            setContactInfo(storedEmail);
        }
    }, [router]);

    const handleFileDrop = useCallback((e: React.DragEvent<HTMLDivElement>) => {
        e.preventDefault();
        processFiles(Array.from(e.dataTransfer.files));
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [files]);

    const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files) processFiles(Array.from(e.target.files));
    };

    const processFiles = (newFiles: File[]) => {
        setError(null);
        const acceptedTypes = itemType === "BUSINESS"
            ? ["image/", "application/pdf"]
            : ["image/", "video/"];

        for (const file of newFiles) {
            if (file.size > MAX_FILE_SIZE) { setError(`${file.name} exceeds the 13 MB limit.`); return; }
            const ok = acceptedTypes.some(t => file.type.startsWith(t));
            if (!ok) {
                setError(itemType === "BUSINESS"
                    ? `${file.name} must be an image or PDF.`
                    : `${file.name} must be an image or video.`);
                return;
            }
        }
        if (files.length + newFiles.length > MAX_FILES) {
            setError(`You can upload up to ${MAX_FILES} files per item.`); return;
        }
        setFiles(prev => [...prev, ...newFiles]);
    };

    const removeFile = (index: number) => setFiles(files.filter((_, i) => i !== index));

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setRetryCount(0);
        setIsRetrying(false);

        if (files.length === 0) { setError("Please upload at least one file."); return; }
        if (!email) { setError("Authentication error. Please login again."); return; }

        await performUpload();
    };

    const performUpload = async () => {
        setIsUploading(true);
        setUploadProgress(10);

        const formData = new FormData();
        files.forEach(f => formData.append("files", f));
        formData.append("name", name);
        formData.append("description", description);
        formData.append("email", email || "");
        formData.append("city", city || "");
        formData.append("category", itemType);

        if (itemType === "HOBBY") {
            formData.append("basePrice", basePrice);
            formData.append("currency", currency);
            formData.append("size", size);
        } else {
            formData.append("basePrice", "0");
            formData.append("currency", "INR");
            formData.append("size", "N/A");
            formData.append("contactInfo", contactInfo);
        }

        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

            // Set up timeout for upload (120 seconds)
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 120000);

            setUploadProgress(30);

            const res = await fetch(`${apiUrl}/catalog/items`, {
                method: "POST",
                body: formData,
                signal: controller.signal
            });

            clearTimeout(timeoutId);
            setUploadProgress(70);

            if (!res.ok) {
                const errorText = await res.text();
                throw new UploadError(errorText || "Upload failed", res.status);
            }

            setUploadProgress(90);
            setSuccessMessage(
                itemType === "HOBBY"
                    ? "✅ Your hobby item is live! You'll be notified via WhatsApp when bids arrive."
                    : "✅ Your visiting card is published in the Connect & Collaborate section!"
            );
            setUploadProgress(100);
            setFiles([]); setName(""); setDescription(""); setBasePrice(""); setSize(""); setCity("");
            setTimeout(() => router.push("/"), 3000);
        } catch (err: any) {
            clearTimeout(0);
            setUploadProgress(0);

            // Determine if error is transient (retryable)
            const isTransient = err.name === "AbortError" ||
                              (err instanceof UploadError && (err.statusCode === 408 || err.statusCode === 503 || err.statusCode === 504));

            let errorMessage = "";

            if (err.name === "AbortError") {
                errorMessage = "⏱️ Upload timed out (120s). Check your internet connection and try again.";
            } else if (err instanceof UploadError) {
                if (err.statusCode === 403) {
                    errorMessage = "❌ S3 Access Denied: Upload storage is unavailable. Check bucket permissions.";
                } else if (err.statusCode === 400) {
                    errorMessage = `❌ Invalid upload: ${err.message}. Check file format and size.`;
                } else if (err.statusCode === 413) {
                    errorMessage = "❌ File too large: Total upload exceeds 60 MB limit.";
                } else if (err.statusCode === 408 || err.statusCode === 503 || err.statusCode === 504) {
                    errorMessage = `⚠️ Server error (${err.statusCode}). Retrying...`;
                } else {
                    errorMessage = err.message || "Upload failed. Please try again.";
                }
            } else {
                errorMessage = err.message || "Something went wrong during the upload.";
            }

            // Auto-retry for transient errors
            if (isTransient && retryCount < MAX_RETRIES) {
                setRetryCount(retryCount + 1);
                setIsRetrying(true);
                setError(`${errorMessage} (Retry ${retryCount + 1}/${MAX_RETRIES})`);
                await new Promise(r => setTimeout(r, 2000 * (retryCount + 1))); // Exponential backoff
                setIsRetrying(false);
                await performUpload();
            } else {
                setError(errorMessage);
                setIsUploading(false);
                if (isTransient && retryCount >= MAX_RETRIES) {
                    setError(`${errorMessage} (Max retries exceeded. Check your connection and try again.)`);
                }
            }
        } finally {
            if (!isRetrying) {
                setIsUploading(false);
            }
        }
    };

    class UploadError extends Error {
        statusCode: number;
        constructor(message: string, statusCode: number) {
            super(message);
            this.statusCode = statusCode;
        }
    }

    if (!email) return <div className="min-h-screen flex text-white justify-center items-center"><Loader2 className="w-6 h-6 animate-spin text-indigo-400" /></div>;

    const cfg = CURRENCY_CONFIG[currency];

    return (
        <div className="min-h-screen py-12 px-4 sm:px-6 lg:px-8">
            <motion.div
                initial={{ opacity: 0, scale: 0.97 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.4 }}
                className="max-w-4xl mx-auto"
            >
                <div className="mb-8 flex items-center justify-between">
                    <div>
                        <h1 className="text-3xl font-black text-white">Upload</h1>
                        <p className="text-slate-400 mt-1">Uploading as <span className="text-blue-400">{email}</span></p>
                    </div>
                    <Link href="/" className="text-sm text-slate-400 hover:text-white transition-colors">← Back</Link>
                </div>

                {/* Item Type Toggle */}
                <div className="flex gap-3 mb-6">
                    {(["HOBBY", "BUSINESS"] as ItemType[]).map(type => (
                        <button
                            key={type}
                            onClick={() => { setItemType(type); setFiles([]); setError(null); }}
                            className={`flex-1 flex items-center justify-center gap-2.5 py-3.5 px-5 rounded-xl font-semibold text-sm border transition-all ${
                                itemType === type
                                    ? type === "HOBBY"
                                        ? "bg-emerald-600/20 border-emerald-500/50 text-emerald-300"
                                        : "bg-indigo-600/20 border-indigo-500/50 text-indigo-300"
                                    : "bg-white/5 border-white/10 text-slate-400 hover:text-white hover:bg-white/10"
                            }`}
                        >
                            {type === "HOBBY" ? <Tag className="w-4 h-4" /> : <Briefcase className="w-4 h-4" />}
                            {type === "HOBBY" ? "Hobby Item" : "Visiting Card / Business"}
                        </button>
                    ))}
                </div>

                <div className="glass-panel p-6 md:p-8 relative overflow-hidden">
                    <div className={`absolute top-0 left-0 w-full h-1 bg-gradient-to-r ${
                        itemType === "HOBBY" ? "from-emerald-500 via-blue-500 to-teal-500" : "from-indigo-500 via-purple-500 to-pink-500"
                    }`} />

                    <form onSubmit={handleSubmit} className="space-y-7">

                        {/* Error / Success */}
                        <AnimatePresence>
                            {error && (
                                <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
                                    className="bg-red-500/10 border border-red-500/50 rounded-xl p-4 flex items-center text-red-200">
                                    <AlertCircle className="w-5 h-5 mr-3 shrink-0" /><p className="text-sm">{error}</p>
                                </motion.div>
                            )}
                            {successMessage && (
                                <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
                                    className="bg-emerald-500/10 border border-emerald-500/50 rounded-xl p-4 flex items-center text-emerald-200">
                                    <ShieldCheck className="w-5 h-5 mr-3 shrink-0 text-emerald-400" /><p className="text-sm">{successMessage}</p>
                                </motion.div>
                            )}
                        </AnimatePresence>

                        {/* File Dropzone */}
                        <div>
                            <label className="block text-sm font-medium text-slate-300 mb-2">
                                {itemType === "HOBBY" ? `Photos / Videos (up to ${MAX_FILES}, <13 MB each)` : `Visiting Card / Business Images or PDF (up to ${MAX_FILES})`}
                            </label>
                            <div
                                onDragOver={e => e.preventDefault()}
                                onDrop={handleFileDrop}
                                className={`border-2 border-dashed rounded-2xl p-8 text-center transition-all ${files.length < MAX_FILES ? "border-white/20 hover:border-blue-500/50 hover:bg-white/5 cursor-pointer" : "border-white/10 opacity-50 cursor-not-allowed"}`}
                            >
                                <input
                                    type="file" multiple
                                    accept={itemType === "BUSINESS" ? "image/*,application/pdf" : "image/*,video/*"}
                                    className="hidden" id="file-upload"
                                    onChange={handleFileInput}
                                    disabled={files.length >= MAX_FILES}
                                />
                                <label htmlFor={files.length < MAX_FILES ? "file-upload" : ""} className={`flex flex-col items-center ${files.length < MAX_FILES ? "cursor-pointer" : "cursor-not-allowed"}`}>
                                    <UploadCloud className="w-12 h-12 text-slate-400 mb-3" />
                                    <p className="text-sm text-slate-300 mb-1">Drag & drop or click to browse</p>
                                    <p className="text-xs text-slate-500">
                                        {itemType === "BUSINESS" ? "Accepts images & PDF" : "Accepts images & videos"}
                                    </p>
                                </label>
                            </div>

                            {files.length > 0 && (
                                <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-4">
                                    {files.map((file, idx) => (
                                        <div key={idx} className="relative group rounded-xl overflow-hidden bg-white/5 border border-white/10 aspect-square flex flex-col items-center justify-center p-2">
                                            {file.type.startsWith("image/") ? (
                                                // eslint-disable-next-line @next/next/no-img-element
                                                <img src={URL.createObjectURL(file)} alt={file.name} className="w-full h-full object-cover absolute inset-0" />
                                            ) : (
                                                <ImageIcon className="w-8 h-8 text-slate-500 mb-1" />
                                            )}
                                            <div className="absolute bottom-0 left-0 right-0 bg-black/60 px-2 py-1">
                                                <span className="text-[10px] text-slate-400 truncate block">{file.name}</span>
                                            </div>
                                            <button type="button" onClick={() => removeFile(idx)}
                                                className="absolute top-2 right-2 p-1 bg-red-500/80 hover:bg-red-500 text-white rounded-full opacity-0 group-hover:opacity-100 transition-opacity z-10">
                                                <X className="w-3 h-3" />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>

                        {/* Text Inputs */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                            <div className="md:col-span-2">
                                <label className="block text-sm font-medium text-slate-300 mb-2">
                                    {itemType === "HOBBY" ? "Item Name" : "Company / Your Name"}
                                </label>
                                <input type="text" value={name} onChange={e => setName(e.target.value)} required
                                    className="w-full bg-background/50 border border-white/10 rounded-xl text-white py-3 px-4 focus:ring-2 focus:ring-blue-500/50 outline-none transition-all"
                                    placeholder={itemType === "HOBBY" ? "e.g. Vintage Stamp Collection" : "e.g. Balaji Creative Studio"} />
                            </div>

                            <div className="md:col-span-2">
                                <label className="block text-sm font-medium text-slate-300 mb-2">Description</label>
                                <textarea value={description} onChange={e => setDescription(e.target.value)} required rows={3}
                                    className="w-full bg-background/50 border border-white/10 rounded-xl text-white py-3 px-4 focus:ring-2 focus:ring-blue-500/50 outline-none transition-all resize-none"
                                    placeholder={itemType === "HOBBY" ? "Condition, history, and details..." : "What does your business do? Key services, USP..."} />
                            </div>

                            {/* Hobby-specific fields */}
                            {itemType === "HOBBY" && (
                                <>
                                    <div>
                                        <label className="block text-sm font-medium text-slate-300 mb-2">Currency</label>
                                        <div className="flex gap-2">
                                            {(Object.keys(CURRENCY_CONFIG) as Currency[]).map(c => (
                                                <button key={c} type="button" onClick={() => setCurrency(c)}
                                                    className={`flex-1 py-2.5 rounded-lg text-sm font-semibold border transition-all ${
                                                        currency === c
                                                            ? "bg-blue-600/30 border-blue-500/60 text-blue-300"
                                                            : "bg-white/5 border-white/10 text-slate-400 hover:text-white"
                                                    }`}>
                                                    {CURRENCY_CONFIG[c].flag} {c}
                                                </button>
                                            ))}
                                        </div>
                                    </div>

                                    <div>
                                        <label className="block text-sm font-medium text-slate-300 mb-2">Base Price ({cfg.symbol})</label>
                                        <div className="relative">
                                            <span className="absolute inset-y-0 left-0 pl-4 flex items-center text-slate-400 font-mono font-bold pointer-events-none">{cfg.symbol}</span>
                                            <input type="number" step="0.01" value={basePrice} onChange={e => setBasePrice(e.target.value)} required
                                                className="w-full bg-background/50 border border-white/10 rounded-xl text-white py-3 pl-9 pr-4 focus:ring-2 focus:ring-blue-500/50 outline-none transition-all"
                                                placeholder={cfg.placeholder} />
                                        </div>
                                    </div>

                                    <div className="md:col-span-2">
                                        <label className="block text-sm font-medium text-slate-300 mb-2">Size / Dimensions</label>
                                        <input type="text" value={size} onChange={e => setSize(e.target.value)} required
                                            className="w-full bg-background/50 border border-white/10 rounded-xl text-white py-3 px-4 focus:ring-2 focus:ring-blue-500/50 outline-none transition-all"
                                            placeholder="e.g. Large, 2×3 ft, A4" />
                                    </div>
                                </>
                            )}

                            {/* Business-specific field */}
                            {itemType === "BUSINESS" && (
                                <div className="md:col-span-2">
                                    <label className="block text-sm font-medium text-slate-300 mb-2">Contact Info</label>
                                    <input type="text" value={contactInfo} onChange={e => setContactInfo(e.target.value)}
                                        className="w-full bg-background/50 border border-white/10 rounded-xl text-white py-3 px-4 focus:ring-2 focus:ring-blue-500/50 outline-none transition-all"
                                        placeholder="Phone / email / website" />
                                </div>
                            )}

                            <div className="md:col-span-2">
                                <label className="block text-sm font-medium text-slate-300 mb-2">City (Optional)</label>
                                <input type="text" value={city} onChange={e => setCity(e.target.value)}
                                    className="w-full bg-background/50 border border-white/10 rounded-xl text-white py-3 px-4 focus:ring-2 focus:ring-blue-500/50 outline-none transition-all"
                                    placeholder="e.g. London, Mumbai, New York" />
                            </div>
                        </div>

                        <div className="space-y-2">
                            <button type="submit" disabled={isUploading}
                                className={`w-full py-4 px-6 rounded-xl font-bold text-white shadow-lg focus:outline-none focus:ring-2 transition-all disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center gap-2 ${
                                    itemType === "HOBBY"
                                        ? "bg-gradient-to-r from-emerald-600 to-blue-600 hover:from-emerald-500 hover:to-blue-500 shadow-blue-900/20 focus:ring-blue-500/50"
                                        : "bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 shadow-indigo-900/20 focus:ring-indigo-500/50"
                                }`}>
                                {isUploading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
                                {isUploading
                                    ? `Uploading & Moderating... ${uploadProgress}%`
                                    : itemType === "HOBBY" ? "Submit Hobby Item" : "Publish Visiting Card"}
                            </button>

                            {/* Progress Bar */}
                            {isUploading && uploadProgress > 0 && (
                                <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}
                                    className="bg-white/5 border border-white/10 rounded-lg overflow-hidden">
                                    <div className={`h-2 transition-all duration-300 ${
                                        uploadProgress >= 100 ? "bg-emerald-500" :
                                        uploadProgress >= 70 ? "bg-blue-500" :
                                        uploadProgress >= 30 ? "bg-cyan-500" : "bg-indigo-500"
                                    }`} style={{ width: `${uploadProgress}%` }} />
                                </motion.div>
                            )}

                            {/* Retry Info */}
                            {isRetrying && retryCount > 0 && (
                                <p className="text-xs text-center text-slate-400 italic">
                                    Retrying in {2 * retryCount} seconds...
                                </p>
                            )}
                        </div>
                    </form>
                </div>
            </motion.div>
        </div>
    );
}
