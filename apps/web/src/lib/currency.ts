/** Formats a price in the given currency.
 *  INR uses Indian lakh formatting (e.g. ₹1,25,000).
 *  GBP and USD use standard Western formatting. */
export function formatCurrency(amount: number | null | undefined, currency: string | null | undefined): string {
    if (amount == null || isNaN(amount)) return "—";
    const cur = (currency || "INR").toUpperCase();
    if (cur === "INR") {
        const formatted = amount.toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
        return `₹${formatted}`;
    }
    if (cur === "GBP") return `£${amount.toLocaleString("en-GB", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    if (cur === "EUR") return `€${amount.toLocaleString("de-DE", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    return `$${amount.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export const CURRENCY_SYMBOLS: Record<string, string> = { INR: "₹", GBP: "£", USD: "$", EUR: "€", AUD: "$", CAD: "$" };
