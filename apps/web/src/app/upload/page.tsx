"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ImagePlus, Loader2, Send, ShieldCheck, Sparkles, Trash2 } from "lucide-react";

type Conversation = {
  conversationId: string; state: string;
  messages: { role: string; text: string }[];
  draft: Record<string, any>; missingFields: string[];
  images: { imageId: string; filename: string }[];
  allowedActions: string[]; publishedItemId?: string;
};
const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";

export default function UploadPage() {
  const router = useRouter();
  const fileRef = useRef<HTMLInputElement>(null);
  const [owner, setOwner] = useState("");
  const [chat, setChat] = useState<Conversation | null>(null);
  const [text, setText] = useState("");
  const [draft, setDraft] = useState<Record<string, any>>({});
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function call(url: string, init?: RequestInit) {
    const response = await fetch(url, init);
    if (!response.ok) throw new Error(await response.text() || `Request failed (${response.status})`);
    return response.status === 204 ? null : response.json();
  }
  async function start(user: string) {
    setBusy(true);
    try {
      const data = await call(`${API}/catalog/listing-conversations`, {
        method: "POST", headers: {"Content-Type":"application/json"},
        body: JSON.stringify({ownerId:user})
      });
      localStorage.setItem("listingConversationId", data.conversationId);
      setChat(data);
    } catch (e:any) { setError(e.message); } finally { setBusy(false); }
  }
  useEffect(() => {
    const user = localStorage.getItem("userEmail");
    if (!user) { router.push("/login"); return; }
    setOwner(user);
    const id = localStorage.getItem("listingConversationId");
    if (!id) { start(user); return; }
    call(`${API}/catalog/listing-conversations/${id}?ownerId=${encodeURIComponent(user)}`)
      .then(setChat).catch(() => start(user));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [router]);
  useEffect(() => { if (chat) setDraft(chat.draft || {}); }, [chat]);

  async function send(e: FormEvent) {
    e.preventDefault(); if (!chat || !text.trim()) return;
    const message = text; setText(""); setBusy(true); setError("");
    try {
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}/messages?ownerId=${encodeURIComponent(owner)}`, {
        method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({message})
      }));
    } catch(e:any) { setError(e.message); setText(message); } finally { setBusy(false); }
  }
  async function addImages(files: FileList | null) {
    if (!chat || !files?.length) return;
    const form = new FormData(); Array.from(files).forEach(f => form.append("files", f));
    setBusy(true); setError("");
    try {
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}/images?ownerId=${encodeURIComponent(owner)}`, {method:"POST", body:form}));
    } catch(e:any) { setError(e.message); } finally {
      setBusy(false); if (fileRef.current) fileRef.current.value = "";
    }
  }
  async function removeImage(imageId:string) {
    if (!chat) return; setBusy(true);
    try {
      await call(`${API}/catalog/listing-conversations/${chat.conversationId}/images/${imageId}?ownerId=${encodeURIComponent(owner)}`, {method:"DELETE"});
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}?ownerId=${encodeURIComponent(owner)}`));
    } catch(e:any) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(confirmSuggestedPrice=false) {
    if (!chat) return; setBusy(true); setError("");
    const body = confirmSuggestedPrice ? {confirmSuggestedPrice:true} : {
      title:draft.title, description:draft.description, city:draft.city, size:draft.size,
      basePrice:Number(draft.basePrice), currency:draft.currency || "INR"
    };
    try {
      setChat(await call(`${API}/catalog/listing-conversations/${chat.conversationId}/draft?ownerId=${encodeURIComponent(owner)}`, {
        method:"PATCH", headers:{"Content-Type":"application/json"}, body:JSON.stringify(body)
      })); setEditing(false);
    } catch(e:any) { setError(e.message); } finally { setBusy(false); }
  }
  async function publish() {
    if (!chat) return; setBusy(true); setError("");
    try {
      const result = await call(`${API}/catalog/listing-conversations/${chat.conversationId}/publish?ownerId=${encodeURIComponent(owner)}`, {method:"POST"});
      setChat(result); localStorage.removeItem("listingConversationId");
      setTimeout(() => router.push("/"), 1800);
    } catch(e:any) { setError(e.message); } finally { setBusy(false); }
  }

  const terminal = chat && ["PUBLISHED","REVIEW_REQUIRED","REJECTED"].includes(chat.state);
  return <main className="min-h-screen px-4 py-8 text-white">
    <div className="mx-auto grid max-w-6xl gap-6 lg:grid-cols-[1.15fr_.85fr]">
      <section className="glass-panel flex min-h-[76vh] flex-col overflow-hidden">
        <header className="flex items-center gap-3 border-b border-white/10 p-5">
          <span className="rounded-xl bg-emerald-500/15 p-2 text-emerald-300"><Sparkles/></span>
          <div><h1 className="text-xl font-bold">Create a hobby listing</h1>
            <p className="text-sm text-slate-400">Add photos and describe the item naturally.</p></div>
        </header>
        <div className="flex-1 space-y-4 overflow-y-auto p-5">
          {(chat?.messages || []).map((m,i) => <div key={i} className={`flex ${m.role==="user"?"justify-end":"justify-start"}`}>
            <div className={`max-w-[85%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${m.role==="user"?"bg-blue-600":"border border-white/10 bg-white/5 text-slate-200"}`}>{m.text}</div>
          </div>)}
          {busy && <div className="flex items-center gap-2 text-sm text-slate-400"><Loader2 className="h-4 w-4 animate-spin"/>Working…</div>}
          {error && <div className="rounded-xl border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-200">{error}</div>}
        </div>
        {!terminal && <footer className="border-t border-white/10 p-4">
          <div className="mb-3 flex flex-wrap gap-2">
            {chat?.images.map(img => <span key={img.imageId} className="flex items-center gap-2 rounded-lg bg-white/5 px-3 py-2 text-xs">
              {img.filename}<button onClick={()=>removeImage(img.imageId)}><Trash2 className="h-3.5 w-3.5 text-red-300"/></button></span>)}
            {(chat?.images.length || 0)<4 && <button onClick={()=>fileRef.current?.click()} className="flex items-center gap-2 rounded-lg border border-dashed border-white/20 px-3 py-2 text-xs">
              <ImagePlus className="h-4 w-4"/>Add photos</button>}
            <input ref={fileRef} type="file" multiple accept="image/*" className="hidden" onChange={e=>addImages(e.target.files)}/>
          </div>
          <form onSubmit={send} className="flex gap-2">
            <input value={text} onChange={e=>setText(e.target.value)} disabled={busy} placeholder="Condition, history, price, city…"
              className="min-w-0 flex-1 rounded-xl border border-white/10 bg-black/20 px-4 py-3 text-sm outline-none focus:border-blue-400"/>
            <button disabled={busy||!text.trim()} className="rounded-xl bg-blue-600 px-4 disabled:opacity-40"><Send className="h-5 w-5"/></button>
          </form>
        </footer>}
      </section>

      <aside className="glass-panel h-fit p-5 lg:sticky lg:top-6">
        <div className="mb-4 flex items-center justify-between"><h2 className="font-bold">Listing preview</h2>
          <span className="rounded-full bg-white/5 px-3 py-1 text-xs">{chat?.state || "STARTING"}</span></div>
        {!chat?.draft?.title ? <p className="py-12 text-center text-sm text-slate-500">Your draft appears after you add a photo and details.</p> :
        <div className="space-y-4">{editing ? <>
          <Field label="Title" value={draft.title||""} onChange={v=>setDraft({...draft,title:v})}/>
          <label className="block text-xs text-slate-400">Description<textarea rows={5} value={draft.description||""} onChange={e=>setDraft({...draft,description:e.target.value})} className="mt-1 w-full rounded-lg border border-white/10 bg-black/20 p-3 text-sm text-white"/></label>
          <Field label="City" value={draft.city||""} onChange={v=>setDraft({...draft,city:v})}/>
          <Field label="Size (optional)" value={draft.size||""} onChange={v=>setDraft({...draft,size:v})}/>
          <Field label="Price (INR)" type="number" value={draft.basePrice??""} onChange={v=>setDraft({...draft,basePrice:v})}/>
          <button onClick={()=>save()} className="w-full rounded-xl bg-blue-600 py-2.5 font-semibold">Save changes</button>
        </> : <>
          <h3 className="text-xl font-bold">{chat.draft.title}</h3><p className="text-sm leading-relaxed text-slate-300">{chat.draft.description}</p>
          <div className="grid grid-cols-2 gap-3 text-sm"><Detail label="Category" value={chat.draft.category}/><Detail label="Condition" value={chat.draft.condition}/>
            <Detail label="City" value={chat.draft.city}/><Detail label="Price" value={chat.draft.basePrice!=null?`₹${chat.draft.basePrice}`:"Not confirmed"}/></div>
          {chat.draft.suggestedPrice>0 && chat.missingFields.includes("confirmedPrice") && <button onClick={()=>save(true)} className="w-full rounded-xl border border-emerald-500/40 bg-emerald-500/10 py-2.5 text-sm font-semibold text-emerald-300">Use suggested price ₹{Math.round(chat.draft.suggestedPrice)}</button>}
          <button onClick={()=>setEditing(true)} className="w-full rounded-xl border border-white/15 py-2.5 text-sm">Edit draft</button>
          <button onClick={publish} disabled={!chat.allowedActions.includes("PUBLISH")||busy} className="flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 py-3 font-bold disabled:opacity-40"><ShieldCheck className="h-5 w-5"/>Publish hobby item</button>
          {!!chat.missingFields.length && <p className="text-xs text-amber-300">Still needed: {chat.missingFields.join(", ")}</p>}
        </>}</div>}
      </aside>
    </div>
  </main>;
}
function Field({label,value,onChange,type="text"}:{label:string;value:any;onChange:(v:string)=>void;type?:string}) {
  return <label className="block text-xs text-slate-400">{label}<input type={type} value={value} onChange={e=>onChange(e.target.value)} className="mt-1 w-full rounded-lg border border-white/10 bg-black/20 p-3 text-sm text-white"/></label>;
}
function Detail({label,value}:{label:string;value:any}) {
  return <div className="rounded-xl bg-white/5 p-3"><div className="text-xs text-slate-500">{label}</div><div className="mt-1">{value||"—"}</div></div>;
}
