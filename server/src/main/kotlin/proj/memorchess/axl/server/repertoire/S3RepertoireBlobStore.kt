package proj.memorchess.axl.server.repertoire

import java.net.URI
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * Payload storage on an S3-compatible bucket, addressed by the payload's sha256.
 *
 * Cloudflare R2 (the intended production target) speaks the S3 API with path style addressing and
 * accepts any region string, so `"auto"` is used rather than making the region configurable.
 *
 * Every call blocks on the underlying SDK client, so it runs on [ioDispatcher] rather than the
 * caller's dispatcher, the same convention [proj.memorchess.axl.server.sync.SyncStore] uses for its
 * blocking JDBC calls.
 *
 * @param endpoint The S3-compatible endpoint, e.g. `https://<accountid>.r2.cloudflarestorage.com`.
 * @param bucket Bucket holding the objects.
 */
internal class S3RepertoireBlobStore(
  endpoint: URI,
  private val bucket: String,
  accessKeyId: String,
  secretAccessKey: String,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RepertoireBlobStore {

  private val client: S3Client =
    S3Client.builder()
      .endpointOverride(endpoint)
      .region(Region.of("auto"))
      .forcePathStyle(true)
      .credentialsProvider(
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
      )
      .build()

  override suspend fun put(sha256: String, bytes: ByteArray) {
    withContext(ioDispatcher) {
      client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(sha256).build(),
        RequestBody.fromBytes(bytes),
      )
    }
  }

  override suspend fun get(sha256: String): ByteArray? =
    withContext(ioDispatcher) {
      try {
        client
          .getObject(
            GetObjectRequest.builder().bucket(bucket).key(sha256).build(),
            ResponseTransformer.toBytes(),
          )
          .asByteArray()
      } catch (e: NoSuchKeyException) {
        null
      }
    }

  override suspend fun delete(sha256: String) {
    withContext(ioDispatcher) {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(sha256).build())
    }
  }
}
