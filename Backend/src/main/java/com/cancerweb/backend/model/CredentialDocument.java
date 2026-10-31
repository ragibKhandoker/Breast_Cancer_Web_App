package com.cancerweb.backend.model;

import java.time.Instant;

/**
 * A verification document a surgeon uploaded (degree, registration certificate, ...), stored in GridFS.
 *
 * @param fileId the GridFS file id
 */
public record CredentialDocument(String fileId, String filename, String contentType, long size, Instant uploadedAt) {
}
