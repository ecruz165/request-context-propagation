#!/bin/bash

# Package Refactoring Script for Request Context Propagation Module
# Usage: ./refactor-package.sh [old_package_name] <new_package_name>
# Example: ./refactor-package.sh com.mycompany.requestcontext
# Example: ./refactor-package.sh com.example.demo com.mycompany.requestcontext

set -e  # Exit on any error

# Source common utilities
source "$(dirname "$0")/common.sh"

# Ensure we're working from project root
ensure_project_root

# Function to detect the main package automatically
detect_main_package() {
    local java_files
    local packages
    local base_packages
    local main_package

    # Find all Java files and extract package declarations
    java_files=$(find src/main/java -name "*.java" -type f 2>/dev/null || true)

    if [ -z "$java_files" ]; then
        echo "ERROR: No Java files found in src/main/java" >&2
        return 1
    fi

    # Extract unique package declarations
    packages=$(grep -h "^package " $java_files 2>/dev/null | \
               sed 's/package \(.*\);/\1/' | \
               sort | uniq)

    if [ -z "$packages" ]; then
        echo "ERROR: No package declarations found in Java files" >&2
        return 1
    fi

    # Find the common base package by looking for the shortest package that all others start with
    # First, get all unique packages and find the shortest common prefix
    base_packages=$(echo "$packages" | \
                   awk -F'.' '{print $1"."$2"."$3}' | \
                   sort | uniq -c | sort -nr | \
                   head -1 | \
                   awk '{print $2}')

    if [ -z "$base_packages" ]; then
        # Fallback: get the most common 2-level package
        base_packages=$(echo "$packages" | \
                       awk -F'.' '{print $1"."$2}' | \
                       sort | uniq -c | sort -nr | \
                       head -1 | \
                       awk '{print $2}')
    fi

    main_package="$base_packages"

    if [ -z "$main_package" ]; then
        echo "ERROR: Could not determine main package" >&2
        return 1
    fi

    echo "$main_package"
}


# Validate arguments
if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    print_error "Usage: $0 [old_package_name] <new_package_name>"
    print_error "Example: $0 com.mycompany.requestcontext"
    print_error "Example: $0 com.example.demo com.mycompany.requestcontext"
    print_error ""
    print_info "If old_package_name is not provided, it will be auto-detected"
    exit 1
fi

# Determine old and new packages based on argument count
if [ $# -eq 1 ]; then
    # Only new package provided, auto-detect old package
    NEW_PACKAGE="$1"
    OLD_PACKAGE=$(detect_main_package 2>&1)
    if [ $? -ne 0 ]; then
        print_error "Failed to auto-detect old package. Please specify it manually."
        exit 1
    fi
    print_info "Auto-detected old package: $OLD_PACKAGE"
else
    # Both packages provided: old_package new_package
    OLD_PACKAGE="$1"
    NEW_PACKAGE="$2"
    print_info "Using specified old package: $OLD_PACKAGE"
fi

NEW_PATH=$(echo "$NEW_PACKAGE" | tr '.' '/')

OLD_PATH=$(echo "$OLD_PACKAGE" | tr '.' '/')

echo -e "${GREEN}Starting package rename from $OLD_PACKAGE to $NEW_PACKAGE...${NC}"

# Validate package name formats
if [[ ! $OLD_PACKAGE =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$ ]]; then
    print_error "Invalid old package name format: $OLD_PACKAGE"
    print_error "Use lowercase letters and dots (e.g., com.example.demo)"
    exit 1
fi

if [[ ! $NEW_PACKAGE =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$ ]]; then
    print_error "Invalid new package name format: $NEW_PACKAGE"
    print_error "Use lowercase letters and dots (e.g., com.mycompany.module)"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "pom.xml" ] || [ ! -d "src" ]; then
    print_error "This script must be run from the project root directory (where pom.xml exists)"
    exit 1
fi

# Show summary of what will be changed
print_info "Package refactoring summary:"
print_info "  Old package: $OLD_PACKAGE"
print_info "  New package: $NEW_PACKAGE"
print_info "  Old path: src/main/java/$OLD_PATH"
print_info "  New path: src/main/java/$NEW_PATH"

# Count files that will be affected
java_files=$(find . -name "*.java" -type f -exec grep -l "$OLD_PACKAGE" {} \; 2>/dev/null | wc -l)
xml_files=$(find . -name "*.xml" -type f -exec grep -l "$OLD_PACKAGE" {} \; 2>/dev/null | wc -l)
yaml_files=$(find . \( -name "*.yml" -o -name "*.yaml" \) -type f -exec grep -l "$OLD_PACKAGE" {} \; 2>/dev/null | wc -l)
properties_files=$(find . -name "*.properties" -type f -exec grep -l "$OLD_PACKAGE" {} \; 2>/dev/null | wc -l)

print_info "Files to be updated:"
print_info "  Java files: $java_files"
print_info "  XML files: $xml_files"
print_info "  YAML files: $yaml_files"
print_info "  Properties files: $properties_files"

echo ""
read -p "Do you want to continue? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    print_info "Operation cancelled by user"
    exit 0
fi

# Create backup in parent directory
print_step "1" "Creating backup in parent directory..."

# Get the current project directory name
PROJECT_NAME=$(basename "$(pwd)")
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="../${PROJECT_NAME}-backup-${TIMESTAMP}"

# Create backup of entire directory in parent folder
if cp -r . "$BACKUP_DIR" 2>/dev/null; then
    print_success "Backup created at: $BACKUP_DIR"
    print_info "Full project backup includes all files and git history"
else
    print_error "Failed to create backup in parent directory"
    print_error "Please ensure you have write permissions in the parent directory"
    print_error "Or run the script from a location where parent directory is writable"
    exit 1
fi

# Step 2: Move directory structure from old base to new base
print_step "2" "Moving directory structure from old base to new base..."

# Function to move entire directory tree
move_package_tree() {
    local source_base="$1"
    local old_path="$2"
    local new_path="$3"

    if [ -d "$source_base/$old_path" ]; then
        # Create parent directories for new path
        local new_parent_dir="$(dirname "$source_base/$new_path")"
        mkdir -p "$new_parent_dir"

        # Move the entire directory tree from old to new location
        mv "$source_base/$old_path" "$source_base/$new_path"
        print_success "Moved directory tree: $source_base/$old_path -> $source_base/$new_path"

        # Clean up empty parent directories of old path
        cleanup_empty_parent_dirs "$source_base" "$(dirname "$old_path")"
    else
        print_info "Directory $source_base/$old_path does not exist, skipping"
    fi
}

# Function to clean up empty parent directories
cleanup_empty_parent_dirs() {
    local base_dir="$1"
    local old_path="$2"

    # Clean up parent directories going up the hierarchy
    local current_path="$old_path"
    while [ "$current_path" != "." ] && [ "$current_path" != "" ]; do
        if [ "$current_path" = "." ]; then
            break
        fi

        # Only remove if directory exists and is empty
        if [ -d "$base_dir/$current_path" ] && [ -z "$(ls -A "$base_dir/$current_path" 2>/dev/null)" ]; then
            rmdir "$base_dir/$current_path" 2>/dev/null || true
            print_success "Removed empty directory: $base_dir/$current_path"
        else
            # Stop if directory is not empty (contains other packages)
            break
        fi

        current_path=$(dirname "$current_path")
    done
}

# Move main and test source directories
move_package_tree "src/main/java" "$OLD_PATH" "$NEW_PATH"
move_package_tree "src/test/java" "$OLD_PATH" "$NEW_PATH"

print_success "Directory structure moved"

# Step 3: Update package declarations and imports in Java files
print_step "3" "Updating package declarations and imports in all Java files..."

find . -name "*.java" -type f | while read -r file; do
    # Check if file contains any reference to the old package
    if grep -q "$OLD_PACKAGE" "$file"; then
        # Create backup of file
        cp "$file" "$file.bak"

        # Simple find and replace: replace old package with new package everywhere
        # This works because we've already moved the directory structure
        sed -i.tmp "s/$OLD_PACKAGE/$NEW_PACKAGE/g" "$file"

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
