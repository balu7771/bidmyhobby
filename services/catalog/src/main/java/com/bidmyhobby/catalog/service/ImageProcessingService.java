package com.bidmyhobby.catalog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${image.processor.url:${IMAGE_PROCESSOR_URL:}}")
    private String processorUrl;

    @Value("${aws.lambda.function.image-processor:bidmyhobby-image-processor}")
    private String functionName;

    public ImageProcessingService(LambdaClient lambdaClient) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    /** Process a hobby item image (no business-card Textract analysis). */
    public byte[] processImage(byte[] imageData, String email) throws Exception {
        return processImage(imageData, email, false);
    }

    /**
     * Process any image via the Python image-processor microservice or AWS Lambda.
     *
     * @param isBusinessCard when true runs OCR to extract and mask contact info
     */
    public byte[] processImage(byte[] imageData, String email, boolean isBusinessCard) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("imageData", Base64.getEncoder().encodeToString(imageData));
        payload.put("email", email);
        payload.put("isBusinessCard", isBusinessCard);

        if (processorUrl != null && !processorUrl.isBlank()) {
            log.info("Processing image via HTTP microservice: {}", processorUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(processorUrl, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("processedImage")) {
                String processedImageBase64 = (String) body.get("processedImage");
                return Base64.getDecoder().decode(processedImageBase64);
            }
            throw new IllegalStateException("Image processor returned empty response");
        }

        log.info("Processing image via AWS Lambda: {}", functionName);
        String payloadJson = objectMapper.writeValueAsString(payload);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payloadJson))
                .build();

        InvokeResponse response;
        try {
            response = lambdaClient.invoke(request);
        } catch (SdkException e) {
            log.error("Failed to invoke AWS Lambda image processor '{}': {}", functionName, e.getMessage(), e);
            throw e;
        }
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

    /** Direct image processor invocation for callers that build their own payload map. */
    public Map<String, Object> processBusinessCard(Map<String, Object> lambdaInput) throws Exception {
        if (processorUrl != null && !processorUrl.isBlank()) {
            log.info("Processing business card via HTTP microservice: {}", processorUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(lambdaInput, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(processorUrl, entity, Map.class);
            return response.getBody();
        }

        String payloadJson = objectMapper.writeValueAsString(lambdaInput);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payloadJson))
                .build();

        InvokeResponse response;
        try {
            response = lambdaClient.invoke(request);
        } catch (SdkException e) {
            log.error("Failed to invoke AWS Lambda image processor '{}': {}", functionName, e.getMessage(), e);
            throw e;
        }
        String responseJson = response.payload().asUtf8String();

        Map<String, Object> result = objectMapper.readValue(responseJson, Map.class);
        Map<String, Object> body = objectMapper.readValue((String) result.get("body"), Map.class);

        return body;
    }
}
