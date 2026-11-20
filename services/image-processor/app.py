import base64
import json
import logging
import os
import re
from io import BytesIO
from flask import Flask, request, jsonify
from PIL import Image, ImageDraw, ImageFont, ImageOps

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("image-processor")

app = Flask(__name__)

PHONE_RE = re.compile(r"(\+?[1-9]\d{6,14}|\(?\d{3}\)?[.\-\s]?\d{3}[.\-\s]?\d{4})")
EMAIL_RE = re.compile(r"[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}")
WEBSITE_RE = re.compile(r"(https?://)?([a-zA-Z0-9.\-]+\.[a-zA-Z]{2,})")
INSTAGRAM_RE = re.compile(r"@([a-zA-Z0-9._]+)|instagram\.com/([a-zA-Z0-9._]+)")

# Google Cloud Vision client (initialized lazily)
_vision_client = None
_vision_init_attempted = False

def get_vision_client():
    global _vision_client, _vision_init_attempted
    if not _vision_init_attempted:
        _vision_init_attempted = True
        try:
            from google.cloud import vision
            _vision_client = vision.ImageAnnotatorClient()
            logger.info("✅ Google Cloud Vision client initialized successfully with ADC")
        except Exception as e:
            logger.warn(f"⚠️ Google Cloud Vision client could not be initialized: {e}")
            _vision_client = None
    return _vision_client


# ── masking helpers ────────────────────────────────────────────────────────────

def mask_identity(identity: str) -> str:
    """Mask a phone number or email address for public display."""
    if not identity:
        return identity
    digits = re.sub(r"\D", "", identity)
    if len(digits) >= 7 and (identity.startswith("+") or identity.lstrip().isdigit() or digits == identity.replace(" ", "").replace("-", "")):
        return "*" * (len(digits) - 4) + digits[-4:]
    at = identity.find("@")
    if at > 2:
        username = identity[:at]
        masked = username[:2] + "*" * max(0, len(username) - 3) + username[-1]
        return masked + identity[at:]
    return identity


def mask_email(email: str) -> str:
    return mask_identity(email)


def mask_phone(phone: str) -> str:
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
    font = None
    font_paths = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
        "C:\\Windows\\Fonts\\arial.ttf"
    ]
    for fp in font_paths:
        if os.path.exists(fp):
            try:
                font = ImageFont.truetype(fp, font_size)
                break
            except Exception:
                pass
    if font is None:
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
    out_img.save(out, format="JPEG", quality=90, optimize=True)
    return out.getvalue()


# ── contact redaction via Google Cloud Vision ──────────────────────────────────

def redact_contact_details(image_bytes: bytes) -> tuple[bytes, dict]:
    client = get_vision_client()
    image = ImageOps.exif_transpose(Image.open(BytesIO(image_bytes))).convert("RGB")
    draw = ImageDraw.Draw(image)

    phones = []
    emails = []
    redactions = 0

    if client:
        try:
            from google.cloud import vision
            gcv_image = vision.Image(content=image_bytes)
            response = client.document_text_detection(image=gcv_image)

            if response.full_text_annotation:
                for page in response.full_text_annotation.pages:
                    for block in page.blocks:
                        for paragraph in block.paragraphs:
                            para_text = "".join(
                                "".join(symbol.text for symbol in word.symbols) + (" " if word.symbols and word.symbols[-1].property.detected_break else "")
                                for word in paragraph.words
                            )
                            found_phones = PHONE_RE.findall(para_text)
                            found_emails = EMAIL_RE.findall(para_text)
                            if found_phones or found_emails:
                                verts = paragraph.bounding_box.vertices
                                xs = [v.x for v in verts if v.x is not None]
                                ys = [v.y for v in verts if v.y is not None]
                                if xs and ys:
                                    left = max(0, min(xs) - 6)
                                    top = max(0, min(ys) - 4)
                                    right = min(image.width, max(xs) + 6)
                                    bottom = min(image.height, max(ys) + 4)
                                    draw.rectangle((left, top, right, bottom), fill=(20, 20, 20))
                                    redactions += 1
                                    phones.extend(mask_phone(p if isinstance(p, str) else p[0]) for p in found_phones)
                                    emails.extend(mask_email(e) for e in found_emails)

        except Exception as e:
            logger.warn(f"⚠️ Google Cloud Vision OCR detection skipped: {e}")

    output = BytesIO()
    image.save(output, format="JPEG", quality=90, optimize=True)
    return output.getvalue(), {
        "redactionCount": redactions,
        "maskedPhones": list(dict.fromkeys(phones)),
        "maskedEmails": list(dict.fromkeys(emails)),
    }


def analyze_business_card(image_bytes: bytes) -> dict:
    client = get_vision_client()
    if not client:
        return {}

    try:
        from google.cloud import vision
        gcv_image = vision.Image(content=image_bytes)
        response = client.text_detection(image=gcv_image)
        text = response.text_annotations[0].description if response.text_annotations else ""

        phone_m = PHONE_RE.search(text)
        email_m = EMAIL_RE.search(text)
        web_m = WEBSITE_RE.search(text)
        insta_m = INSTAGRAM_RE.search(text)

        phone = phone_m.group() if phone_m else None
        info = {
            "phone": mask_phone(phone) if phone else None,
            "email": email_m.group() if email_m else None,
            "website": web_m.group() if web_m else None,
            "instagram": (insta_m.group(1) or insta_m.group(2)) if insta_m else None,
            "rawText": text,
        }
        if info["instagram"]:
            info["latestReel"] = f"https://instagram.com/reel/{info['instagram']}/latest"
        return info
    except Exception as e:
        logger.warn(f"⚠️ Business card analysis skipped: {e}")
        return {"error": str(e)}


# ── HTTP API ───────────────────────────────────────────────────────────────────

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "UP", "service": "bidmyhobby-image-processor"}), 200


@app.route("/process", methods=["POST"])
def process():
    try:
        payload = request.get_json(force=True)
        if not payload or "imageData" not in payload:
            return jsonify({"error": "Missing imageData field"}), 400

        image_bytes = base64.b64decode(payload["imageData"])
        email = payload.get("email", "")
        watermark_text = payload.get("watermarkText", "")
        is_business_card = payload.get("isBusinessCard", False)

        masked_email = mask_email(email) if email else ""
        body = {}

        # 1. Redact contacts
        redacted_bytes, redaction_info = redact_contact_details(image_bytes)
        body["redaction"] = redaction_info

        # 2. Business card parsing if requested
        if is_business_card:
            business_info = analyze_business_card(image_bytes)
            body["businessInfo"] = business_info
            if not watermark_text and business_info.get("phone"):
                watermark_text = f"BidMyHobby | {business_info['phone']}"

        # 3. Watermarking
        if not watermark_text:
            watermark_text = f"BidMyHobby | {masked_email}" if masked_email else "© BidMyHobby"

        watermarked_bytes = add_watermark(redacted_bytes, watermark_text)
        body["processedImage"] = base64.b64encode(watermarked_bytes).decode("utf-8")
        body["maskedEmail"] = masked_email
        body["watermarkText"] = watermark_text

        return jsonify(body), 200

    except Exception as e:
        logger.error(f"Image processing error: {e}", exc_info=True)
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)
