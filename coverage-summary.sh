#!/bin/bash

# Script to display JaCoCo coverage summary
# Usage: ./coverage-summary.sh

set -e

echo "📊 JaCoCo Coverage Summary"
echo "=========================="

if [ ! -f "target/site/jacoco/jacoco.csv" ]; then
    echo "❌ Coverage report not found. Run tests first:"
    echo "   ./mvnw test jacoco:report"
    exit 1
fi

echo ""
echo "📈 Overall Coverage Statistics:"
echo "------------------------------"

# Parse CSV and calculate totals
awk -F',' '
BEGIN {
    total_instruction_missed = 0
    total_instruction_covered = 0
    total_branch_missed = 0
    total_branch_covered = 0
    total_line_missed = 0
    total_line_covered = 0
    total_method_missed = 0
    total_method_covered = 0
}
NR > 1 {  # Skip header
    total_instruction_missed += $4
    total_instruction_covered += $5
    total_branch_missed += $6
    total_branch_covered += $7
    total_line_missed += $8
    total_line_covered += $9
    total_method_missed += $12
    total_method_covered += $13
}
END {
    total_instructions = total_instruction_missed + total_instruction_covered
    total_branches = total_branch_missed + total_branch_covered
    total_lines = total_line_missed + total_line_covered
    total_methods = total_method_missed + total_method_covered
    
    instruction_coverage = (total_instructions > 0) ? (total_instruction_covered * 100 / total_instructions) : 0
    branch_coverage = (total_branches > 0) ? (total_branch_covered * 100 / total_branches) : 0
    line_coverage = (total_lines > 0) ? (total_line_covered * 100 / total_lines) : 0
    method_coverage = (total_methods > 0) ? (total_method_covered * 100 / total_methods) : 0
    
    printf "  📏 Instruction Coverage: %d/%d (%.1f%%)\n", total_instruction_covered, total_instructions, instruction_coverage
    printf "  🌿 Branch Coverage:      %d/%d (%.1f%%)\n", total_branch_covered, total_branches, branch_coverage
    printf "  📝 Line Coverage:        %d/%d (%.1f%%)\n", total_line_covered, total_lines, line_coverage
    printf "  🔧 Method Coverage:      %d/%d (%.1f%%)\n", total_method_covered, total_methods, method_coverage
    
    printf "\n"
    if (instruction_coverage >= 80) {
        printf "✅ Excellent coverage! (≥80%%)\n"
    } else if (instruction_coverage >= 60) {
        printf "🟡 Good coverage (60-79%%)\n"
    } else if (instruction_coverage >= 40) {
        printf "🟠 Fair coverage (40-59%%)\n"
    } else {
        printf "🔴 Low coverage (<40%%) - Consider adding more tests\n"
    }
}' target/site/jacoco/jacoco.csv

echo ""
echo "📋 Coverage by Package:"
echo "----------------------"

# Group by package and show coverage
awk -F',' '
NR > 1 {  # Skip header
    package = $2
    instruction_missed = $4
    instruction_covered = $5
    line_missed = $8
    line_covered = $9
    
    packages[package "_instruction_missed"] += instruction_missed
    packages[package "_instruction_covered"] += instruction_covered
    packages[package "_line_missed"] += line_missed
    packages[package "_line_covered"] += line_covered
}
END {
    for (key in packages) {
        if (index(key, "_instruction_missed") > 0) {
            package = substr(key, 1, index(key, "_instruction_missed") - 1)
            
            instruction_missed = packages[package "_instruction_missed"]
            instruction_covered = packages[package "_instruction_covered"]
            line_missed = packages[package "_line_missed"]
            line_covered = packages[package "_line_covered"]
            
            total_instructions = instruction_missed + instruction_covered
            total_lines = line_missed + line_covered
            
            instruction_coverage = (total_instructions > 0) ? (instruction_covered * 100 / total_instructions) : 0
            line_coverage = (total_lines > 0) ? (line_covered * 100 / total_lines) : 0
            
            printf "  %-35s Instructions: %.1f%% | Lines: %.1f%%\n", package, instruction_coverage, line_coverage
        }
    }
}' target/site/jacoco/jacoco.csv | sort

echo ""
echo "🎯 Coverage Thresholds:"
echo "----------------------"
echo "  • Current minimum: 50% (configured in pom.xml)"
echo "  • Recommended:     80% for production code"
echo "  • Excellent:       90%+ coverage"

echo ""
echo "📁 Report Locations:"
echo "-------------------"
echo "  • HTML Report:  target/site/jacoco/index.html"
echo "  • XML Report:   target/site/jacoco/jacoco.xml"
echo "  • CSV Report:   target/site/jacoco/jacoco.csv"

echo ""
echo "🚀 Quick Actions:"
echo "----------------"
echo "  ./view-coverage.sh        # Open HTML report in browser"
echo "  ./run-coverage.sh         # Run tests and generate reports"
echo "  ./mvnw test jacoco:report # Generate coverage after tests"

echo ""
