"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, CheckCircle2, Mail, ShieldCheck } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";

const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo") || "/upload";
  const [step, setStep] = useState<"email" | "code">("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = window.setInterval(() => setSeconds(value => Math.max(0, value - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [seconds]);

  async function requestCode(event?: FormEvent) {
    event?.preventDefault();
    setBusy(true); setError("");
    try {
      const response = await fetch(`${API}/auth/otp/send`, {
        credentials: "include",
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email.trim().toLowerCase() })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "We could not send a login code right now.");
      setEmail(value => value.trim().toLowerCase());
      setStep("code"); setSeconds(data.resendAfterSeconds || 60);
    } catch (e: any) { setError(e.message); } finally { setBusy(false); }
  }

  async function verify(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError("");
    try {
      const response = await fetch(`${API}/auth/otp/verify`, {
        credentials: "include",
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, otp: code })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "Invalid or expired login code.");
      localStorage.setItem("userEmail", data.email);
      localStorage.setItem("userRole", data.role);
      window.dispatchEvent(new Event("userLogin"));
      router.push(returnTo);
    } catch (e: any) { setError(e.message); } finally { setBusy(false); }
  }

  function changeEmail() {
    setStep("email"); setCode(""); setError(""); setSeconds(0);
  }

  return (
    <main className="flex min-h-[calc(100vh-64px)] items-center justify-center px-4 py-10 text-white">
      <section className="glass-panel w-full max-w-md p-6 sm:p-8">
        <div className="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-blue-500/15 text-blue-300">
          {step === "email" ? <Mail className="h-7 w-7" /> : <ShieldCheck className="h-7 w-7" />}
        </div>
        <h1 className="text-center text-2xl font-bold">{step === "email" ? "Sign in to BidMyHobby" : "Check your email"}</h1>
        <p className="mt-2 text-center text-sm text-slate-400">
          {step === "email" ? "We’ll email you a secure one-time login code." : <>Enter the six-digit code sent to <strong className="text-slate-200">{email}</strong>.</>}
        </p>
        {step === "code" && (
          <p className="mt-3 rounded-lg bg-amber-500/10 px-3 py-2 text-center text-xs text-amber-200">
            Can’t find it? Check your spam or junk folder.
          </p>
        )}

        {error && <div role="alert" className="mt-5 rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">{error}</div>}

        {step === "email" ? (
          <form onSubmit={requestCode} className="mt-6 space-y-4">
            <label className="block text-sm text-slate-300">Email address
              <input type="email" autoComplete="email" required autoFocus value={email}
                onChange={event => setEmail(event.target.value)}
                placeholder="you@example.com"
                className="mt-2 w-full rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-white outline-none focus:border-blue-400" />
            </label>
            <button disabled={busy} className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-600 py-3 font-semibold disabled:opacity-50">
              {busy ? "Sending…" : <>Email me a code <ArrowRight className="h-4 w-4" /></>}
            </button>
          </form>
        ) : (
          <form onSubmit={verify} className="mt-6 space-y-4">
            <input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required autoFocus
              aria-label="Six-digit login code" value={code} onChange={event => setCode(event.target.value.replace(/\D/g, ""))}
              placeholder="000000"
              className="w-full rounded-xl border border-white/10 bg-black/20 px-4 py-4 text-center font-mono text-3xl tracking-[0.35em] outline-none focus:border-blue-400" />
            <p className="text-center text-xs text-slate-500">The code expires in 10 minutes and can only be used once.</p>
            <button disabled={busy || code.length !== 6} className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 font-semibold disabled:opacity-50">
              {busy ? "Verifying…" : <>Sign in <CheckCircle2 className="h-4 w-4" /></>}
            </button>
            <div className="flex items-center justify-between text-sm">
              <button type="button" onClick={changeEmail} className="flex items-center gap-1 text-slate-400 hover:text-white"><ArrowLeft className="h-4 w-4" /> Change email</button>
              <button type="button" disabled={busy || seconds > 0} onClick={() => requestCode()} className="text-blue-300 disabled:text-slate-600">
                {seconds > 0 ? `Resend in ${seconds}s` : "Resend code"}
              </button>
            </div>
          </form>
        )}
        <p className="mt-6 text-center text-xs leading-relaxed text-slate-500">By continuing, you agree to receive security emails required to access your account.</p>
      </section>
    </main>
  );
}

export default function LoginPage() {
  return <Suspense fallback={<main className="min-h-screen" />}><LoginForm /></Suspense>;
}
