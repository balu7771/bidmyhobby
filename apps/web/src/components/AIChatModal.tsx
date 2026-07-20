"use client";

import { motion, AnimatePresence } from "framer-motion";
import { X, Send, User } from "lucide-react";
import { useState, useEffect, useRef } from "react";

interface ChatMessage {
    messageText: string;
    authorId: string;
    messageTime: string;
}

interface AIChatModalProps {
    profile: any;
    onClose: () => void;
}

const MAX_USER_MESSAGES = 10;

function chatUserId() {
    const signedInUser = localStorage.getItem("userEmail");
    if (signedInUser) return signedInUser;
    const existingGuest = localStorage.getItem("aiChatGuestId");
    if (existingGuest) return existingGuest;
    const guestId = `Guest-${crypto.randomUUID()}`;
    localStorage.setItem("aiChatGuestId", guestId);
    return guestId;
}

export function AIChatModal({ profile, onClose }: AIChatModalProps) {
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [newMessage, setNewMessage] = useState("");
    const [isTyping, setIsTyping] = useState(false);
    const [conversationId, setConversationId] = useState<string | null>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const [userEmail, setUserEmail] = useState<string>("Guest");
    const [error, setError] = useState("");
    const userMessageCount = messages.filter(message =>
        message.authorId !== profile.id && message.authorId !== "AI"
    ).length;
    const limitReached = userMessageCount >= MAX_USER_MESSAGES;

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages, isTyping]);

    useEffect(() => {
        const actorId = chatUserId();
        setUserEmail(actorId);
        startConversation(actorId);
    }, [profile]);

    const startConversation = async (actorId: string) => {
        setIsTyping(true);
        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/ai/conversations`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    userId: actorId,
                    profileId: profile.id,
                    initialMessage: `Hi ${profile.firstName}! I'd love to chat about ${profile.mainHobby || "hobbies"}.`
                }),
            });
            if (res.ok) {
                const data = await res.json();
                setConversationId(data.id);
                setMessages(data.messages || []);
            }
        } catch (err) {
            console.error("Failed to start conversation:", err);
        } finally {
            setIsTyping(false);
        }
    };

    const handleSendMessage = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newMessage.trim() || !conversationId || isTyping || limitReached) return;

        const userMsg = newMessage.trim();
        setNewMessage("");
        setError("");
        
        // Optimistically add user message
        const tempUserMsg: ChatMessage = {
            messageText: userMsg,
            authorId: userEmail,
            messageTime: new Date().toISOString()
        };
        setMessages(prev => [...prev, tempUserMsg]);
        setIsTyping(true);

        try {
            const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
            const res = await fetch(`${apiUrl}/ai/conversations/${conversationId}/messages`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    messageText: userMsg,
                    authorId: userEmail
                }),
            });
            if (res.ok) {
                const data = await res.json();
                setMessages(data.messages || []);
            } else {
                setMessages(prev => prev.slice(0, -1));
                setError(res.status === 429
                    ? "You’ve reached the 10-message limit for this conversation."
                    : "We couldn’t send that message. Please try again.");
            }
        } catch (err) {
            console.error("Failed to send message:", err);
            setMessages(prev => prev.slice(0, -1));
            setError("We couldn’t send that message. Please try again.");
        } finally {
            setIsTyping(false);
        }
    };

    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[100] flex items-center justify-center p-4 md:p-6 bg-black/80 backdrop-blur-sm"
        >
            <motion.div
                initial={{ scale: 0.9, y: 20 }}
                animate={{ scale: 1, y: 0 }}
                exit={{ scale: 0.9, y: 20 }}
                className="w-full max-w-2xl h-[80vh] md:h-[600px] glass-panel flex flex-col overflow-hidden shadow-2xl border-white/10"
            >
                {/* Header */}
                <div className="p-4 border-b border-white/10 flex items-center justify-between bg-white/5">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full overflow-hidden border border-indigo-500/50">
                            {profile.imageUrl ? (
                                <img 
                                    src={`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api"}/ai/images/${profile.imageUrl}`} 
                                    alt={profile.name} 
                                    className="w-full h-full object-cover"
                                />
                            ) : (
                                <div className="w-full h-full bg-indigo-500/20 flex items-center justify-center">
                                    <User className="w-5 h-5 text-indigo-400" />
                                </div>
                            )}
                        </div>
                        <div>
                            <h3 className="font-bold text-white flex items-center gap-2">
                                {profile.firstName} {profile.lastName}
                                <span className="px-1.5 py-0.5 bg-indigo-500/20 text-indigo-400 text-[10px] rounded uppercase font-black tracking-tighter">AI</span>
                            </h3>
                            <p className="text-[10px] text-slate-400 uppercase tracking-widest">{profile.mainHobby || "Hobbyist"}</p>
                        </div>
                    </div>
                    <button 
                        onClick={onClose}
                        className="p-2 hover:bg-white/10 rounded-full text-slate-400 hover:text-white transition-colors"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Messages */}
                <div className="flex-1 overflow-y-auto p-4 space-y-4 scroll-smooth">
                    {messages.map((msg, idx) => {
                        const isAI = msg.authorId === profile.id || msg.authorId === "AI";
                        return (
                            <motion.div
                                key={idx}
                                initial={{ opacity: 0, x: isAI ? -10 : 10 }}
                                animate={{ opacity: 1, x: 0 }}
                                className={`flex ${isAI ? "justify-start" : "justify-end"}`}
                            >
                                <div className={`max-w-[80%] p-3 rounded-2xl text-sm ${
                                    isAI 
                                    ? "bg-white/10 text-slate-200 rounded-tl-none border border-white/5 shadow-inner" 
                                    : "bg-indigo-600 text-white rounded-tr-none shadow-lg shadow-indigo-900/20"
                                }`}>
                                    {msg.messageText}
                                    <div className={`text-[10px] mt-1 opacity-50 ${isAI ? "text-left" : "text-right"}`}>
                                        {new Date(msg.messageTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                    </div>
                                </div>
                            </motion.div>
                        );
                    })}
                    
                    {isTyping && (
                        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex justify-start">
                            <div className="bg-white/10 p-3 rounded-2xl rounded-tl-none border border-white/5">
                                <div className="flex gap-1">
                                    <motion.div animate={{ opacity: [0.4, 1, 0.4] }} transition={{ repeat: Infinity, duration: 1, delay: 0 }} className="w-1.5 h-1.5 bg-indigo-400 rounded-full" />
                                    <motion.div animate={{ opacity: [0.4, 1, 0.4] }} transition={{ repeat: Infinity, duration: 1, delay: 0.2 }} className="w-1.5 h-1.5 bg-indigo-400 rounded-full" />
                                    <motion.div animate={{ opacity: [0.4, 1, 0.4] }} transition={{ repeat: Infinity, duration: 1, delay: 0.4 }} className="w-1.5 h-1.5 bg-indigo-400 rounded-full" />
                                </div>
                            </div>
                        </motion.div>
                    )}
                    <div ref={messagesEndRef} />
                </div>

                {/* Input */}
                <form onSubmit={handleSendMessage} className="p-4 bg-white/5 border-t border-white/10">
                    <div className="mb-2 flex items-center justify-between gap-3 text-xs">
                        <span className={limitReached ? "font-semibold text-amber-300" : "text-slate-500"}>
                            {limitReached ? "Conversation limit reached" : `${userMessageCount} of ${MAX_USER_MESSAGES} messages used`}
                        </span>
                        {error && <span role="alert" className="text-right text-red-300">{error}</span>}
                    </div>
                    <div className="relative">
                        <input
                            type="text"
                            value={newMessage}
                            onChange={(e) => setNewMessage(e.target.value)}
                            placeholder={limitReached ? "10-message limit reached" : `Message ${profile.firstName}...`}
                            className="w-full bg-background/50 border border-white/10 rounded-xl py-3 pl-4 pr-12 text-sm text-white focus:ring-2 focus:ring-indigo-500/50 outline-none transition-all"
                            disabled={isTyping || limitReached}
                        />
                        <button
                            type="submit"
                            disabled={!newMessage.trim() || isTyping || limitReached}
                            className="absolute right-2 top-1/2 -translate-y-1/2 p-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg transition-all disabled:opacity-50 disabled:grayscale"
                        >
                            <Send className="w-4 h-4" />
                        </button>
                    </div>
                </form>
            </motion.div>
        </motion.div>
    );
}
