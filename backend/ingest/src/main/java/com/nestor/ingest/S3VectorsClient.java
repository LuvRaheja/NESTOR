package com.nestor.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.PutVectorsRequest;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Client for storing vectors in S3 Vectors using the AWS SDK v2.
 * <p>
 * Uses {@code software.amazon.awssdk.services.s3vectors.S3VectorsClient} directly,
 * which works inside Lambda containers without any CLI dependency.
 */
public class S3VectorsClient {

    private static final Logger log = LoggerFactory.getLogger(S3VectorsClient.class);

    private final String vectorBucket;
    private final String indexName;
    private final software.amazon.awssdk.services.s3vectors.S3VectorsClient sdkClient;

    public S3VectorsClient(String vectorBucket, String indexName, String region) {
        this.vectorBucket = vectorBucket;
        this.indexName = indexName;
        this.sdkClient = software.amazon.awssdk.services.s3vectors.S3VectorsClient.builder()
                .region(Region.of(region))
                .build();
    }

    /**
     * Store a single vector in S3 Vectors.
     *
     * @param key      unique vector key (UUID)
     * @param data     embedding vector (list of floats)
     * @param metadata key-value metadata to store alongside the vector
     * @throws RuntimeException if the put-vectors call fails
     */
    public void putVector(String key, List<Float> data, Map<String, String> metadata) {
        try {
            log.info("Storing vector in bucket: {}, index: {}, key: {}", vectorBucket, indexName, key);

            // Convert String metadata map to SDK Document (map of string Documents)
            Map<String, Document> docMetadata = metadata.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> Document.fromString(e.getValue())
                    ));

            PutInputVector vector = PutInputVector.builder()
                    .key(key)
                    .data(VectorData.fromFloat32(data))
                    .metadata(Document.fromMap(docMetadata))
                    .build();

            PutVectorsRequest request = PutVectorsRequest.builder()
                    .vectorBucketName(vectorBucket)
                    .indexName(indexName)
                    .vectors(vector)
                    .build();

            sdkClient.putVectors(request);

            log.info("Successfully stored vector {}", key);

        } catch (RuntimeException e) {
            log.error("Failed to store vector {} in S3 Vectors: {}", key, e.getMessage());
            throw new RuntimeException("Failed to store vector in S3 Vectors: " + e.getMessage(), e);
        }
    }
}
