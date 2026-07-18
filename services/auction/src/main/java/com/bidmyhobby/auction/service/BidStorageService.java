package com.bidmyhobby.auction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.*;

@Service
public class BidStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BidStorageService.class);
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.bucket.bids-prefix:bids/}")
    private String bidsPrefix;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    public BidStorageService(S3Client s3Client) {
        this.s3Client = s3Client;
        this.objectMapper = new ObjectMapper();
    }

    public void saveBid(String itemId, String bidderEmail, double bidAmount) throws Exception {
        try {
            String bidId = UUID.randomUUID().toString();
            String bidKey = bidsPrefix + itemId + "/" + bidId + ".json";

            Map<String, Object> bidData = new HashMap<>();
            bidData.put("bidId", bidId);
            bidData.put("itemId", itemId);
            bidData.put("bidderEmail", bidderEmail);
            bidData.put("bidAmount", bidAmount);
            bidData.put("currency", "INR");
            bidData.put("timestamp", System.currentTimeMillis());
            bidData.put("status", "ACTIVE");

            String jsonContent = objectMapper.writeValueAsString(bidData);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(bidKey)
                    .contentType("application/json")
                    .build();

            logger.debug("Saving bid to S3 - Bucket: {}, Key: {}, Region: {}", bucketName, bidKey, region);
            s3Client.putObject(request, RequestBody.fromString(jsonContent));
            logger.info("✅ Bid saved successfully - BidID: {}, ItemID: {}, Bidder: {}", bidId, itemId, bidderEmail);
        } catch (S3Exception e) {
            logger.error("❌ S3 Error saving bid - Status: {}, Error Code: {}, Message: {}",
                    e.statusCode(), e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());
            logger.error("S3 Error Details - Bucket: {}, Region: {}", bucketName, region);
            throw e;
        } catch (Exception e) {
            logger.error("❌ Unexpected error saving bid", e);
            throw e;
        }
    }

    public List<Map<String, Object>> getBidsForItem(String itemId) {
        List<Map<String, Object>> bids = new ArrayList<>();

        try {
            String prefix = bidsPrefix + itemId + "/";
            logger.debug("Retrieving bids from S3 - Bucket: {}, Prefix: {}, Region: {}", bucketName, prefix, region);

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            logger.debug("Found {} bid objects for item: {}", listResponse.contents().size(), itemId);

            for (S3Object s3Object : listResponse.contents()) {
                try {
                    GetObjectRequest getRequest = GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Object.key())
                            .build();

                    String content = s3Client.getObjectAsBytes(getRequest).asUtf8String();
                    Map<String, Object> bidData = objectMapper.readValue(content, Map.class);
                    bids.add(bidData);
                    logger.debug("Loaded bid from S3 - Key: {}", s3Object.key());
                } catch (S3Exception e) {
                    logger.warn("⚠️ S3 Error retrieving bid object - Key: {}, Error: {}", s3Object.key(), e.awsErrorDetails().errorMessage());
                } catch (Exception e) {
                    logger.warn("⚠️ Error parsing bid data from key: {} - {}", s3Object.key(), e.getMessage());
                }
            }

            // Sort by timestamp (newest first)
            bids.sort((a, b) -> Long.compare(
                    (Long) b.get("timestamp"),
                    (Long) a.get("timestamp")
            ));

            logger.info("✅ Retrieved {} bids for item: {}", bids.size(), itemId);
        } catch (S3Exception e) {
            logger.error("❌ S3 Error listing bids - Bucket: {}, Status: {}, Error Code: {}",
                    bucketName, e.statusCode(), e.awsErrorDetails().errorCode());
            logger.error("Check bucket configuration - Bucket: {}, Region: {}", bucketName, region);
        } catch (Exception e) {
            logger.error("❌ Unexpected error retrieving bids for item: {}", itemId, e);
        }

        return bids;
    }

    public List<Map<String, Object>> getBidsByUser(String userEmail) {
        List<Map<String, Object>> userBids = new ArrayList<>();

        try {
            logger.debug("Retrieving bids for user - Email: {}, Bucket: {}, Region: {}", userEmail, bucketName, region);

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(bidsPrefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            logger.debug("Found {} total bid objects", listResponse.contents().size());

            for (S3Object s3Object : listResponse.contents()) {
                try {
                    GetObjectRequest getRequest = GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Object.key())
                            .build();

                    String content = s3Client.getObjectAsBytes(getRequest).asUtf8String();
                    Map<String, Object> bidData = objectMapper.readValue(content, Map.class);

                    if (userEmail.equals(bidData.get("bidderEmail"))) {
                        userBids.add(bidData);
                        logger.debug("Matched user bid - Key: {}", s3Object.key());
                    }
                } catch (S3Exception e) {
                    logger.warn("⚠️ S3 Error retrieving bid object - Key: {}, Error: {}", s3Object.key(), e.awsErrorDetails().errorMessage());
                } catch (Exception e) {
                    logger.warn("⚠️ Error parsing bid data from key: {} - {}", s3Object.key(), e.getMessage());
                }
            }

            // Sort by timestamp (newest first)
            userBids.sort((a, b) -> Long.compare(
                    (Long) b.get("timestamp"),
                    (Long) a.get("timestamp")
            ));

            logger.info("✅ Retrieved {} bids for user: {}", userBids.size(), userEmail);
        } catch (S3Exception e) {
            logger.error("❌ S3 Error listing user bids - Bucket: {}, Status: {}, Error Code: {}",
                    bucketName, e.statusCode(), e.awsErrorDetails().errorCode());
            logger.error("Check bucket configuration - Bucket: {}, Region: {}", bucketName, region);
        } catch (Exception e) {
            logger.error("❌ Unexpected error retrieving bids for user: {}", userEmail, e);
        }

        return userBids;
    }
}

