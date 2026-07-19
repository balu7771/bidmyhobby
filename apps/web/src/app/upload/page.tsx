"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Check, ChevronRight, ImagePlus, Loader2, Pencil, RotateCcw, Send, ShieldCheck, Trash2 } from "lucide-react";

type Conversation = {
  conversationId: string;
  state: string;
  messages: { role: string; text: string }[];
  draft: Record<string, any>;
  missingFields: string[];
  images: { imageId: string; filename: string }[];
  allowedActions: string[];
  publishedItemId?: string;
};
type Action = "starting" | "analyzing" | "sending" | "saving" | "publishing" | "deleting" | null;

const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

export default function UploadPage() {
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);
  const [owner, setOwner] = useState("");
  const [chat, setChat] = useState<Conversation | null>(null);
  const [text, setText] = useState("");
  const [draft, setDraft] = useState<Record<string, any>>({});
  const [editing, setEditing] = useState(false);
  const [action, setAction] = useState<Action>("starting");
  const [error, setError] = useState("");

  const busy = action !== null;

  async function call(url: string, init?: RequestInit) {
    const response = await fetch(url, init);
    const body = await response.text();
    if (!response.ok) {
      try { throw new Error(JSON.parse(body).error || `Request failed (${response.status})`); }
      catch (error) {
        if (error instanceof SyntaxError) throw new Error(body || `Request failed (${response.status})`);
        throw error;
      }
    }
    return response.status === 204 ? null : JSON.parse(body);
  }

  async function start(user: string) {
    setAction("starting"); setError("");
    try {
      const data = await call(`${API}/catalog/listing-conversations`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ownerId: user })
      });
      localStorage.setItem("listingConversationId", data.conversationId);
      setChat(data);
    } catch (e: any) { setError(e.message); } finally { setAction(null); }
  }

  useEffect(() => {
    const user = localStorage.getItem("userEmail");
    if (!user) { router.push("/login?returnTo=/upload"); return; }
    setOwner(user);
    const id = localStorage.getItem("listingConversationId");
    if (!id) { start(user); return; }
    call(`${API}/catalog/listing-conversations/${id}?ownerId=${encodeURIComponent(user)}`)
      .then(setChat).catch(() => start(user)).finally(() => setAction(null));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);

  useEffect(() => { if (chat) setDraft(chat.draft || {}); }, [chat]);

  async function newListing() {
    localStorage.removeItem("listingConversationId");
    setChat(null); setEditing(false); setText("");
    await start(owner);
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!chat || !text.trim() || busy) return;
    const message = text; setText(""); setAction("sending"); setError("");
    try {
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}/messages?ownerId=${encodeURIComponent(owner)}`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ message })
      }));
    } catch (e: any) { setError(e.message); setText(message); } finally { setAction(null); }
  }

  async function addImages(files: FileList | null) {
    if (!chat || !files?.length || busy) return;
    const form = new FormData();
    Array.from(files).forEach(file => form.append("files", file));
    setAction("analyzing"); setError("");
    try {
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}/images?ownerId=${encodeURIComponent(owner)}`, { method: "POST", body: form }));
    } catch (e: any) { setError(e.message); } finally {
      setAction(null); if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function removeImage(imageId: string) {
    if (!chat || busy) return;
    setAction("deleting"); setError("");
    try {
      await call(`${API}/catalog/listing-conversations/${chat.conversationId}/images/${imageId}?ownerId=${encodeURIComponent(owner)}`, { method: "DELETE" });
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}?ownerId=${encodeURIComponent(owner)}`));
    } catch (e: any) { setError(e.message); } finally { setAction(null); }
  }

  async function save(confirmSuggestedPrice = false) {
    if (!chat || busy) return;
    setAction("saving"); setError("");
    const body = confirmSuggestedPrice ? { confirmSuggestedPrice: true } : {
      title: draft.title, description: draft.description, city: draft.city, size: draft.size,
      basePrice: Number(draft.basePrice), currency: draft.currency || "INR"
    };
    try {
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}/draft?ownerId=${encodeURIComponent(owner)}`, {
        method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
      }));
      setEditing(false);
    } catch (e: any) { setError(e.message); } finally { setAction(null); }
  }

  async function publish() {
    if (!chat || busy || !chat.allowedActions.includes("PUBLISH")) return;
    setAction("publishing"); setError("");
    try {
      const result = await call(`${API}/catalog/listing-conversations/${chat.conversationId}/publish?ownerId=${encodeURIComponent(owner)}`, { method: "POST" });
      setChat(result);
      localStorage.removeItem("listingConversationId");
    } catch (e: any) { setError(e.message); } finally { setAction(null); }
  }

  const hasPhotos = !!chat?.images.length;
  const hasDraft = !!chat?.draft?.title;
  const ready = !!chat?.allowedActions.includes("PUBLISH");
  const published = !!chat && ["PUBLISHED", "REVIEW_REQUIRED"].includes(chat.state);
  const statusText: Record<string, string> = {
    starting: "Preparing your listing…", analyzing: "Analyzing photos with AI…",
    sending: "Updating your draft…", saving: "Saving changes…",
    publishing: "Processing images and publishing…", deleting: "Removing photo…"
  };

  if (published) return (
    <main className="min-h-[calc(100vh-64px)] px-4 py-12 text-white">
      <div className="glass-panel mx-auto max-w-xl p-7 text-center sm:p-10">
        <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-500/15 text-emerald-300"><Check className="h-8 w-8" /></div>
        <h1 className="text-2xl font-bold sm:text-3xl">{chat.state === "PUBLISHED" ? "Your listing is live" : "Submitted for review"}</h1>
        <p className="mx-auto mt-3 max-w-md text-slate-300">{chat.state === "PUBLISHED" ? "Buyers can now discover your hobby item." : "We’ll make it visible after a quick review."}</p>
        <div className="mt-7 grid gap-3 sm:grid-cols-2">
          <button onClick={() => router.push("/")} className="rounded-xl bg-emerald-600 px-5 py-3 font-semibold">View marketplace</button>
          <button onClick={newListing} className="rounded-xl border border-white/15 px-5 py-3 font-semibold">List another item</button>
        </div>
      </div>
    </main>
  );

  return (
    <main className="min-h-screen px-3 py-5 text-white sm:px-5 sm:py-8">
      <div className="mx-auto mb-5 max-w-6xl">
        <div className="flex items-start justify-between gap-4">
          <div><h1 className="text-2xl font-bold sm:text-3xl">Sell a hobby item</h1><p className="mt-1 text-sm text-slate-400">Add photos and answer a few questions. You can review everything before it goes live.</p></div>
          {chat && <button disabled={busy} onClick={newListing} className="flex shrink-0 items-center gap-2 rounded-lg border border-white/10 px-3 py-2 text-xs text-slate-300 disabled:opacity-40"><RotateCcw className="h-3.5 w-3.5" /> Start over</button>}
        </div>
        <div className="mt-5 grid grid-cols-3 gap-2">
          <Step done={hasPhotos} active={!hasPhotos} number="1" label="Photos" />
          <Step done={hasDraft && ready} active={hasPhotos && !ready} number="2" label="Details" />
          <Step done={false} active={ready} number="3" label="Review & publish" />
        </div>
      </div>

      <div className="mx-auto grid max-w-6xl gap-5 lg:grid-cols-[1.08fr_.92fr]">
        <section className="glass-panel order-2 flex min-h-[460px] flex-col overflow-hidden lg:order-1 lg:min-h-[650px]">
          <div className="flex-1 space-y-3 overflow-y-auto p-4 sm:p-5">
            {(chat?.messages || []).map((message, index) => (
              <div key={index} className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}>
                <div className={`max-w-[88%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${message.role === "user" ? "bg-blue-600" : "border border-white/10 bg-white/5 text-slate-200"}`}>{message.text}</div>
              </div>
            ))}
            {action && <div className="flex items-center gap-2 rounded-xl bg-blue-500/10 p-3 text-sm text-blue-200"><Loader2 className="h-4 w-4 animate-spin" />{statusText[action]}</div>}
            {error && <div role="alert" className="rounded-xl border border-red-500/40 bg-red-500/10 p-4 text-sm text-red-100"><p className="font-semibold">We couldn’t complete that step.</p><p className="mt-1 text-red-200">{error}</p><p className="mt-2 text-xs">Your draft is safe. Check the service configuration and try again.</p></div>}
          </div>

          <footer className="border-t border-white/10 p-4">
            <div className="mb-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
              {chat?.images.map(image => <div key={image.imageId} className="flex min-w-0 items-center gap-2 rounded-xl bg-white/5 px-3 py-2 text-xs"><span className="min-w-0 flex-1 truncate">{image.filename}</span><button aria-label={`Remove ${image.filename}`} disabled={busy} onClick={() => removeImage(image.imageId)}><Trash2 className="h-4 w-4 text-red-300" /></button></div>)}
              {(chat?.images.length || 0) < 4 && <button disabled={busy || !chat} onClick={() => fileRef.current?.click()} className="flex min-h-10 items-center justify-center gap-2 rounded-xl border border-dashed border-white/20 px-3 py-2 text-xs disabled:opacity-40"><ImagePlus className="h-4 w-4" /> Add photos</button>}
              <input ref={fileRef} type="file" multiple accept="image/*" className="hidden" onChange={event => addImages(event.target.files)} />
            </div>
            <form onSubmit={send} className="flex gap-2">
              <input value={text} onChange={event => setText(event.target.value)} disabled={busy || !chat} placeholder={hasPhotos ? "Add condition, history, city or price…" : "Add photos first, then describe your item…"} className="min-w-0 flex-1 rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm outline-none focus:border-blue-400 disabled:opacity-50" />
              <button aria-label="Send" disabled={busy || !text.trim() || !chat} className="rounded-xl bg-blue-600 px-4 disabled:opacity-40"><Send className="h-5 w-5" /></button>
            </form>
          </footer>
        </section>

        <aside className="glass-panel order-1 h-fit p-4 sm:p-5 lg:order-2 lg:sticky lg:top-6">
          <div className="mb-4 flex items-center justify-between"><h2 className="font-bold">Listing preview</h2><span className="rounded-full bg-white/5 px-3 py-1 text-xs">{friendlyState(chat?.state)}</span></div>
          {!hasDraft ? <button disabled={busy || !chat} onClick={() => fileRef.current?.click()} className="flex w-full flex-col items-center rounded-2xl border border-dashed border-white/15 px-5 py-10 text-center disabled:opacity-50"><ImagePlus className="mb-3 h-8 w-8 text-blue-300" /><span className="font-semibold">Add your first photo</span><span className="mt-1 text-sm text-slate-400">AI will create a draft you can edit.</span></button> :
          <div className="space-y-4">
            {editing ? <>
              <Field label="Title" value={draft.title || ""} onChange={value => setDraft({ ...draft, title: value })} />
              <label className="block text-xs text-slate-400">Description<textarea rows={5} value={draft.description || ""} onChange={event => setDraft({ ...draft, description: event.target.value })} className="mt-1 w-full rounded-lg border border-white/10 bg-black/20 p-3 text-sm text-white" /></label>
              <div className="grid gap-3 sm:grid-cols-2"><Field label="City" value={draft.city || ""} onChange={value => setDraft({ ...draft, city: value })} /><Field label="Size (optional)" value={draft.size || ""} onChange={value => setDraft({ ...draft, size: value })} /></div>
              <Field label="Your asking price (INR)" type="number" value={draft.basePrice ?? ""} onChange={value => setDraft({ ...draft, basePrice: value })} />
              {draft.suggestedPrice > 0 && <p className="rounded-lg bg-emerald-500/10 px-3 py-2 text-xs text-emerald-200">Estimated market value: ₹{Math.round(draft.suggestedPrice).toLocaleString("en-IN")}. This is only a guide—choose the starting price you want.</p>}
              <div className="grid grid-cols-2 gap-2"><button disabled={busy} onClick={() => setEditing(false)} className="rounded-xl border border-white/15 py-2.5 text-sm">Cancel</button><button disabled={busy} onClick={() => save()} className="rounded-xl bg-blue-600 py-2.5 text-sm font-semibold">Save changes</button></div>
            </> : <>
              <div><h3 className="text-xl font-bold">{chat?.draft.title}</h3><p className="mt-2 text-sm leading-relaxed text-slate-300">{chat?.draft.description}</p></div>
              <div className="grid grid-cols-2 gap-2 text-sm"><Detail label="Estimated market value" value={chat?.draft.suggestedPrice != null ? `₹${Math.round(chat.draft.suggestedPrice).toLocaleString("en-IN")}` : "—"} /><Detail label="Your starting price" value={chat?.draft.basePrice != null ? `₹${Number(chat.draft.basePrice).toLocaleString("en-IN")}` : "Choose a price"} /><Detail label="Category" value={chat?.draft.category} /><Detail label="City" value={chat?.draft.city} /></div>
              {chat?.draft.suggestedPrice > 0 && <p className="text-xs leading-relaxed text-slate-400">The market estimate is a guide. Your starting price is your choice and can be higher or lower.</p>}
              {chat?.draft.suggestedPrice > 0 && chat.missingFields.includes("askingPrice") && <div className="space-y-2 rounded-xl border border-emerald-500/25 bg-emerald-500/5 p-3"><p className="text-sm text-slate-200">What price would you like buyers to start from?</p><p className="text-xs text-slate-400">Type it in the chat, for example “My asking price is 1500”, or accept the AI estimate.</p><button disabled={busy} onClick={() => save(true)} className="w-full rounded-lg bg-emerald-600 py-2.5 text-sm font-semibold text-white">Accept ₹{Math.round(chat.draft.suggestedPrice).toLocaleString("en-IN")} as my price</button></div>}
              <button disabled={busy} onClick={() => setEditing(true)} className="flex w-full items-center justify-center gap-2 rounded-xl border border-white/15 py-2.5 text-sm"><Pencil className="h-4 w-4" /> Edit draft</button>
              {!!chat?.missingFields.length && <div className="rounded-xl bg-amber-500/10 p-3 text-xs text-amber-200">Before publishing, add: {chat.missingFields.map(friendlyMissingField).join(", ")}.</div>}
              <button onClick={publish} disabled={!ready || busy} className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3.5 font-bold shadow-lg shadow-emerald-950/30 disabled:cursor-not-allowed disabled:opacity-40">{action === "publishing" ? <Loader2 className="h-5 w-5 animate-spin" /> : <ShieldCheck className="h-5 w-5" />}{action === "publishing" ? "Publishing…" : "Publish hobby item"}{ready && !busy && <ChevronRight className="h-5 w-5" />}</button>
              {!ready && <p className="text-center text-xs text-slate-500">Complete the highlighted details to enable publishing.</p>}
            </>}
          </div>}
        </aside>
      </div>
    </main>
  );
}

function Step({ done, active, number, label }: { done: boolean; active: boolean; number: string; label: string }) {
  return <div className={`flex items-center gap-2 rounded-xl border px-3 py-2.5 text-xs sm:text-sm ${active ? "border-blue-500/50 bg-blue-500/10 text-blue-200" : done ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300" : "border-white/10 text-slate-500"}`}><span className="flex h-6 w-6 items-center justify-center rounded-full bg-white/10 text-xs">{done ? <Check className="h-3.5 w-3.5" /> : number}</span><span className="font-medium">{label}</span></div>;
}
function Field({ label, value, onChange, type = "text" }: { label: string; value: any; onChange: (value: string) => void; type?: string }) {
  return <label className="block text-xs text-slate-400">{label}<input type={type} value={value} onChange={event => onChange(event.target.value)} className="mt-1 w-full rounded-lg border border-white/10 bg-black/20 p-3 text-sm text-white" /></label>;
}
function Detail({ label, value }: { label: string; value: any }) {
  return <div className="rounded-xl bg-white/5 p-3"><div className="text-xs text-slate-500">{label}</div><div className="mt-1 truncate">{value || "—"}</div></div>;
}
function friendlyState(state?: string) {
  const labels: Record<string, string> = { COLLECTING: "Add details", ANALYZING: "Analyzing", NEEDS_INFORMATION: "Needs details", READY_FOR_REVIEW: "Ready to publish", PUBLISHING: "Publishing" };
  return state ? labels[state] || state.replaceAll("_", " ").toLowerCase() : "Starting";
}
function friendlyMissingField(field: string) {
  const labels: Record<string, string> = {
    title: "a title", description: "a description", city: "your city",
    askingPrice: "the price you want"
  };
  return labels[field] || field;
}
