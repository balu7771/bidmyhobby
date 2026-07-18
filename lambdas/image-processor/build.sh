#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PACKAGE_DIR="$SCRIPT_DIR/package"
ZIP_FILE="$SCRIPT_DIR/bidmyhobby-image-processor.zip"

echo "==> Cleaning up previous build..."
rm -rf "$PACKAGE_DIR" "$ZIP_FILE"
mkdir -p "$PACKAGE_DIR"

echo "==> Installing dependencies..."
pip install -r "$SCRIPT_DIR/requirements.txt" \
    --platform manylinux2014_x86_64 \
    --target "$PACKAGE_DIR" \
    --implementation cp \
    --python-version 3.12 \
    --only-binary=:all: \
    --upgrade

echo "==> Copying handler..."
cp "$SCRIPT_DIR/lambda_function.py" "$PACKAGE_DIR/"

echo "==> Creating zip..."
cd "$PACKAGE_DIR"
zip -r "$ZIP_FILE" . -x "*.pyc" -x "*/__pycache__/*"

echo ""
echo "Done! Upload this file to AWS Lambda:"
echo "  $ZIP_FILE"
echo ""
echo "Lambda settings:"
echo "  Runtime : Python 3.12"
echo "  Handler : lambda_function.lambda_handler"
echo "  Timeout : 30s (recommended)"
echo "  Memory  : 512 MB (recommended)"
