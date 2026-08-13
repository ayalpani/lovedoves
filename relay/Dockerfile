FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/lovedoves-relay ./cmd/lovedoves-relay

FROM gcr.io/distroless/static-debian13:nonroot
COPY --from=build /out/lovedoves-relay /lovedoves-relay
VOLUME ["/data"]
EXPOSE 8787
ENV LOVE_DOVES_DATA_DIR=/data
ENTRYPOINT ["/lovedoves-relay"]
