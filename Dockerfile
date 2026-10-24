# SERC hosted demo: build the JAR from source, then run it on a JRE.
# Render (Docker runtime) builds this and sets PORT; bind/demo settings come
# from SERC_BIND, SERC_PUBLIC_HOST, SERC_DEMO_CODE, SERC_MODE_LABEL env vars.
# Extra startup flags (for example "--mysql --init-demo" with SERC_DB_URL /
# SERC_DB_USER / SERC_DB_PASSWORD) go in SERC_ARGS. The Connector/J driver is
# copied onto the classpath for MySQL mode.
FROM eclipse-temurin:11-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out dist \
 && javac --release 11 -encoding UTF-8 -d out src/main/java/serc/*.java \
 && cp -R src/main/resources/. out/ \
 && jar --create --file dist/serc.jar --main-class serc.WebMain -C out .

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/dist/serc.jar dist/serc.jar
COPY lib ./lib
EXPOSE 8765
CMD ["sh","-c","java -cp dist/serc.jar:lib/mysql-connector-j-8.4.0.jar serc.WebMain $SERC_ARGS"]
