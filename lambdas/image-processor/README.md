# Image processor Lambda

This Python 3.12 Lambda performs deterministic image processing for the catalog
service:

- Textract OCR
- pixel-level redaction of lines containing phone numbers or email addresses
- masked contact metadata
- watermarking and JPEG normalization

Hobby classification, safety moderation, descriptions, categories, and price
suggestions are handled by the catalog service through OpenAI. The Lambda does
not call an LLM and fails closed if OCR/redaction cannot complete.

Build the deployment ZIP with:

```bash
./build.sh
```

The generated `package/` directory and ZIP are ignored. Configure the Lambda
handler as `lambda_function.lambda_handler`, then set
`AWS_LAMBDA_IMAGE_PROCESSOR` in the application environment to the deployed
function name.
