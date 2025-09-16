#!/bin/bash

# Script to open JaCoCo coverage reports in the browser
# Usage: ./view-coverage.sh [report-type]
# report-type: unit (default), integration, merged, or all

set -e

REPORT_TYPE=${1:-unit}

echo "📊 Opening JaCoCo Coverage Reports..."
echo "===================================="

case $REPORT_TYPE in
    "unit")
        if [ -f "target/site/jacoco/index.html" ]; then
            echo "🔬 Opening Unit Test Coverage Report..."
            open target/site/jacoco/index.html
        else
            echo "❌ Unit test coverage report not found. Run: ./mvnw test jacoco:report"
            exit 1
        fi
        ;;
    "integration")
        if [ -f "target/site/jacoco-it/index.html" ]; then
            echo "🔗 Opening Integration Test Coverage Report..."
            open target/site/jacoco-it/index.html
        else
            echo "❌ Integration test coverage report not found. Run: ./mvnw verify"
            exit 1
        fi
        ;;
    "merged")
        if [ -f "target/site/jacoco-merged/index.html" ]; then
            echo "🎯 Opening Merged Coverage Report..."
            open target/site/jacoco-merged/index.html
        else
            echo "❌ Merged coverage report not found. Run: ./mvnw verify"
            exit 1
        fi
        ;;
    "all")
        echo "🌐 Opening All Available Coverage Reports..."
        
        if [ -f "target/site/jacoco/index.html" ]; then
            echo "  • Unit Test Coverage"
            open target/site/jacoco/index.html
        fi
        
        if [ -f "target/site/jacoco-it/index.html" ]; then
            echo "  • Integration Test Coverage"
            open target/site/jacoco-it/index.html
        fi
        
        if [ -f "target/site/jacoco-merged/index.html" ]; then
            echo "  • Merged Coverage"
            open target/site/jacoco-merged/index.html
        fi
        
        if [ -f "target/site/index.html" ]; then
            echo "  • Site Reports"
            open target/site/index.html
        fi
        ;;
    *)
        echo "❌ Invalid report type: $REPORT_TYPE"
        echo ""
        echo "Usage: $0 [report-type]"
        echo "  report-type: unit (default), integration, merged, or all"
        echo ""
        echo "Examples:"
        echo "  $0           # Opens unit test coverage"
        echo "  $0 unit      # Opens unit test coverage"
        echo "  $0 merged    # Opens merged coverage"
        echo "  $0 all       # Opens all available reports"
        exit 1
        ;;
esac

echo ""
echo "✅ Coverage report(s) opened in your default browser!"
echo ""
echo "📋 Available Commands:"
echo "  ./view-coverage.sh unit        # Unit test coverage"
echo "  ./view-coverage.sh integration # Integration test coverage"
echo "  ./view-coverage.sh merged      # Merged coverage"
echo "  ./view-coverage.sh all         # All reports"
echo ""
