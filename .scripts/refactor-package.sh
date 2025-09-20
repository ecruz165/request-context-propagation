#!/bin/bash

# Package Refactoring Script for Request Context Propagation Module
# Usage: ./refactor-package.sh <new_package_name>
# Example: ./refactor-package.sh com.mycompany.requestcontext

set -e  # Exit on any error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
OLD_PACKAGE="com.example.demo"
OLD_PATH="com/example/demo"

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

# Validate arguments
if [ $# -ne 1 ]; then
    print_error "Usage: $0 <new_package_name>"
    print_error "Example: $0 com.mycompany.requestcontext"
    exit 1
fi

NEW_PACKAGE="$1"
NEW_PATH="${NEW_PACKAGE//./\/}"

echo -e "${GREEN}Starting package rename from $OLD_PACKAGE to $NEW_PACKAGE...${NC}"

# Validate new package name format
if [[ ! $NEW_PACKAGE =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$ ]]; then
    print_error "Invalid package name format. Use lowercase letters and dots (e.g., com.mycompany.module)"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "pom.xml" ] || [ ! -d "src" ]; then
    print_error "This script must be run from the project root directory (where pom.xml exists)"
    exit 1
fi

# Create backup
print_step "1" "Creating backup..."
BACKUP_DIR="backup-$(date +%Y%m%d-%H%M%S)"
cp -r . "../$BACKUP_DIR" 2>/dev/null || {
    print_warning "Could not create backup in parent directory, creating local backup"
    mkdir -p "$BACKUP_DIR"
    cp -r src pom.xml README.md *.md "$BACKUP_DIR/" 2>/dev/null || true
}
print_success "Backup created at $BACKUP_DIR"

# Step 2: Rename directory structure
print_step "2" "Renaming directory structure..."

# Create new directory structure
create_dir_if_not_exists() {
    if [ ! -d "$1" ]; then
        mkdir -p "$1"
        print_success "Created directory: $1"
    fi
}

# Main source directories
create_dir_if_not_exists "src/main/java/$NEW_PATH"
create_dir_if_not_exists "src/test/java/$NEW_PATH"

# Move all files from old to new directory structure
if [ -d "src/main/java/$OLD_PATH" ]; then
    find "src/main/java/$OLD_PATH" -name "*.java" -type f | while read -r file; do
        relative_path="${file#src/main/java/$OLD_PATH/}"
        new_file="src/main/java/$NEW_PATH/$relative_path"
        new_dir=$(dirname "$new_file")
        mkdir -p "$new_dir"
        mv "$file" "$new_file"
        print_success "Moved: $file -> $new_file"
    done
fi

if [ -d "src/test/java/$OLD_PATH" ]; then
    find "src/test/java/$OLD_PATH" -name "*.java" -type f | while read -r file; do
        relative_path="${file#src/test/java/$OLD_PATH/}"
        new_file="src/test/java/$NEW_PATH/$relative_path"
        new_dir=$(dirname "$new_file")
        mkdir -p "$new_dir"
        mv "$file" "$new_file"
        print_success "Moved: $file -> $new_file"
    done
fi

# Remove old empty directories
if [ -d "src/main/java/$OLD_PATH" ]; then
    find "src/main/java/$OLD_PATH" -type d -empty -delete 2>/dev/null || true
    rmdir "src/main/java/com/example" 2>/dev/null || true
    rmdir "src/main/java/com" 2>/dev/null || true
fi

if [ -d "src/test/java/$OLD_PATH" ]; then
    find "src/test/java/$OLD_PATH" -type d -empty -delete 2>/dev/null || true
    rmdir "src/test/java/com/example" 2>/dev/null || true
    rmdir "src/test/java/com" 2>/dev/null || true
fi

print_success "Directory structure renamed"

# Step 3: Update package declarations and imports in Java files
print_step "3" "Updating package declarations and imports..."

find . -name "*.java" -type f | while read -r file; do
    if grep -q "$OLD_PACKAGE" "$file"; then
        # Create backup of file
        cp "$file" "$file.bak"
        
        # Replace package declarations and imports
        sed -i.tmp "s/package $OLD_PACKAGE/package $NEW_PACKAGE/g" "$file"
        sed -i.tmp "s/import $OLD_PACKAGE/import $NEW_PACKAGE/g" "$file"
        sed -i.tmp "s/import static $OLD_PACKAGE/import static $NEW_PACKAGE/g" "$file"
        
        # Clean up temporary files
        rm -f "$file.tmp" "$file.bak"
        
        print_success "Updated Java file: $file"
    fi
done

# Step 4: Update XML configuration files
print_step "4" "Updating XML configuration files..."

find . -name "*.xml" -type f | while read -r file; do
    if grep -q "$OLD_PACKAGE" "$file"; then
        cp "$file" "$file.bak"
        sed -i.tmp "s/$OLD_PACKAGE/$NEW_PACKAGE/g" "$file"
        rm -f "$file.tmp" "$file.bak"
        print_success "Updated XML file: $file"
    fi
done

# Step 5: Update YAML files
print_step "5" "Updating YAML files..."

find . -name "*.yml" -o -name "*.yaml" | while read -r file; do
    if grep -q "$OLD_PACKAGE" "$file"; then
        cp "$file" "$file.bak"
        sed -i.tmp "s/$OLD_PACKAGE/$NEW_PACKAGE/g" "$file"
        rm -f "$file.tmp" "$file.bak"
        print_success "Updated YAML file: $file"
    fi
done

# Step 6: Update properties files
print_step "6" "Updating properties files..."

find . -name "*.properties" -type f | while read -r file; do
    if grep -q "$OLD_PACKAGE" "$file"; then
        cp "$file" "$file.bak"
        sed -i.tmp "s/$OLD_PACKAGE/$NEW_PACKAGE/g" "$file"
        rm -f "$file.tmp" "$file.bak"
        print_success "Updated properties file: $file"
    fi
done

print_step "7" "Cleaning up and validating..."

# Verify compilation
print_step "8" "Testing compilation..."
if command -v mvn &> /dev/null; then
    if mvn compile -q; then
        print_success "Compilation successful!"
    else
        print_warning "Compilation failed. Please check for any remaining references to $OLD_PACKAGE"
    fi
else
    print_warning "Maven not found. Please manually verify compilation with: mvn compile"
fi

echo ""
echo -e "${GREEN}Package refactoring completed!${NC}"
echo -e "${BLUE}Summary:${NC}"
echo "  Old package: $OLD_PACKAGE"
echo "  New package: $NEW_PACKAGE"
echo "  Backup location: $BACKUP_DIR"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Review the changes and test your application"
echo "2. Update any external references to the old package name"
echo "3. Update documentation and README files if needed"
echo "4. Run full test suite: mvn test"
echo "5. If everything works, you can remove the backup: rm -rf $BACKUP_DIR"
