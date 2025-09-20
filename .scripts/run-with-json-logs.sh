#!/bin/bash

# Script to run the application with different logging configurations
# Usage: ./run-with-json-logs.sh [profile]

set -e

# Source common utilities
source "$(dirname "$0")/common.sh"

# Ensure we're working from project root
ensure_project_root

PROFILE=${1:-json-logs}

echo "🚀 Starting application with logging profile: $PROFILE"
echo "=================================================="

case $PROFILE in
    "json-logs"|"json")
        echo "📊 Using JSON structured logging"
        echo "  • Console output: JSON format"
        echo "  • File output: JSON format with rotation"
        echo "  • MDC context: Included in all log entries"
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=$PROFILE
        ;;
    "dev"|"development"|"local")
        echo "🔧 Using development logging"
        echo "  • Console output: Human-readable format"
        echo "  • Debug level: Enabled for application"
        echo "  • Security debug: Enabled"
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=$PROFILE
        ;;
    "prod"|"production")
        echo "🏭 Using production logging"
        echo "  • Console output: JSON format"
        echo "  • File output: JSON format with async appender"
        echo "  • Log level: INFO with reduced framework noise"
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=$PROFILE
        ;;
    "test")
        echo "🧪 Using test logging"
        echo "  • Console output: Minimal logging"
        echo "  • Log level: WARN with application INFO"
        ./mvnw spring-boot:run -Dspring-boot.run.profiles=$PROFILE
        ;;
    "default")
        echo "📝 Using default logging"
        echo "  • Console output: Pattern format with MDC"
        echo "  • File output: Standard log file"
        ./mvnw spring-boot:run
        ;;
    *)
        echo "❌ Invalid profile: $PROFILE"
        echo ""
        echo "Available profiles:"
        echo "  json-logs    - JSON structured logging"
        echo "  dev          - Development with debug logging"
        echo "  prod         - Production with JSON and async logging"
        echo "  test         - Minimal logging for tests"
        echo "  default      - Standard pattern logging"
        echo ""
        echo "Usage: $0 [profile]"
        echo "Example: $0 json-logs"
        exit 1
        ;;
esac
