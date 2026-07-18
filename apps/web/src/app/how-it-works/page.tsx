import { Construction, ArrowLeft, Rocket, Wrench, Calendar } from "lucide-react";
import Link from "next/link";
import { motion } from "framer-motion";

export const metadata = {
    title: "How It Works",
    description: "Learn how the BidMyHobby platform works. We are actively building new features every weekend.",
};

export default function HowItWorks() {
    return (
        <div className="flex flex-col min-h-screen pt-24 pb-20 px-4">
            <div className="max-w-4xl mx-auto w-full relative">
                {/* Decorative Background Elements */}
                <div className="absolute top-0 right-1/4 w-[400px] h-[400px] bg-blue-600/20 blur-[100px] rounded-full -z-10 pointer-events-none" />
                <div className="absolute bottom-0 left-1/4 w-[300px] h-[300px] bg-indigo-600/20 blur-[100px] rounded-full -z-10 pointer-events-none" />

                <Link 
                    href="/"
                    className="inline-flex items-center gap-2 text-slate-400 hover:text-white mb-8 transition-colors"
                >
                    <ArrowLeft className="w-4 h-4" />
                    Back to Home
                </Link>

                <div className="text-center mb-12">
                    <div className="inline-flex items-center justify-center w-20 h-20 rounded-full bg-blue-600/20 border border-blue-500/20 mb-6 text-blue-400">
                        <Construction className="w-10 h-10 animate-pulse" />
                    </div>
                    <h1 className="text-4xl md:text-5xl font-black text-white mb-6 tracking-tight">
                        How It Works
                    </h1>
                    <p className="text-xl text-slate-400 max-w-2xl mx-auto leading-relaxed">
                        We're currently laying the foundation for the ultimate hobbyist marketplace. 
                        Here is a sneak peek at how the platform will operate once fully launched.
                    </p>
                </div>

                <div className="grid md:grid-cols-3 gap-6 mb-16">
                    <div className="glass-panel p-8 rounded-2xl flex flex-col items-center text-center">
                        <div className="w-14 h-14 rounded-full bg-blue-500/20 flex items-center justify-center text-blue-400 mb-4">
                            <span className="text-xl font-bold">1</span>
                        </div>
                        <h3 className="text-xl font-bold text-white mb-3">Upload Items</h3>
                        <p className="text-slate-400 text-sm">
                            Take a few photos of your hobby items, collectibles, or gear. Our AI will automatically analyze the images and draft a rich description.
                        </p>
                    </div>
                    <div className="glass-panel p-8 rounded-2xl flex flex-col items-center text-center">
                        <div className="w-14 h-14 rounded-full bg-emerald-500/20 flex items-center justify-center text-emerald-400 mb-4">
                            <span className="text-xl font-bold">2</span>
                        </div>
                        <h3 className="text-xl font-bold text-white mb-3">AI Pricing</h3>
                        <p className="text-slate-400 text-sm">
                            Using advanced computer vision, we suggest a fair market value for your item so you don't have to guess what it's worth.
                        </p>
                    </div>
                    <div className="glass-panel p-8 rounded-2xl flex flex-col items-center text-center">
                        <div className="w-14 h-14 rounded-full bg-purple-500/20 flex items-center justify-center text-purple-400 mb-4">
                            <span className="text-xl font-bold">3</span>
                        </div>
                        <h3 className="text-xl font-bold text-white mb-3">Connect & Sell</h3>
                        <p className="text-slate-400 text-sm">
                            Connect with thousands of dedicated collectors through auctions and direct sales to find the perfect buyer.
                        </p>
                    </div>
                </div>

                {/* Developer Note */}
                <div className="glass-panel p-8 md:p-12 rounded-3xl bg-gradient-to-br from-blue-900/40 to-indigo-900/40 border border-blue-500/30 relative overflow-hidden">
                    <div className="absolute top-0 right-0 p-8 opacity-10">
                        <Wrench className="w-32 h-32" />
                    </div>
                    <div className="relative z-10">
                        <div className="flex items-center gap-3 mb-4">
                            <Rocket className="w-8 h-8 text-blue-400" />
                            <h2 className="text-3xl font-bold text-white">Product Roadmap</h2>
                        </div>
                        <p className="text-lg text-slate-300 leading-relaxed mb-6">
                            BidMyHobby is proudly being built as a passion project. New features, AI models, and optimizations are pushed live <strong className="text-white bg-blue-500/20 px-2 py-1 rounded">every weekend</strong>.
                        </p>
                        
                        <div className="space-y-4">
                            <div className="flex items-center gap-4 bg-black/40 p-4 rounded-xl border border-white/5">
                                <Calendar className="w-6 h-6 text-emerald-400" />
                                <div>
                                    <h4 className="text-white font-semibold">Coming Soon</h4>
                                    <p className="text-slate-400 text-sm">Real-time bidding engine and enhanced AI visual search.</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
}
