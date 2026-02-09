#!/usr/bin/env python3
"""
Analyze MaxSum vs P-MaxSum comparison test results.

This script:
1. Parses all CSV result files from a test run
2. Groups by algorithm and lastRound
3. Computes average costs and runtimes
4. Generates a comparison summary and chart

Usage:
    python scripts/analyze_results.py <results_directory>
    
Example:
    python scripts/analyze_results.py results/maxsum_comparison_20260123_143022
"""

import os
import sys
import csv
import re
from collections import defaultdict
from typing import Dict, List, Tuple

def parse_csv_file(filepath: str) -> List[dict]:
    """Parse a single CSV result file."""
    results = []
    with open(filepath, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            results.append({
                'iteration': int(row['Iteration']),
                'runtime_ms': int(row['Runtime_ms']),
                'total_cost': int(row['TotalCost']),
                'max_rounds': int(row['MaxRounds']) if row['MaxRounds'] != '-1' else None
            })
    return results

def extract_metadata_from_filename(filename: str) -> Tuple[str, int]:
    """Extract algorithm and round number from filename.
    
    Filename format: dcop_results_ALGO_TIMESTAMP_roundN.csv
    """
    # Match pattern like "dcop_results_MAXSUM_20260123_143022_round5.csv"
    match = re.search(r'dcop_results_(\w+)_\d+_\d+_round(\d+)\.csv', filename)
    if match:
        algorithm = match.group(1)
        round_num = int(match.group(2))
        return algorithm, round_num
    return None, None

def analyze_results_directory(results_dir: str) -> Dict:
    """Analyze all CSV files in a results directory."""
    # Structure: {algorithm: {round: [results]}}
    data = defaultdict(lambda: defaultdict(list))
    
    for filename in os.listdir(results_dir):
        if not filename.endswith('.csv'):
            continue
            
        filepath = os.path.join(results_dir, filename)
        algorithm, round_num = extract_metadata_from_filename(filename)
        
        if algorithm is None:
            print(f"  Skipping unrecognized file: {filename}")
            continue
            
        results = parse_csv_file(filepath)
        data[algorithm][round_num].extend(results)
        
    return data

def compute_statistics(data: Dict) -> Dict:
    """Compute average cost and runtime for each algorithm/round combination."""
    stats = defaultdict(dict)
    
    for algorithm, rounds in data.items():
        for round_num, results in rounds.items():
            if not results:
                continue
                
            costs = [r['total_cost'] for r in results]
            runtimes = [r['runtime_ms'] for r in results]
            
            stats[algorithm][round_num] = {
                'avg_cost': sum(costs) / len(costs),
                'min_cost': min(costs),
                'max_cost': max(costs),
                'avg_runtime_ms': sum(runtimes) / len(runtimes),
                'num_samples': len(results)
            }
    
    return stats

def generate_summary_csv(stats: Dict, output_path: str):
    """Generate a CSV summary of results."""
    with open(output_path, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['Algorithm', 'LastRound', 'AvgCost', 'MinCost', 'MaxCost', 'AvgRuntimeMs', 'NumSamples'])
        
        for algorithm in sorted(stats.keys()):
            for round_num in sorted(stats[algorithm].keys()):
                s = stats[algorithm][round_num]
                writer.writerow([
                    algorithm,
                    round_num,
                    f"{s['avg_cost']:.2f}",
                    s['min_cost'],
                    s['max_cost'],
                    f"{s['avg_runtime_ms']:.2f}",
                    s['num_samples']
                ])
    
    print(f"Summary CSV saved to: {output_path}")

def print_comparison_table(stats: Dict):
    """Print a side-by-side comparison table."""
    algorithms = sorted(stats.keys())
    
    if len(algorithms) < 2:
        print("\nOnly one algorithm found, cannot compare.")
        return
    
    # Get all rounds that exist in both algorithms
    all_rounds = set()
    for algo in algorithms:
        all_rounds.update(stats[algo].keys())
    all_rounds = sorted(all_rounds)
    
    print("\n" + "=" * 80)
    print("COMPARISON: MAXSUM vs P-MAXSUM")
    print("=" * 80)
    
    # Header
    header = f"{'Round':>6}"
    for algo in algorithms:
        header += f" | {algo:>12} Cost | {algo:>12} Time"
    print(header)
    print("-" * 80)
    
    # Data rows
    for round_num in all_rounds:
        row = f"{round_num:>6}"
        for algo in algorithms:
            if round_num in stats[algo]:
                s = stats[algo][round_num]
                row += f" | {s['avg_cost']:>12.1f} | {s['avg_runtime_ms']:>12.0f}ms"
            else:
                row += f" | {'N/A':>12} | {'N/A':>12}"
        print(row)
    
    print("=" * 80)
    
    # Summary comparison
    if 'MAXSUM' in stats and 'PMAXSUM' in stats:
        print("\nSUMMARY:")
        common_rounds = set(stats['MAXSUM'].keys()) & set(stats['PMAXSUM'].keys())
        if common_rounds:
            maxsum_avg = sum(stats['MAXSUM'][r]['avg_cost'] for r in common_rounds) / len(common_rounds)
            pmaxsum_avg = sum(stats['PMAXSUM'][r]['avg_cost'] for r in common_rounds) / len(common_rounds)
            
            maxsum_time = sum(stats['MAXSUM'][r]['avg_runtime_ms'] for r in common_rounds) / len(common_rounds)
            pmaxsum_time = sum(stats['PMAXSUM'][r]['avg_runtime_ms'] for r in common_rounds) / len(common_rounds)
            
            print(f"  MAXSUM  - Overall avg cost: {maxsum_avg:.1f}, avg runtime: {maxsum_time:.0f}ms")
            print(f"  PMAXSUM - Overall avg cost: {pmaxsum_avg:.1f}, avg runtime: {pmaxsum_time:.0f}ms")
            
            if pmaxsum_avg > 0:
                cost_diff = ((pmaxsum_avg - maxsum_avg) / maxsum_avg) * 100
                print(f"  Cost difference: {cost_diff:+.1f}% (positive = PMAXSUM higher)")
            
            if pmaxsum_time > 0:
                time_ratio = pmaxsum_time / maxsum_time
                print(f"  Runtime ratio: {time_ratio:.1f}x (PMAXSUM / MAXSUM)")

def main():
    if len(sys.argv) < 2:
        print("Usage: python scripts/analyze_results.py <results_directory>")
        print("")
        print("Example:")
        print("  python scripts/analyze_results.py results/maxsum_comparison_20260123_143022")
        sys.exit(1)
    
    results_dir = sys.argv[1]
    
    if not os.path.isdir(results_dir):
        print(f"Error: Directory not found: {results_dir}")
        sys.exit(1)
    
    print(f"Analyzing results in: {results_dir}")
    print("")
    
    # Parse all CSV files
    print("Parsing CSV files...")
    data = analyze_results_directory(results_dir)
    
    if not data:
        print("No valid result files found.")
        sys.exit(1)
    
    # Report what was found
    for algo, rounds in data.items():
        total_samples = sum(len(r) for r in rounds.values())
        print(f"  {algo}: {len(rounds)} rounds, {total_samples} total samples")
    
    # Compute statistics
    print("\nComputing statistics...")
    stats = compute_statistics(data)
    
    # Generate summary CSV
    summary_path = os.path.join(results_dir, "summary.csv")
    generate_summary_csv(stats, summary_path)
    
    # Print comparison table
    print_comparison_table(stats)
    
    print("\nAnalysis complete!")

if __name__ == "__main__":
    main()
