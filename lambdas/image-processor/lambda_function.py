import base64
import json
import re
import boto3
from io import BytesIO
from PIL import Image, ImageDraw, ImageFont

# AWS clients — reused across warm invocations
textract = boto3.client("textract", region_name="us-east-1")
bedrock = boto3.client("bedrock-runtime", region_name="us-east-1")

PHONE_RE = re.compile(r"(\+?[1-9]\d{1,14}|\(?\d{3}\)?[.\-\s]?\d{3}[.\-\s]?\d{4})")
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
    img = Image.open(BytesIO(image_bytes)).convert("RGBA")
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


# ── Bedrock content moderation ─────────────────────────────────────────────────

def moderate_content(image_bytes: bytes) -> bool:
    try:
        payload = {
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image",
                            "source": {
                                "type": "base64",
                                "media_type": "image/jpeg",
                                "data": base64.b64encode(image_bytes).decode(),
                            },
                        },
                        {
                            "type": "text",
                            "text": (
                                "Analyze this image and determine if it is appropriate for a business listing platform. "
                                "Look for any inappropriate content, offensive material, or non-business related content. "
                                "Respond with only 'APPROVED' or 'REJECTED' followed by a brief reason."
                            ),
                        },
                    ],
                }
            ],
            "max_tokens": 100,
            "anthropic_version": "bedrock-2023-05-31",
        }

        resp = bedrock.invoke_model(
            modelId="amazon.nova-micro-v1:0",
            body=json.dumps(payload),
        )
        result = json.loads(resp["body"].read())
        text = result.get("content", [{}])[0].get("text", "").upper()
        return "APPROVED" in text
    except Exception:
        return True  # default approve on failure


# ── handler ────────────────────────────────────────────────────────────────────

def lambda_handler(event, context):
    try:
        image_bytes = base64.b64decode(event["imageData"])
        email = event.get("email", "")
        watermark_text = event.get("watermarkText", "")
        is_business_card = event.get("isBusinessCard", False)

        masked_email = mask_email(email) if email else ""
        body = {}

        if is_business_card:
            business_info = analyze_business_card(image_bytes)
            body["businessInfo"] = business_info

            if not moderate_content(image_bytes):
                body["contentApproved"] = False
                body["error"] = "Content not approved for listing"
                return {"statusCode": 400, "body": json.dumps(body)}

            body["contentApproved"] = True

            # Use masked phone as watermark when available (last 4 digits only)
            if not watermark_text and business_info.get("phone"):
                watermark_text = f"BidMyHobby | {business_info['phone']}"

        if not watermark_text:
            watermark_text = f"BidMyHobby | {masked_email}" if masked_email else "© BidMyHobby"

        body["processedImage"] = base64.b64encode(add_watermark(image_bytes, watermark_text)).decode()
        body["maskedEmail"] = masked_email
        body["watermarkText"] = watermark_text

        return {"statusCode": 200, "body": json.dumps(body)}

    except Exception as e:
        print(f"Error: {e}")
        return {"statusCode": 500, "body": json.dumps({"error": str(e)})}
