"use client";

import { motion, AnimatePresence } from "framer-motion";
import { ArrowRight, ShieldCheck, Smartphone, MessageCircle, CheckCircle2 } from "lucide-react";
import Link from "next/link";
import { useState, Suspense, useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";

type Step = "phone" | "otp";
type PollStatus = "waiting" | "verified" | "expired" | "timeout";

function maskPhone(phone: string): string {
    const digits = phone.replace(/[^0-9]/g, "");
    if (digits.length < 6) return phone;
    const visible = digits.slice(-3);
    return `+${"*".repeat(digits.length - 3)}${visible}`;
}

function LoginForm() {
    const [countryCode, setCountryCode] = useState("+91");
    const [localPhone, setLocalPhone] = useState("");
    const [step, setStep] = useState<Step>("phone");
    const [otp, setOtp] = useState(["", "", "", "", "", ""]);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [fallbackOtp, setFallbackOtp] = useState<string | null>(null);
    const [deliveryFailed, setDeliveryFailed] = useState(false);
    const [isPolling, setIsPolling] = useState(false);
    const [pollStatus, setPollStatus] = useState<PollStatus>("waiting");
    const [whatsappOpened, setWhatsappOpened] = useState(false);
    const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);
    const pollCountRef = useRef(0);
    // Store the full phone for use in async polling callbacks
    const fullPhoneRef = useRef("");
    // Prevent double-firing the token auto-login effect
    const tokenAutoLoginDoneRef = useRef(false);
    const [tokenAutoLoginState, setTokenAutoLoginState] = useState<"idle" | "loading" | "error">("idle");

    const COUNTRY_CODES = [
        { code: "+91", label: "🇮🇳 India",      flag: "🇮🇳", placeholder: "98765 43210" },
        { code: "+44", label: "🇬🇧 UK",          flag: "🇬🇧", placeholder: "7911 123456" },
        { code: "+1",  label: "🇺🇸 USA / Canada", flag: "🇺🇸", placeholder: "201 555 0123" },
    ];

    const selectedCountry = COUNTRY_CODES.find(c => c.code === countryCode) || COUNTRY_CODES[0];

    // Full E.164 phone number, e.g. +919908650357
    const fullPhone = countryCode + localPhone.replace(/[^0-9]/g, "");


    const JOIN_KEYWORD = "join cream-enter";
    const SANDBOX_NUMBER = "14155238886";
    const SANDBOX_NUMBER_DISPLAY = "+1 415 523 8886";

    const router = useRouter();
    const searchParams = useSearchParams();
    const returnTo = searchParams.get("returnTo") || "/upload";

    // Auto-login via magic link token (?token=xxx in URL)
    useEffect(() => {
        const token = searchParams.get("token");
        if (!token || tokenAutoLoginDoneRef.current) return;
        tokenAutoLoginDoneRef.current = true;
        setTokenAutoLoginState("loading");
        const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
        fetch(`${apiUrl}/ai/whatsapp/login/auto-login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ token }),
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    localStorage.setItem("userEmail", data.email || data.phoneNumber);
                    window.dispatchEvent(new Event("userLogin"));
                    router.push(returnTo);
                } else {
                    setTokenAutoLoginState("error");
                    setError(data.error || "Magic link has expired. Please log in again.");
                }
            })
            .catch(() => {
                setTokenAutoLoginState("error");
                setError("Could not complete auto-login. Please enter your number below.");
            });
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Cleanup polling on unmount
    useEffect(() => {
        return () => {
            if (pollIntervalRef.current) clearTimeout(pollIntervalRef.current);
        };
    }, []);


    const startPolling = (phoneOverride?: string) => {
        const phoneNumber = phoneOverride || fullPhoneRef.current;
        setIsPolling(true);
        setPollStatus("waiting");
        pollCountRef.current = 0;

        const poll = async () => {
            try {
                const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
                const res = await fetch(`${apiUrl}/ai/whatsapp/login/verify-status`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ phoneNumber }),
                });

                if (res.ok) {
                    const data = await res.json();
                    if (data.verified) {
                        setPollStatus("verified");
                        setIsPolling(false);
                        if (pollIntervalRef.current) clearTimeout(pollIntervalRef.current);
                        localStorage.setItem("userEmail", data.email || phoneNumber);
                        window.dispatchEvent(new Event("userLogin"));
                        setTimeout(() => router.push(returnTo), 900);
                        return;
                    }
                    if (data.expired) {
                        setPollStatus("expired");
                        setIsPolling(false);
                        if (pollIntervalRef.current) clearTimeout(pollIntervalRef.current);
                        return;
                    }
                }
            } catch (err) {
                console.error("Polling error:", err);
            }

            pollCountRef.current++;
            const nextInterval = Math.min(2000 * Math.pow(2, pollCountRef.current - 1), 30000);

            if (pollCountRef.current >= 8) {
                setPollStatus("timeout");
                setIsPolling(false);
                return;
            }

            pollIntervalRef.current = setTimeout(poll, nextInterval);
        };

        poll();
    };

    // Step 1 — Check consent, send OTP and (if needed) open WhatsApp automatically
    const handleSubmitPhone = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setIsLoading(true);

        // Store the full phone in a ref so async polling closures can access it
        fullPhoneRef.current = fullPhone;

        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

            // Check if this number has already consented
            const consentRes = await fetch(`${apiUrl}/ai/whatsapp/login/check-consent`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ phoneNumber: fullPhone }),
            });
            if (!consentRes.ok) throw new Error("Failed to check consent. Please try again.");
            const consentData = await consentRes.json();

            if (consentData.hasConsent) {
                // Already consented — send OTP directly
                const otpRes = await fetch(`${apiUrl}/ai/whatsapp/login/send-otp`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ phoneNumber: fullPhone }),
                });
                const otpData = await otpRes.json();
                if (!otpRes.ok) {
                    if (otpRes.status === 429) {
                        throw new Error("Too many attempts. Please wait 1 hour before trying again.");
                    }
                    throw new Error(otpData.message || "Failed to send OTP. Please try again.");
                }
                if (otpData.fallbackOtp) setFallbackOtp(otpData.fallbackOtp);
                setDeliveryFailed(otpData.success === false);
                setStep("otp");
                setTimeout(() => startPolling(fullPhone), 500);

            } else {
                // First-time user — open WhatsApp automatically with the pre-filled join message
                const waUrl = `https://wa.me/${SANDBOX_NUMBER}?text=${encodeURIComponent(JOIN_KEYWORD)}`;
                window.open(waUrl, "_blank");
                setWhatsappOpened(true);
                setStep("otp");

                // In background: confirm consent + send OTP + start polling
                setTimeout(() => {
                    confirmConsentAndPoll(apiUrl, fullPhone);
                }, 1500);
            }
        } catch (err: any) {
            setError(err.message || "An error occurred");
        } finally {
            setIsLoading(false);
        }
    };

    const confirmConsentAndPoll = async (apiUrl: string, phoneNumber: string) => {
        try {
            const res = await fetch(`${apiUrl}/ai/whatsapp/login/confirm-consent`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ phoneNumber }),
            });
            if (res.ok) {
                const data = await res.json();
                if (data.fallbackOtp) setFallbackOtp(data.fallbackOtp);
                setDeliveryFailed(data.success === false);
            }
        } catch {
            // Silently fail — user can still enter OTP manually
        }
        startPolling(phoneNumber);
    };

    // Step 2 — Verify OTP manually
    const handleVerifyOtp = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setIsLoading(true);
        const phoneNumber = fullPhoneRef.current || fullPhone;
        try {
            const enteredOtp = otp.join("");
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/ai/whatsapp/login/verify-otp`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ phoneNumber, otp: enteredOtp }),
            });
            if (!res.ok) throw new Error("Invalid OTP. Please check and try again.");
            const data = await res.json();
            localStorage.setItem("userEmail", data.email || phoneNumber);
            window.dispatchEvent(new Event("userLogin"));
            router.push(returnTo);
        } catch (err: any) {
            setError(err.message || "Invalid code");
        } finally {
            setIsLoading(false);
        }
    };

    const resetToPhone = () => {
        setStep("phone");
        setLocalPhone("");
        setOtp(["", "", "", "", "", ""]);
        setFallbackOtp(null);
        setDeliveryFailed(false);
        setWhatsappOpened(false);
        setPollStatus("waiting");
        setIsPolling(false);
        fullPhoneRef.current = "";
        if (pollIntervalRef.current) clearTimeout(pollIntervalRef.current);
    };

    const updateOtp = (index: number, value: string) => {
        if (!/^[0-9]?$/.test(value)) return;
        const newOtp = [...otp];
        newOtp[index] = value;
        setOtp(newOtp);
        if (value && index < 5) {
            document.getElementById(`otp-${index + 1}`)?.focus();
        }
    };

    const handleOtpKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Backspace" && !otp[index] && index > 0) {
            document.getElementById(`otp-${index - 1}`)?.focus();
        }
    };

    const Spinner = () => <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />;

    // Show the OTP entry form when:
    // - Not auto-verified
    // - AND (not waiting for first-time WhatsApp join message, or timed out, or code already delivered via fallback)
    const waitingForJoin = whatsappOpened && isPolling && !fallbackOtp;
    const showOtpForm = step === "otp" && pollStatus !== "verified" && !waitingForJoin;

    return (
        <div className="min-h-screen flex items-center justify-center px-4 py-12">
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-indigo-600/10 blur-[120px] rounded-full pointer-events-none" />

            {/* Magic link loading overlay */}
            {tokenAutoLoginState === "loading" && (
                <motion.div
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    className="w-full max-w-md relative z-10 text-center"
                >
                    <div className="glass-panel p-10 flex flex-col items-center gap-6">
                        <div className="w-16 h-16 rounded-full bg-emerald-500/15 flex items-center justify-center border border-emerald-500/20">
                            <span className="w-8 h-8 border-2 border-emerald-400/30 border-t-emerald-400 rounded-full animate-spin block" />
                        </div>
                        <div>
                            <h2 className="text-xl font-bold text-white mb-2">Logging you in…</h2>
                            <p className="text-slate-400 text-sm">Verifying your WhatsApp magic link</p>
                        </div>
                    </div>
                </motion.div>
            )}

            {tokenAutoLoginState !== "loading" && (

            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ duration: 0.5 }}
                className="w-full max-w-md relative z-10"
            >
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-black text-white">
                        <span className="text-blue-400">Bid</span>MyHobby
                    </h1>
                    <p className="text-slate-400 mt-2 text-sm">Sign in with WhatsApp to continue.</p>
                </div>

                <div className="glass-panel overflow-hidden relative">
                    <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-emerald-500 via-blue-500 to-indigo-500" />

                    <div className="p-8">
                        <AnimatePresence mode="wait">

                            {/* ─── STEP 1: Phone Number ─── */}
                            {step === "phone" && (
                                <motion.div key="phone"
                                    initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 20 }}
                                    transition={{ duration: 0.3 }}>
                                    <div className="flex justify-center mb-6">
                                        <div className="w-16 h-16 bg-emerald-500/15 rounded-2xl flex items-center justify-center border border-emerald-500/20">
                                            <Smartphone className="w-8 h-8 text-emerald-400" />
                                        </div>
                                    </div>
                                    <h2 className="text-xl font-bold text-white text-center mb-2">Sign in with WhatsApp</h2>
                                    <p className="text-slate-400 text-sm text-center mb-6">Enter your number and we&apos;ll send a one-time code.</p>
                                    <form onSubmit={handleSubmitPhone} className="space-y-4">
                                        <div>
                                            <label className="block text-xs font-medium text-slate-400 mb-2 uppercase tracking-wider">
                                                WhatsApp Number
                                            </label>
                                            <div className="flex gap-2">
                                                {/* Country code selector */}
                                                <div className="relative">
                                                    <select
                                                        value={countryCode}
                                                        onChange={e => setCountryCode(e.target.value)}
                                                        className="appearance-none bg-white/5 border border-white/10 rounded-xl text-white py-3.5 pl-3 pr-8 focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 outline-none transition-all text-sm font-semibold cursor-pointer h-full"
                                                        style={{ minWidth: "110px" }}
                                                    >
                                                        {COUNTRY_CODES.map(c => (
                                                            <option key={c.code} value={c.code} className="bg-slate-900 text-white">
                                                                {c.flag} {c.code}
                                                            </option>
                                                        ))}
                                                    </select>
                                                    <div className="pointer-events-none absolute inset-y-0 right-2 flex items-center">
                                                        <svg className="w-3.5 h-3.5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                                            <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                                                        </svg>
                                                    </div>
                                                </div>
                                                {/* Local number */}
                                                <input
                                                    type="tel"
                                                    value={localPhone}
                                                    onChange={e => setLocalPhone(e.target.value.replace(/[^0-9 ]/g, ""))}
                                                    placeholder={selectedCountry.placeholder}
                                                    required
                                                    className="flex-1 bg-white/5 border border-white/10 rounded-xl text-white py-3.5 px-4 focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 outline-none transition-all placeholder-slate-600 text-lg"
                                                />
                                            </div>
                                            <p className="text-slate-600 text-xs mt-1.5">
                                                {selectedCountry.label} · Enter digits only, no spaces or dashes needed
                                            </p>
                                        </div>
                                        {error && <p className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">{error}</p>}
                                        <button type="submit" disabled={isLoading || localPhone.replace(/[^0-9]/g, "").length < 7}
                                            className="w-full py-3.5 px-6 rounded-xl font-bold text-white bg-gradient-to-r from-emerald-600 to-blue-600 hover:from-emerald-500 hover:to-blue-500 shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2">
                                            {isLoading ? <Spinner /> : <>Get WhatsApp Code <ArrowRight className="w-4 h-4" /></>}
                                        </button>
                                    </form>
                                    {/* How it works */}
                                    <div className="mt-6 space-y-2">
                                        <p className="text-xs text-slate-500 text-center uppercase tracking-wider font-semibold mb-3">How it works</p>
                                        {[
                                            { icon: "📱", text: "Enter your WhatsApp number above" },
                                            { icon: "💬", text: "First time? WhatsApp opens automatically" },
                                            { icon: "🔐", text: "You receive a 6-digit code on WhatsApp" },
                                        ].map((s, i) => (
                                            <div key={i} className="flex items-center gap-3 text-xs text-slate-400 bg-white/[0.03] rounded-lg px-3 py-2">
                                                <span className="text-base">{s.icon}</span>
                                                <span>{s.text}</span>
                                            </div>
                                        ))}
                                    </div>
                                </motion.div>
                            )}

                            {/* ─── STEP 2: OTP / Waiting ─── */}
                            {step === "otp" && (
                                <motion.div key="otp"
                                    initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }}
                                    transition={{ duration: 0.3 }}>

                                    {/* Icon */}
                                    <div className="flex justify-center mb-6">
                                        <div className={`w-16 h-16 rounded-2xl flex items-center justify-center border ${pollStatus === "verified" ? "bg-emerald-500/15 border-emerald-500/20" : whatsappOpened ? "bg-emerald-500/15 border-emerald-500/20" : "bg-blue-500/15 border-blue-500/20"}`}>
                                            {pollStatus === "verified"
                                                ? <CheckCircle2 className="w-8 h-8 text-emerald-400" />
                                                : whatsappOpened
                                                    ? <MessageCircle className="w-8 h-8 text-emerald-400" />
                                                    : <ShieldCheck className="w-8 h-8 text-blue-400" />
                                            }
                                        </div>
                                    </div>

                                    {/* ✅ Verified state */}
                                    {pollStatus === "verified" && (
                                        <div className="text-center mb-6">
                                            <h2 className="text-xl font-bold text-emerald-400 mb-1">✅ Verified!</h2>
                                            <p className="text-slate-400 text-sm">Logging you in…</p>
                                        </div>
                                    )}

                                    {/* First-time user: WhatsApp opened */}
                                    {pollStatus !== "verified" && whatsappOpened && (
                                        <>
                                            <div className="text-center mb-5">
                                                <h2 className="text-xl font-bold text-white mb-1">WhatsApp opened!</h2>
                                                <p className="text-slate-400 text-sm">
                                                    Send the message to <span className="text-white font-semibold">{SANDBOX_NUMBER_DISPLAY}</span>.
                                                    <br />We&apos;ll log you in automatically once received.
                                                </p>
                                            </div>
                                            {isPolling && (
                                                <div className="bg-emerald-500/10 border border-emerald-500/25 rounded-xl p-4 mb-5">
                                                    <div className="flex items-center gap-2 mb-2">
                                                        <span className="w-2 h-2 bg-emerald-400 rounded-full animate-pulse" />
                                                        <p className="text-emerald-300 text-sm font-medium">Waiting for your message…</p>
                                                    </div>
                                                    <p className="text-slate-400 text-xs mb-3">
                                                        Once you tap Send in WhatsApp, your login code will arrive and you&apos;ll be signed in automatically.
                                                    </p>
                                                    <a
                                                        href={`https://wa.me/${SANDBOX_NUMBER}?text=${encodeURIComponent(JOIN_KEYWORD)}`}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="w-full flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg font-semibold text-white text-sm bg-[#25D366] hover:bg-[#20bd5a] transition-colors"
                                                    >
                                                        <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                                                            <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z" />
                                                        </svg>
                                                        Re-open WhatsApp
                                                    </a>
                                                </div>
                                            )}
                                        </>
                                    )}

                                    {/* Returning user: auto-polling status */}
                                    {pollStatus !== "verified" && !whatsappOpened && isPolling && (
                                        <>
                                            <div className="text-center mb-5">
                                                <h2 className="text-xl font-bold text-white mb-1">Check your WhatsApp</h2>
                                                <p className="text-slate-400 text-sm">
                                                    We sent a code to <span className="text-white font-semibold font-mono">{maskPhone(fullPhoneRef.current || fullPhone)}</span>
                                                </p>
                                            </div>
                                            <div className="bg-blue-500/10 border border-blue-500/30 rounded-xl p-3 mb-4 text-center">
                                                <div className="flex items-center justify-center gap-2 mb-1">
                                                    <span className="w-2 h-2 bg-blue-400 rounded-full animate-pulse" />
                                                    <p className="text-blue-300 text-sm font-medium">Waiting for WhatsApp delivery…</p>
                                                </div>
                                                <p className="text-xs text-slate-500">We&apos;ll sign you in automatically once your code arrives.</p>
                                                <button
                                                    onClick={() => {
                                                        setIsPolling(false);
                                                        if (pollIntervalRef.current) clearTimeout(pollIntervalRef.current);
                                                        setPollStatus("timeout");
                                                    }}
                                                    className="text-xs text-blue-400 hover:text-blue-300 mt-2 underline"
                                                >
                                                    Enter code manually instead
                                                </button>
                                            </div>
                                        </>
                                    )}

                                    {/* Timeout — show heading + manual entry */}
                                    {pollStatus === "timeout" && !whatsappOpened && (
                                        <div className="text-center mb-4">
                                            <h2 className="text-xl font-bold text-white mb-1">Check your WhatsApp</h2>
                                            <p className="text-slate-400 text-sm">
                                                Code sent to <span className="text-white font-semibold font-mono">{maskPhone(fullPhoneRef.current || fullPhone)}</span>
                                            </p>
                                        </div>
                                    )}

                                    {/* Timeout / Expired notices */}
                                    {pollStatus === "timeout" && (
                                        <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3 mb-4 text-center">
                                            <p className="text-amber-400 text-sm font-medium">⏱️ Enter the 6-digit code from WhatsApp below.</p>
                                        </div>
                                    )}
                                    {pollStatus === "expired" && (
                                        <div className="bg-red-500/10 border border-red-500/30 rounded-xl p-3 mb-4 text-center">
                                            <p className="text-red-400 text-sm font-medium">
                                                ⏰ Code expired.{" "}
                                                <button onClick={resetToPhone} className="underline">Try again</button>
                                            </p>
                                        </div>
                                    )}

                                    {/* Fallback OTP */}
                                    {fallbackOtp && (
                                        <motion.div initial={{ opacity: 0, y: -8 }} animate={{ opacity: 1, y: 0 }}
                                            className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3 mb-4 text-center">
                                            <p className="text-amber-400 text-xs font-medium mb-1">
                                                {deliveryFailed ? "📵 WhatsApp delivery failed — use this code:" : "💡 Backup code:"}
                                            </p>
                                            <p className="text-amber-300 font-mono text-2xl font-black tracking-widest">{fallbackOtp}</p>
                                        </motion.div>
                                    )}

                                    {/* OTP Form — show when not auto-verified and manual entry is appropriate */}
                                    {showOtpForm && (
                                        <form onSubmit={handleVerifyOtp} className="space-y-5">
                                            <div>
                                                <p className="text-xs text-slate-500 text-center mb-3">Enter the 6-digit code from WhatsApp</p>
                                                <div className="flex gap-2 justify-center">
                                                    {otp.map((digit, index) => (
                                                        <input key={index} id={`otp-${index}`} type="text" inputMode="numeric"
                                                            maxLength={1} value={digit}
                                                            onChange={e => updateOtp(index, e.target.value)}
                                                            onKeyDown={e => handleOtpKeyDown(index, e)}
                                                            className="w-11 aspect-square text-center text-xl font-bold bg-white/5 border border-white/10 rounded-xl text-white focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 outline-none transition-all py-3" />
                                                    ))}
                                                </div>
                                            </div>
                                            {error && <p className="text-red-400 text-sm bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2 text-center">{error}</p>}
                                            <button type="submit" disabled={isLoading || otp.join("").length < 6}
                                                className="w-full py-3.5 px-6 rounded-xl font-bold text-white bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2">
                                                {isLoading ? <Spinner /> : "Verify & Log In"}
                                            </button>
                                        </form>
                                    )}

                                    {/* Back button */}
                                    {pollStatus !== "verified" && (
                                        <button onClick={resetToPhone}
                                            className="w-full mt-4 py-2 text-sm text-slate-500 hover:text-slate-300 transition-colors">
                                            ← Use a different number
                                        </button>
                                    )}
                                </motion.div>
                            )}

                        </AnimatePresence>
                    </div>
                </div>

                <p className="text-center text-slate-600 text-xs mt-6">
                    By continuing you agree to our{" "}
                    <Link href="/" className="text-slate-400 hover:text-white transition-colors">Terms of Service</Link>
                </p>
            </motion.div>
            )}
        </div>
    );
}

export default function Login() {
    return (
        <Suspense fallback={
            <div className="min-h-screen flex items-center justify-center">
                <span className="w-8 h-8 border-2 border-blue-500/30 border-t-blue-500 rounded-full animate-spin" />
            </div>
        }>
            <LoginForm />
        </Suspense>
    );
}
