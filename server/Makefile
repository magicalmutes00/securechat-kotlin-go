.PHONY: build run test migrate sqlc lint clean dev

# Build the server binary
build:
	go build -o bin/server ./cmd/server

# Run the server
run:
	go run ./cmd/server

# Run tests
test:
	go test -v ./...

# Run tests with coverage
test-coverage:
	go test -v -coverprofile=coverage.out ./...
	go tool cover -html=coverage.out -o coverage.html

# Run database migrations (reads .env; add &tls=true for TiDB Cloud)
migrate:
	@set -a; . ./.env; set +a; \
	goose -dir migrations mysql "$${SECURECHAT_MYSQL_USER}:$${SECURECHAT_MYSQL_PASSWORD}@tcp($${SECURECHAT_MYSQL_HOST}:$${SECURECHAT_MYSQL_PORT})/$${SECURECHAT_MYSQL_DATABASE}?parseTime=true&tls=true" up

# Generate sqlc code
sqlc:
	sqlc generate

# Lint code
lint:
	golangci-lint run

# Clean build artifacts
clean:
	rm -rf bin/
	rm -f coverage.out coverage.html

# Development: run with air for hot reload
dev:
	air -c .air.toml

# Install development tools
install-tools:
	go install github.com/pressly/goose/v3/cmd/goose@latest
	go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest
	go install github.com/cosmtrek/air@latest
	go install github.com/golangci/golangci-lint/cmd/golangci-lint@latest

# Generate RSA keys for JWT
gen-keys:
	@go run ./cmd/genkeys

# Docker commands (for Cloudinary/Ollama only)
docker-up:
	docker-compose up -d

docker-down:
	docker-compose down

docker-logs:
	docker-compose logs -f