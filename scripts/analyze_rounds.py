#!/usr/bin/env python3
"""
Analyze round completion status across all configurations.
"""

import sys
import os
import csv
import re
from collections import defaultdict


def parse_filename(filename):
    """Extract configuration from filename."""
    pattern = r'test_comparison_\d+_(\w+)_(RANDOM|SCALE_FREE)_t(\d+)_n(\d+)_results\.csv'
    match = re.match(pattern, filename)
    if match:
        return {
            'algorithm': match.group(1),
            'network': match.group(2),
            'timeout': int(match.group(3)),
            'agents': int(match.group(4))
        }
    return None


def read_rounds(filepath):
    """Read rounds from a CSV file."""
    rounds = []
    
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    in_results = False
    header = None
    
    for line in lines:
        line = line.strip()
        if line == '# Results':
            in_results = True
            continue
        if in_results and header is None:
            header = line.split(',')
            continue
        if in_results and line and not line.startswith('#'):
            parts = line.split(',')
            if len(parts) >= 4:
                try:
                    rounds_completed = int(parts[3])
                    rounds.append(rounds_completed)
                except (ValueError, IndexError):
                    pass
    
    return rounds


def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_rounds.py <results_directory>")
        sys.exit(1)
    
    results_dir = sys.argv[1]
    
    # Collect all results
    all_results = {}
    
    for filename in os.listdir(results_dir):
        if not filename.endswith('_results.csv'):
            continue
        
        config = parse_filename(filename)
        if not config:
            continue
        
        filepath = os.path.join(results_dir, filename)
        rounds = read_rounds(filepath)
        
        if rounds:
            key = (config['algorithm'], config['network'], config['timeout'], config['agents'])
            all_results[key] = rounds
    
    # Print analysis
    print(f"{'='*100}")
    print("Round Completion Analysis")
    print(f"{'='*100}")
    
    for network in ['RANDOM', 'SCALE_FREE']:
        print(f"\n{'='*100}")
        print(f"Network: {network}")
        print(f"{'='*100}")
        
        for algo in ['PDSA', 'PMGM']:
            print(f"\n--- {algo} ---")
            print(f"{'Timeout':>8} | {'Agents':>8} | {'Min':>6} | {'Max':>6} | {'Avg':>8} | {'Zero%':>8} | {'Status':<20}")
            print(f"{'-'*8}-+-{'-'*8}-+-{'-'*6}-+-{'-'*6}-+-{'-'*8}-+-{'-'*8}-+-{'-'*20}")
            
            for timeout in [60, 120, 180]:
                for agents in [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]:
                    key = (algo, network, timeout, agents)
                    
                    if key not in all_results:
                        print(f"{timeout:>8} | {agents:>8} | {'N/A':>6} | {'N/A':>6} | {'N/A':>8} | {'N/A':>8} | {'MISSING':<20}")
                        continue
                    
                    rounds = all_results[key]
                    min_r = min(rounds)
                    max_r = max(rounds)
                    avg_r = sum(rounds) / len(rounds)
                    zero_count = sum(1 for r in rounds if r == 0)
                    zero_pct = zero_count / len(rounds) * 100
                    
                    if zero_count == len(rounds):
                        status = "ALL ZERO (timeout)"
                    elif zero_count > 0:
                        status = f"MIXED ({zero_count}/50 zero)"
                    elif max_r == min_r:
                        status = f"ALL ROUND {max_r}"
                    else:
                        status = f"CONVERGED"
                    
                    print(f"{timeout:>8} | {agents:>8} | {min_r:>6} | {max_r:>6} | {avg_r:>8.1f} | {zero_pct:>7.0f}% | {status:<20}")
    
    # Summary of problematic configs
    print(f"\n{'='*100}")
    print("PROBLEMATIC CONFIGURATIONS (any problems with 0 rounds)")
    print(f"{'='*100}")
    
    problem_configs = []
    for key, rounds in all_results.items():
        algo, network, timeout, agents = key
        zero_count = sum(1 for r in rounds if r == 0)
        if zero_count > 0:
            problem_configs.append((algo, network, timeout, agents, zero_count, len(rounds)))
    
    problem_configs.sort(key=lambda x: (x[0], x[1], x[2], x[3]))
    
    for algo, network, timeout, agents, zeros, total in problem_configs:
        print(f"  {algo:5} | {network:12} | timeout={timeout:3}s | agents={agents:3} | {zeros}/{total} problems with 0 rounds")
    
    print(f"\nTotal problematic configs: {len(problem_configs)}")


if __name__ == '__main__':
    main()
