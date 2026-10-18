import { DeleteObjectsCommand, GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

// Chore proof photos, in Neon's S3-compatible object storage
const BUCKET = "subphotos";
const VIEW_SECONDS = 60 * 60;

let client;

export function storageConfigured() {
  return Boolean(
    process.env.NEON_STORAGE_ENDPOINT &&
    process.env.NEON_STORAGE_ACCESS_KEY_ID &&
    process.env.NEON_STORAGE_SECRET_ACCESS_KEY,
  );
}

function storage() {
  client ??= new S3Client({
    region: "us-east-1",
    endpoint: process.env.NEON_STORAGE_ENDPOINT,
    credentials: {
      accessKeyId: process.env.NEON_STORAGE_ACCESS_KEY_ID,
      secretAccessKey: process.env.NEON_STORAGE_SECRET_ACCESS_KEY,
    },
    forcePathStyle: true,
    requestChecksumCalculation: "WHEN_REQUIRED",
  });
  return client;
}

export function putPhoto(key, bytes, contentType) {
  return storage().send(new PutObjectCommand({ Bucket: BUCKET, Key: key, Body: bytes, ContentType: contentType }));
}

// short-lived link the signed-in browser can show in an <img>
export function photoUrl(key) {
  return getSignedUrl(storage(), new GetObjectCommand({ Bucket: BUCKET, Key: key }), { expiresIn: VIEW_SECONDS });
}

export async function deletePhotos(keys) {
  for (let i = 0; i < keys.length; i += 1000) {
    const Objects = keys.slice(i, i + 1000).map((Key) => ({ Key }));
    await storage().send(new DeleteObjectsCommand({ Bucket: BUCKET, Delete: { Objects, Quiet: true } }));
  }
}
