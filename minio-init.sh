#!/bin/sh
set -e

MINIO_ENDPOINT=${MINIO_ENDPOINT:-http://minio:9000}
MINIO_ROOT_USER=${MINIO_ROOT_USER:-minioadmin}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:-minioadmin}
MINIO_BUCKET=${MINIO_BUCKET:-mokatest}

echo "Waiting for MinIO at ${MINIO_ENDPOINT}..."

until curl -sf "${MINIO_ENDPOINT}/minio/health/live" > /dev/null 2>&1; do
  echo "MinIO not ready yet, retrying in 2s..."
  sleep 2
done

echo "MinIO is ready. Setting up alias and bucket..."

mc alias set local "${MINIO_ENDPOINT}" "${MINIO_ROOT_USER}" "${MINIO_ROOT_PASSWORD}" --api S3v4

if mc ls local/"${MINIO_BUCKET}" > /dev/null 2>&1; then
  echo "Bucket ${MINIO_BUCKET} already exists."
else
  echo "Creating bucket ${MINIO_BUCKET}..."
  mc mb local/"${MINIO_BUCKET}"
fi

echo "MinIO initialization completed."
