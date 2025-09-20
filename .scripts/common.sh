#!/bin/bash

# Common utility functions for project scripts
# Source this file in other scripts: source "$(dirname "$0")/common.sh"

# Function to find project root (directory containing pom.xml)
find_project_root() {
    local current_dir="$(pwd)"
    while [ "$current_dir" != "/" ]; do
        if [ -f "$current_dir/pom.xml" ]; then
            echo "$current_dir"
            return 0
        fi
        current_dir="$(dirname "$current_dir")"
    done
    echo "❌ Error: Could not find project root (pom.xml not found)" >&2
    exit 1
}

# Function to ensure we're working from project root
ensure_project_root() {
    local project_root
    project_root=$(find_project_root)
    cd "$project_root"
    echo "📁 Working from project root: $project_root"
}

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_step() {
    echo -e "${BLUE}Step $1: $2${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}
