package com.bidmyhobby.catalog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class ImageProcessingService {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.lambda.function.image-processor:bidmyhobby-image-processor}")
    private String functionName;

    public ImageProcessingService(LambdaClient lambdaClient) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = new ObjectMapper();
    }

    /** Process a hobby item image (no business-card Textract analysis). */
    public byte[] processImage(byte[] imageData, String email) throws Exception {
        return processImage(imageData, email, false);
    }

    /**
     * Process any image via the Python Lambda.
     *
     * @param isBusinessCard when true the Lambda runs Textract to extract and mask
     *                       the phone number from the card and uses it as the watermark text.
     */
    public byte[] processImage(byte[] imageData, String email, boolean isBusinessCard) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("imageData", Base64.getEncoder().encodeToString(imageData));
        payload.put("email", email);
        payload.put("isBusinessCard", isBusinessCard);

        String payloadJson = objectMapper.writeValueAsString(payload);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payloadJson))
                .build();

        InvokeResponse response = lambdaClient.invoke(request);
        String responseJson = response.payload().asUtf8String();

        Map<String, Object> result = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> body = objectMapper.readValue((String) result.get("body"), Map.class);
        Number statusCode = (Number) result.get("statusCode");
        if (response.functionError() != null || statusCode == null || statusCode.intValue() != 200) {
            throw new IllegalStateException("Image redaction failed; image will not be published");
        }

        String processedImageBase64 = (String) body.get("processedImage");
        if (processedImageBase64 == null || processedImageBase64.isBlank()) {
            throw new IllegalStateException("Image processor returned no redacted image");
        }
        return Base64.getDecoder().decode(processedImageBase64);
    }

    /** Direct Lambda invocation for callers that build their own payload map. */
    public Map<String, Object> processBusinessCard(Map<String, Object> lambdaInput) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(lambdaInput);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payloadJson))
                .build();

        InvokeResponse response = lambdaClient.invoke(request);
        String responseJson = response.payload().asUtf8String();

        Map<String, Object> result = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> body = objectMapper.readValue((String) result.get("body"), Map.class);

        return body;
    }
}
