FROM node:22-alpine AS web
WORKDIR /workspace/webApp
COPY webApp/package.json webApp/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY webApp/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . ./
COPY --from=web /workspace/webApp/dist ./webApp/dist
RUN ./gradlew --configure-on-demand :room-server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /opt/foodrun
COPY --from=build /workspace/room-server/build/install/room-server/ ./
ENV FOODRUN_TLS_MODE=proxy
ENV FOODRUN_DATA=/var/lib/foodrun
EXPOSE 10000
VOLUME ["/var/lib/foodrun"]
ENTRYPOINT ["./bin/room-server"]
