# 서버 실행 이미지. Gradle 빌드는 배포하는 PC 에서 끝내고(deploy.sh), 여기서는 실행본만 담는다.
# 1GB 인스턴스에서 Gradle 을 돌리면 메모리가 모자라기 때문이다.
FROM eclipse-temurin:17-jre-jammy

RUN useradd --system --uid 1001 talk \
    && mkdir -p /data/files \
    && chown talk /data/files

COPY --chown=talk server /app

USER talk
ENV PORT=8080 \
    FILES_DIR=/data/files \
    JAVA_OPTS="-Xms96m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseSerialGC -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
