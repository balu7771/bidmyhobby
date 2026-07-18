# Image processor Lambda

This Python 3.12 Lambda performs image watermarking and business-card text
extraction for the catalog service.

Build the deployment ZIP with:

```bash
./build.sh
```

The generated `package/` directory and ZIP are ignored. Configure the Lambda
handler as `lambda_function.lambda_handler`, then set
`AWS_LAMBDA_IMAGE_PROCESSOR` in the application environment to the deployed
function name.
