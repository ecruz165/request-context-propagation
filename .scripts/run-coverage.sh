#!/bin/bash

# Script to run tests and generate JaCoCo coverage reports
# Usage: ./run-coverage.sh

set -e

# Source common utilities
source "$(dirname "$0")/common.sh"

# Ensure we're working from project root
ensure_project_root

echo "🧪 Running tests with JaCoCo coverage..."
echo "========================================"

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./mvnw clean

# Run tests with coverage using Maven profile
echo "🔬 Running unit tests with coverage..."
./mvnw clean test -Pcoverage

# Run integration tests with full coverage verification
echo "🔗 Running integration tests with coverage verification..."
./mvnw verify -Pcoverage-verify -Dmaven.test.failure.ignore=true

# Generate site reports (optional)
echo "📊 Generating site reports..."
./mvnw site -Dmaven.test.failure.ignore=true

echo ""
echo "✅ Coverage reports generated successfully!"
echo ""
echo "📋 Coverage Report Locations:"
echo "  • Unit Test Coverage:        target/site/jacoco/index.html"
echo "  • Integration Test Coverage: target/site/jacoco-it/index.html" 
echo "  • Merged Coverage:           target/site/jacoco-merged/index.html"
echo "  • Site Reports:              target/site/index.html"
echo ""
echo "🌐 To view reports, open the HTML files in your browser:"
echo "  open target/site/jacoco/index.html"
echo ""

# Check if coverage reports exist and show summary
if [ -f "target/site/jacoco/index.html" ]; then
    echo "📈 Coverage Summary:"
    echo "  Unit test coverage report is available at: target/site/jacoco/index.html"
fi

if [ -f "target/site/jacoco-merged/index.html" ]; then
    echo "  Merged coverage report is available at: target/site/jacoco-merged/index.html"
fi

echo ""
echo "🎯 Next Steps:"
echo "  1. Open the HTML reports in your browser to view detailed coverage"
echo "  2. Review uncovered lines and add tests as needed"
echo "  3. Adjust coverage thresholds in pom.xml if desired"
echo ""
