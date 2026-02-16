#!/usr/bin/env python3
"""
Generate comparison tables for DCOP results.
"""

import sys
from pathlib import Path
from collections import defaultdict
import csv

def parse_result_file(filepath):
    """Parse a single result CSV file."""
    results = []
    config = {}
    
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    # Parse configuration (line 3)
    if len(lines) >= 3:
        config_header = lines[1].strip().split(',')
        config_values = lines[2].strip().split(',')
        for i, key in enumerate(config_header):
            if i < len(config_values):
                config[key] = config_values[i]
    
    # Parse results (starting from line 7)
    for line in lines[6:]:
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        parts = line.split(',')
        if len(parts) >= 5:
            try:
                results.append({
                    'problem_id': int(parts[0]),
                    'seed': int(parts[1]),
                    'final_cost': int(parts[2]),
                    'rounds': int(parts[3]),
                    'runtime_ms': int(parts[4])
                })
            except ValueError:
                continue
    
    return config, results

def analyze_directory(results_dir):
    """Analyze all result files in a directory."""
    data = {}
    
    for filepath in Path(results_dir).glob('*_results.csv'):
        config, results = parse_result_file(filepath)
        
        if not results:
            continue
        
        algo = config.get('algorithm', 'UNKNOWN')
        network = config.get('network_type', 'UNKNOWN')
        timeout = int(config.get('timeout_sec', 0))
        agents = int(config.get('num_agents', 0))
        
        # Calculate statistics
        costs = [r['final_cost'] for r in results]
        rounds = [r['rounds'] for r in results]
        
        key = (network, timeout, agents, algo)
        data[key] = {
            'avg_cost': sum(costs) / len(costs),
            'avg_rounds': sum(rounds) / len(rounds),
        }
    
    return data

def print_table(data, network):
    """Print formatted table for a network type."""
    timeouts = [60, 120, 180]
    agents_list = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    algos = ['PDSA', 'PMGM', 'PMAXSUM']
    
    print(f"\n{'='*140}")
    print(f"{network} NETWORK - Cost (Rounds)")
    print(f"{'='*140}")
    
    # Header
    header = f"{'Timeout':<8}"
    for agents in agents_list:
        header += f" | {agents:^12}"
    print(header)
    print("-" * 140)
    
    for timeout in timeouts:
        timeout_label = f"{timeout//60}min"
        
        for algo in algos:
            if algo == 'PDSA':
                row = f"{timeout_label:<8}"
            else:
                row = f"{'':8}"
            
            for agents in agents_list:
                key = (network, timeout, agents, algo)
                if key in data:
                    cost = data[key]['avg_cost']
                    rounds = data[key]['avg_rounds']
                    cell = f"{cost:.0f}({rounds:.0f})"
                else:
                    cell = "N/A"
                row += f" | {cell:^12}"
            
            print(f"{row}  {algo}")
        
        print("-" * 140)

def main():
    if len(sys.argv) < 2:
        print("Usage: python generate_table.py <results_dir>")
        return 1
    
    results_dir = sys.argv[1]
    print(f"Analyzing: {results_dir}")
    
    data = analyze_directory(results_dir)
    
    print_table(data, 'RANDOM')
    print_table(data, 'SCALE_FREE')
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
