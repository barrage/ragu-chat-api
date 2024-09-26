FROM nexus.barrage.net:13455/barrage-internal/ubuntu-java-17

WORKDIR /app

COPY build/libs/llmao.jar .
RUN mkdir -p /app/config
EXPOSE 42069/tcp
CMD ["java", "-jar", "/app/llmao.jar"]