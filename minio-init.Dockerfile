FROM minio/minio:RELEASE.2024-12-18T13-15-44Z

COPY minio-init.sh /minio-init.sh
RUN chmod +x /minio-init.sh

ENTRYPOINT ["/minio-init.sh"]
