#!/usr/bin/env python3
"""
Verify Problem Consistency Across Test Runs

This script compares DCOP problem export files to ensure that tests using
the same seed produce identical problem structures.

Usage:
    python scripts/verify_problems.py file1.csv file2.csv [file3.csv ...]
    python scripts/verify_problems.py results/test_*_problems.csv

Output:
    - Reports whether all files contain identical problems
    - Lists any differences found (by problem ID and seed)
"""

import sys
import csv
import hashlib
from collections import defaultdict


def read_problems(filename):
    """
    Read problems from a CSV file.
    Returns a dict mapping (problem_id, seed) -> problem data dict
    """
    problems = {}
    
    try:
        with open(filename, 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                problem_id = int(row['problem_id'])
                seed = int(row['seed'])
                key = (problem_id, seed)
                problems[key] = {
                    'num_agents': row['num_agents'],
                    'domain_size': row['domain_size'],
                    'num_edges': row['num_edges'],
                    'edges': row['edges'],
                    'cost_matrices': row['cost_matrices']
                }
    except FileNotFoundError:
        print(f"Error: File not found: {filename}")
        sys.exit(1)
    except KeyError as e:
        print(f"Error: Missing expected column in {filename}: {e}")
        sys.exit(1)
    
    return problems


def hash_problem(problem_data):
    """Generate a hash of problem data for comparison."""
    content = (
        problem_data['num_agents'] +
        problem_data['domain_size'] +
        problem_data['num_edges'] +
        problem_data['edges'] +
        problem_data['cost_matrices']
    )
    return hashlib.md5(content.encode()).hexdigest()


def compare_problems(file1, file2):
    """
    Compare problems between two files.
    Returns list of differences.
    """
    problems1 = read_problems(file1)
    problems2 = read_problems(file2)
    
    differences = []
    all_keys = set(problems1.keys()) | set(problems2.keys())
    
    for key in sorted(all_keys):
        problem_id, seed = key
        
        if key not in problems1:
            differences.append({
                'problem_id': problem_id,
                'seed': seed,
                'type': 'missing_in_file1',
                'file1': file1,
                'file2': file2
            })
        elif key not in problems2:
            differences.append({
                'problem_id': problem_id,
                'seed': seed,
                'type': 'missing_in_file2',
                'file1': file1,
                'file2': file2
            })
        else:
            hash1 = hash_problem(problems1[key])
            hash2 = hash_problem(problems2[key])
            if hash1 != hash2:
                differences.append({
                    'problem_id': problem_id,
                    'seed': seed,
                    'type': 'content_mismatch',
                    'file1': file1,
                    'file2': file2,
                    'details': find_differences(problems1[key], problems2[key])
                })
    
    return differences


def find_differences(prob1, prob2):
    """Find specific differences between two problem data dicts."""
    diffs = []
    for field in ['num_agents', 'domain_size', 'num_edges', 'edges', 'cost_matrices']:
        if prob1[field] != prob2[field]:
            diffs.append(field)
    return diffs


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    
    if sys.argv[1] in ['-h', '--help']:
        print(__doc__)
        sys.exit(0)
    
    files = sys.argv[1:]
    
    if len(files) < 2:
        print("Error: Need at least 2 files to compare")
        print("Usage: python verify_problems.py file1.csv file2.csv ...")
        sys.exit(1)
    
    print("=" * 60)
    print("DCOP Problem Verification")
    print("=" * 60)
    print(f"\nComparing {len(files)} files:\n")
    for f in files:
        print(f"  - {f}")
    print()
    
    # Compare all pairs of files
    all_differences = []
    comparison_count = 0
    
    for i, file1 in enumerate(files):
        for file2 in files[i+1:]:
            comparison_count += 1
            differences = compare_problems(file1, file2)
            if differences:
                all_differences.extend(differences)
    
    # Report results
    print("-" * 60)
    
    if not all_differences:
        print("\n✓ SUCCESS: All files contain identical problems!\n")
        print(f"  Compared {comparison_count} file pairs")
        
        # Print summary of first file
        problems = read_problems(files[0])
        print(f"  Total problems: {len(problems)}")
        if problems:
            sample_key = next(iter(problems.keys()))
            sample = problems[sample_key]
            print(f"  Sample problem ({sample_key[0]}, seed={sample_key[1]}):")
            print(f"    - Agents: {sample['num_agents']}")
            print(f"    - Domain size: {sample['domain_size']}")
            print(f"    - Edges: {sample['num_edges']}")
        print()
        sys.exit(0)
    else:
        print(f"\n✗ FAILURE: Found {len(all_differences)} differences!\n")
        
        # Group differences by type
        by_type = defaultdict(list)
        for diff in all_differences:
            by_type[diff['type']].append(diff)
        
        for diff_type, diffs in by_type.items():
            print(f"\n{diff_type.upper().replace('_', ' ')} ({len(diffs)} occurrences):")
            for diff in diffs[:10]:  # Show first 10
                print(f"  Problem {diff['problem_id']} (seed={diff['seed']})")
                print(f"    File 1: {diff['file1']}")
                print(f"    File 2: {diff['file2']}")
                if 'details' in diff:
                    print(f"    Differing fields: {', '.join(diff['details'])}")
            if len(diffs) > 10:
                print(f"  ... and {len(diffs) - 10} more")
        
        print()
        sys.exit(1)


if __name__ == '__main__':
    main()
