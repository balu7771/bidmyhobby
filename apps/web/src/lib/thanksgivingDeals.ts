export interface ThanksgivingDeal {
    id: string;
    name: string;
    tagline: string;
    category: "Laptops & Tech" | "TVs & Home Theater" | "Audio & Headphones" | "Kitchen & Cooking" | "Beauty & Grooming" | "Smart Home" | "Travel & Lifestyle" | "Smartphones";
    store: "Amazon" | "Best Buy" | "Target" | "Macy's" | "eBay";
    storeBadgeColor: string;
    image: string;
    usRegularPrice: number;
    usSalePrice: number;
    usSavings: number;
    discountPercent: number;
    indiaPriceInr: number;
    savingsVsIndiaInr: number;
    dealUrl: string;
    badge: string;
    keySpecs: string[];
    description: string;
}

export const THANKSGIVING_DEALS: ThanksgivingDeal[] = [
    {
        id: "deal-macbook-air-m2",
        name: "Apple MacBook Air 13.6\" (M2 Chip)",
        tagline: "Ultra-thin powerhouse with 18-hour battery life",
        category: "Laptops & Tech",
        store: "Best Buy",
        storeBadgeColor: "bg-blue-600/20 text-blue-300 border-blue-500/30",
        image: "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 999.00,
        usSalePrice: 699.00,
        usSavings: 300.00,
        discountPercent: 30,
        indiaPriceInr: 89900,
        savingsVsIndiaInr: 30835, // 89,900 - (699 * 84.5 = 59,065)
        dealUrl: "https://www.bestbuy.com/site/apple-macbook-air-13-6-laptop-m2-chip-8gb-memory-256gb-ssd-midnight/6509650.p",
        badge: "⭐ $300 Off Doorbuster",
        keySpecs: ["Apple M2 Chip (8-Core CPU)", "13.6\" Liquid Retina Display", "Up to 18 hrs battery", "MagSafe 3 Charging"],
        description: "The most popular laptop on the planet hits an all-time low price for Thanksgiving. Ultra-light, fanless silent operation, and stunning 500-nit Liquid Retina display."
    },
    {
        id: "deal-lg-c4-oled-55",
        name: "LG 55\" C4 Series 4K OLED Smart TV",
        tagline: "Reference-grade infinite contrast & 144Hz OLED gaming",
        category: "TVs & Home Theater",
        store: "Amazon",
        storeBadgeColor: "bg-amber-600/20 text-amber-300 border-amber-500/30",
        image: "https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 1999.99,
        usSalePrice: 1296.00,
        usSavings: 703.99,
        discountPercent: 35,
        indiaPriceInr: 139990,
        savingsVsIndiaInr: 30478, // 139,990 - (1296 * 84.5 = 109,512)
        dealUrl: "https://www.amazon.com/dp/B0CVR726RF",
        badge: "🔥 Biggest Dollar Savings ($704 Off)",
        keySpecs: ["Self-Lit OLED Pixels (Zero Blooming)", "α9 AI Processor Gen7", "144Hz VRR & G-Sync / FreeSync", "Dolby Vision & Atmos"],
        description: "The definitive 4K TV for movies and console gaming. LG's Brightness Booster OLED technology produces inky pitch blacks and dazzling highlights with a massive $704 price cut."
    },
    {
        id: "deal-sony-wh1000xm5",
        name: "Sony WH-1000XM5 Wireless ANC Headphones",
        tagline: "Flagship active noise canceling with 30-hour battery",
        category: "Audio & Headphones",
        store: "Best Buy",
        storeBadgeColor: "bg-blue-600/20 text-blue-300 border-blue-500/30",
        image: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 399.99,
        usSalePrice: 174.90,
        usSavings: 225.09,
        discountPercent: 56,
        indiaPriceInr: 29990,
        savingsVsIndiaInr: 15211, // 29,990 - (174.90 * 84.5 = 14,779)
        dealUrl: "https://www.bestbuy.com/site/sony-wh1000xm5-wireless-noise-canceling-over-the-ear-headphones-black/6505727.p",
        badge: "⚡ 56% Massive Markdown",
        keySpecs: ["Dual Processors & 8 Microphones", "Industry-Leading ANC", "30-Hr Battery + Quick Charge", "LDAC Hi-Res Audio"],
        description: "Sony's class-leading noise cancelers see a historic clearance price drop across Best Buy and Amazon. Shuts out plane cabin noise, street chaos, and office chatter effortlessly."
    },
    {
        id: "deal-kitchenaid-artisan-5qt",
        name: "KitchenAid Artisan Series 5-Qt. Tilt-Head Stand Mixer",
        tagline: "The timeless culinary workhorse built to last decades",
        category: "Kitchen & Cooking",
        store: "Macy's",
        storeBadgeColor: "bg-red-600/20 text-red-300 border-red-500/30",
        image: "https://images.unsplash.com/photo-1594385208974-2e75f8d7bb48?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 499.99,
        usSalePrice: 349.99,
        usSavings: 150.00,
        discountPercent: 30,
        indiaPriceInr: 49990,
        savingsVsIndiaInr: 20416, // 49,990 - (349.99 * 84.5 = 29,574)
        dealUrl: "https://www.macys.com/shop/product/kitchenaid-ksm150ps-artisan-5-qt.-stand-mixer",
        badge: "🏆 Holiday Baker's Choice",
        keySpecs: ["5-Quart Stainless Steel Bowl with Handle", "10 Optimized Speeds", "Planetary 59-Point Mixing Action", "Full Die-Cast Metal Construction"],
        description: "Macy's signature Thanksgiving doorbuster deal on the iconic KitchenAid 5-quart mixer. Effortlessly kneads artisan bread dough, whips meringue, and mixes double cookie batches."
    },
    {
        id: "deal-dyson-airwrap-multistyler",
        name: "Dyson Airwrap Multi-Styler Complete Long",
        tagline: "Style, curl, wave, and smooth using Coanda airflow",
        category: "Beauty & Grooming",
        store: "Macy's",
        storeBadgeColor: "bg-red-600/20 text-red-300 border-red-500/30",
        image: "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 599.99,
        usSalePrice: 449.99,
        usSavings: 150.00,
        discountPercent: 25,
        indiaPriceInr: 49900,
        savingsVsIndiaInr: 11876, // 49,900 - (449.99 * 84.5 = 38,024)
        dealUrl: "https://www.macys.com/shop/product/dyson-airwrap-multi-styler-complete-long",
        badge: "💖 Macy's Doorbuster Exclusive",
        keySpecs: ["Coanda Airflow Technology", "No Extreme Heat Damage", "Includes 6 Re-engineered Barrels & Brushes", "Prussian Blue / Copper Finish"],
        description: "The viral hair-care sensation discounted by $150. Styles hair from wet-to-dry without frying strands with extreme heat, creating voluminous curls and silky salon blowouts."
    },
    {
        id: "deal-apple-watch-ultra-2",
        name: "Apple Watch Ultra 2 (GPS + Cellular, 49mm Titanium)",
        tagline: "Rugged aerospace-grade titanium adventure smartwatch",
        category: "Laptops & Tech",
        store: "Amazon",
        storeBadgeColor: "bg-amber-600/20 text-amber-300 border-amber-500/30",
        image: "https://images.unsplash.com/photo-1508685096489-7aacd43bd3b1?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 799.00,
        usSalePrice: 599.00,
        usSavings: 200.00,
        discountPercent: 25,
        indiaPriceInr: 89900,
        savingsVsIndiaInr: 39285, // 89,900 - (599 * 84.5 = 50,615)
        dealUrl: "https://www.amazon.com/dp/B0CHX3MB1F",
        badge: "🔥 $200 Price Drop",
        keySpecs: ["Corrosion-Resistant Titanium Case", "Up to 3,000 Nits Brightness", "Precision Dual-Frequency GPS", "Up to 72 Hours Battery in Low Power"],
        description: "Save $200 on Apple's most durable and capable smartwatch. Engineered for endurance athletes, outdoor explorers, and water sports enthusiasts with 100m water resistance."
    },
    {
        id: "deal-shark-matrix-robot-mop",
        name: "Shark Matrix Plus 2-in-1 Robot Vacuum & Mop (RV2610WA)",
        tagline: "Self-emptying, matrix grid scrubbing, cleans for 60 days hands-free",
        category: "Smart Home",
        store: "Target",
        storeBadgeColor: "bg-rose-600/20 text-rose-300 border-rose-500/30",
        image: "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 699.99,
        usSalePrice: 299.99,
        usSavings: 400.00,
        discountPercent: 57,
        indiaPriceInr: 59990,
        savingsVsIndiaInr: 34641, // 59,990 - (299.99 * 84.5 = 25,349)
        dealUrl: "https://www.target.com/p/shark-matrix-plus-2-in-1-robot-vacuum-and-mop-with-xl-self-empty-base/-/A-89025287",
        badge: "⚡ 57% Off ($400 Savings!)",
        keySpecs: ["Bagless 60-Day Self-Empty Base", "Sonic Mopping 100x/min Scrubbing", "Matrix Clean Precision Grid", "LiDAR 360° Vision Navigation"],
        description: "Target's biggest home appliance deal of Thanksgiving. This 2-in-1 robot vacuums deep carpets and scrubs hard floors in a precise matrix grid, then empties itself automatically into a HEPA base."
    },
    {
        id: "deal-breville-barista-express",
        name: "Breville Barista Express Espresso Machine (BES870XL)",
        tagline: "Third wave specialty coffee at home from bean to espresso",
        category: "Kitchen & Cooking",
        store: "Macy's",
        storeBadgeColor: "bg-red-600/20 text-red-300 border-red-500/30",
        image: "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 699.95,
        usSalePrice: 499.95,
        usSavings: 200.00,
        discountPercent: 29,
        indiaPriceInr: 79900,
        savingsVsIndiaInr: 37654, // 79,900 - (499.95 * 84.5 = 42,246)
        dealUrl: "https://www.macys.com/shop/product/breville-bes870xl-barista-express-espresso-maker",
        badge: "☕ $200 Off Barista Favorite",
        keySpecs: ["Integrated Conical Burr Grinder", "Digital PID Temperature Control", "15 Bar Italian Pump", "Microfoam Milk Texturing Steam Wand"],
        description: "The gold standard all-in-one espresso machine for coffee enthusiasts. Grind whole beans directly into the portafilter and extract rich café-grade crema in under a minute."
    },
    {
        id: "deal-samsonite-spin-tech",
        name: "Samsonite Spin Tech 6 / Uptempo 2-Piece Hardside Spinner Set",
        tagline: "Unbreakable polycarbonate travel duo with TSA lock",
        category: "Travel & Lifestyle",
        store: "Macy's",
        storeBadgeColor: "bg-red-600/20 text-red-300 border-red-500/30",
        image: "https://images.unsplash.com/photo-1565026057447-bc90a3dceb87?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 619.99,
        usSalePrice: 189.99,
        usSavings: 430.00,
        discountPercent: 70,
        indiaPriceInr: 36990,
        savingsVsIndiaInr: 20936, // 36,990 - (189.99 * 84.5 = 16,054)
        dealUrl: "https://www.macys.com/shop/product/samsonite-spin-tech-6-2-pc.-luggage-set",
        badge: "💥 70% Off Doorbuster ($430 Saved)",
        keySpecs: ["Includes 20\" Carry-On & 25\" Checked Spinner", "100% Polycarbonate Scratch-Resistant Shell", "Built-In TSA Combination Lock", "Smooth 360° Dual Spinner Wheels"],
        description: "A jaw-dropping 70% off at Macy's. Get two durable, premium Samsonite hardside spinners for less than the usual cost of a single carry-on. Perfect for upcoming holiday travel."
    },
    {
        id: "deal-samsung-s24-ultra",
        name: "Samsung Galaxy S24 Ultra (512GB Factory Unlocked)",
        tagline: "Galaxy AI powerhouse with 200MP camera & built-in S-Pen",
        category: "Smartphones",
        store: "eBay",
        storeBadgeColor: "bg-emerald-600/20 text-emerald-300 border-emerald-500/30",
        image: "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=800&q=80",
        usRegularPrice: 1419.99,
        usSalePrice: 1099.99,
        usSavings: 320.00,
        discountPercent: 23,
        indiaPriceInr: 129999,
        savingsVsIndiaInr: 37050, // 129,999 - (1099.99 * 84.5 = 92,949)
        dealUrl: "https://www.ebay.com/itm/Samsung-Galaxy-S24-Ultra-512GB-Unlocked-Holiday-Sale",
        badge: "📱 $320 Off + Galaxy AI",
        keySpecs: ["Titanium Frame with Gorilla Armor Glass", "200MP Main + 5x Telephoto 100x Space Zoom", "Snapdragon 8 Gen 3 for Galaxy", "Built-in S-Pen Stylus"],
        description: "Grab Samsung's top flagship in the 512GB high-capacity tier with a massive $320 discount on eBay and Amazon. Enjoy live translation, Circle to Search, and pro-grade night photography."
    }
];

export const EXCHANGE_RATE_USD_TO_INR = 84.5;
