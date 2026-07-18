import base64
import json
import re
import boto3
from io import BytesIO
from PIL import Image, ImageDraw, ImageFont, ImageOps

# AWS clients — reused across warm invocations
textract = boto3.client("textract", region_name="us-east-1")

PHONE_RE = re.compile(r"(\+?[1-9]\d{6,14}|\(?\d{3}\)?[.\-\s]?\d{3}[.\-\s]?\d{4})")
EMAIL_RE = re.compile(r"[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}")
WEBSITE_RE = re.compile(r"(https?://)?([a-zA-Z0-9.\-]+\.[a-zA-Z]{2,})")
INSTAGRAM_RE = re.compile(r"@([a-zA-Z0-9._]+)|instagram\.com/([a-zA-Z0-9._]+)")


# ── masking ────────────────────────────────────────────────────────────────────

def mask_identity(identity: str) -> str:
    """Mask a phone number or email address for public display."""
    if not identity:
        return identity
    # Phone number: keep only last 4 digits
    digits = re.sub(r"\D", "", identity)
    if len(digits) >= 7 and (identity.startswith("+") or identity.lstrip().isdigit() or digits == identity.replace(" ", "").replace("-", "")):
        return "*" * (len(digits) - 4) + digits[-4:]
    # Email address
    at = identity.find("@")
    if at > 2:
        username = identity[:at]
        masked = username[:2] + "*" * max(0, len(username) - 3) + username[-1]
        return masked + identity[at:]
    return identity


def mask_email(email: str) -> str:
    """Kept for backward compatibility — delegates to mask_identity."""
    return mask_identity(email)


def mask_phone(phone: str) -> str:
    """Show only last 4 digits: ******1234"""
    digits = re.sub(r"\D", "", phone)
    if len(digits) < 4:
        return phone
    return "*" * (len(digits) - 4) + digits[-4:]


# ── watermark ──────────────────────────────────────────────────────────────────

def add_watermark(image_bytes: bytes, text: str) -> bytes:
    img = ImageOps.exif_transpose(Image.open(BytesIO(image_bytes))).convert("RGBA")
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    font_size = max(img.width // 20, 14)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", font_size)
    except Exception:
        font = ImageFont.load_default()

    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    padding = 10
    x = img.width - tw - padding
    y = padding

    draw.text((x + 2, y + 2), text, font=font, fill=(0, 0, 0, 120))   # shadow
    draw.text((x, y), text, font=font, fill=(255, 255, 255, 180))      # text

    out_img = Image.alpha_composite(img, overlay).convert("RGB")
    out = BytesIO()
    out_img.save(out, format="JPEG")
    return out.getvalue()


def redact_contact_details(image_bytes: bytes) -> tuple[bytes, dict]:
    """Redact OCR lines containing phone numbers or email addresses.

    Textract returns normalized bounding boxes. Redacting the entire matching line
    is intentionally conservative and avoids exposing adjacent fragments of a
    formatted phone number.
    """
    response = textract.detect_document_text(Document={"Bytes": image_bytes})
    image = ImageOps.exif_transpose(Image.open(BytesIO(image_bytes))).convert("RGB")
    draw = ImageDraw.Draw(image)
    phones = []
    emails = []
    redactions = 0

    for block in response.get("Blocks", []):
        if block.get("BlockType") != "LINE":
            continue
        text = block.get("Text", "")
        found_phones = PHONE_RE.findall(text)
        found_emails = EMAIL_RE.findall(text)
        if not found_phones and not found_emails:
            continue
        box = block.get("Geometry", {}).get("BoundingBox", {})
        left = max(0, int(box.get("Left", 0) * image.width) - 6)
        top = max(0, int(box.get("Top", 0) * image.height) - 4)
        right = min(image.width, int((box.get("Left", 0) + box.get("Width", 0)) * image.width) + 6)
        bottom = min(image.height, int((box.get("Top", 0) + box.get("Height", 0)) * image.height) + 4)
        draw.rectangle((left, top, right, bottom), fill=(20, 20, 20))
        redactions += 1
        phones.extend(mask_phone(p if isinstance(p, str) else p[0]) for p in found_phones)
        emails.extend(mask_email(e) for e in found_emails)

    output = BytesIO()
    image.save(output, format="JPEG", quality=90, optimize=True)
    return output.getvalue(), {
        "redactionCount": redactions,
        "maskedPhones": list(dict.fromkeys(phones)),
        "maskedEmails": list(dict.fromkeys(emails)),
    }


# ── extraction helpers ─────────────────────────────────────────────────────────

def _first(pattern, text):
    m = pattern.search(text)
    return m.group() if m else None


def _first_instagram(text):
    m = INSTAGRAM_RE.search(text)
    if m:
        return m.group(1) or m.group(2)
    return None


# ── Textract business card analysis ───────────────────────────────────────────

def analyze_business_card(image_bytes: bytes) -> dict:
    try:
        resp = textract.analyze_document(
            Document={"Bytes": image_bytes},
            FeatureTypes=["TABLES", "FORMS"],
        )
        text = "\n".join(b["Text"] for b in resp["Blocks"] if b["BlockType"] == "LINE")

        phone = _first(PHONE_RE, text)
        info = {
            "phone": mask_phone(phone) if phone else None,  # always store masked phone
            "email": _first(EMAIL_RE, text),
            "website": _first(WEBSITE_RE, text),
            "instagram": _first_instagram(text),
            "rawText": text,
        }

        if info["instagram"]:
            info["latestReel"] = f"https://instagram.com/reel/{info['instagram']}/latest"

        return info
    except Exception as e:
        return {"error": f"Failed to analyze business card: {e}"}


# ── handler ────────────────────────────────────────────────────────────────────

def lambda_handler(event, context):
    try:
        image_bytes = base64.b64decode(event["imageData"])
        email = event.get("email", "")
        watermark_text = event.get("watermarkText", "")
        is_business_card = event.get("isBusinessCard", False)

        masked_email = mask_email(email) if email else ""
        body = {}

        # Redact contact information on every hobby image as well as business cards.
        # If OCR fails, fail the invocation so catalog never publishes an image that
        # may contain unredacted personal information.
        redacted_bytes, redaction_info = redact_contact_details(image_bytes)
        body["redaction"] = redaction_info

        if is_business_card:
            business_info = analyze_business_card(image_bytes)
            body["businessInfo"] = business_info

            # Use masked phone as watermark when available (last 4 digits only)
            if not watermark_text and business_info.get("phone"):
                watermark_text = f"BidMyHobby | {business_info['phone']}"

        if not watermark_text:
            watermark_text = f"BidMyHobby | {masked_email}" if masked_email else "© BidMyHobby"

        body["processedImage"] = base64.b64encode(add_watermark(redacted_bytes, watermark_text)).decode()
        body["maskedEmail"] = masked_email
        body["watermarkText"] = watermark_text

        return {"statusCode": 200, "body": json.dumps(body)}

    except Exception as e:
        print(f"Error: {e}")
        return {"statusCode": 500, "body": json.dumps({"error": str(e)})}
