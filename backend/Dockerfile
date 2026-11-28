FROM golang:1.23-alpine AS builder

WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/oneroute-api ./cmd/api

FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata
ENV TZ=America/Lima

WORKDIR /app
COPY --from=builder /out/oneroute-api /usr/local/bin/oneroute-api

EXPOSE 8080
ENTRYPOINT ["oneroute-api"]
