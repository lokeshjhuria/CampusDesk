FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY *.java ./
RUN mkdir out && javac -encoding UTF-8 -d out *.java

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 --home-dir /app app \
    && mkdir -p /app/data \
    && chown -R app:app /app
COPY --from=build --chown=app:app /build/out /app/out
ENV HOST=0.0.0.0
ENV PORT=8080
EXPOSE 8080
VOLUME ["/app/data"]
USER app
ENTRYPOINT ["java", "-cp", "/app/out", "college.registration.Main"]
