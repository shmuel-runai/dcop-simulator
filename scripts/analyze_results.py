#!/usr/bin/env python3
"""
Analyze DCOP comparison results and generate summary tables.
"""

import os
import sys
import csv
import re
from collections import defaultdict
from pathlib import Path

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
    data = []
    
    for filepath in Path(results_dir).glob('*_results.csv'):
        config, results = parse_result_file(filepath)
        
        if not results:
            continue
        
        # Extract info from filename as backup
        filename = filepath.name
        match = re.search(r'_(PDSA|PMGM)_(RANDOM|SCALE_FREE)_t(\d+)_n(\d+)_', filename)
        
        algo = config.get('algorithm', match.group(1) if match else 'UNKNOWN')
        network = config.get('network_type', match.group(2) if match else 'UNKNOWN')
        timeout = int(config.get('timeout_sec', match.group(3) if match else 0))
        agents = int(config.get('num_agents', match.group(4) if match else 0))
        
        # Calculate statistics
        costs = [r['final_cost'] for r in results]
        rounds = [r['rounds'] for r in results]
        zero_rounds = sum(1 for r in rounds if r == 0)
        
        data.append({
            'algorithm': algo,
            'network': network,
            'timeout': timeout,
            'agents': agents,
            'num_problems': len(results),
            'avg_cost': sum(costs) / len(costs),
            'min_cost': min(costs),
            'max_cost': max(costs),
            'avg_rounds': sum(rounds) / len(rounds),
            'min_rounds': min(rounds),
            'max_rounds': max(rounds),
            'zero_round_count': zero_rounds,
            'zero_round_pct': 100 * zero_rounds / len(results)
        })
    
    return data

def print_zero_round_analysis(data):
    """Print analysis of configurations with zero rounds."""
    print("\n" + "="*80)
    print("CONFIGURATIONS WITH ZERO-ROUND COMPLETIONS")
    print("="*80)
    
    zero_configs = [d for d in data if d['zero_round_count'] > 0]
    
    if not zero_configs:
        print("No configurations had zero-round completions.")
        return
    
    # Sort by algorithm, then agents
    zero_configs.sort(key=lambda x: (x['algorithm'], x['network'], x['agents'], x['timeout']))
    
    print(f"\n{'Algorithm':<8} {'Network':<12} {'Timeout':>7} {'Agents':>6} {'Zero-Round':>10} {'Pct':>6}")
    print("-"*60)
    
    for d in zero_configs:
        print(f"{d['algorithm']:<8} {d['network']:<12} {d['timeout']:>7}s {d['agents']:>6} "
              f"{d['zero_round_count']:>10} {d['zero_round_pct']:>5.0f}%")
    
    print(f"\nTotal configurations affected: {len(zero_configs)} / {len(data)}")

def print_comparison_table(data):
    """Print comparison table between algorithms."""
    print("\n" + "="*100)
    print("ALGORITHM COMPARISON TABLE")
    print("="*100)
    
    # Check which algorithms are present
    all_algos = set(d['algorithm'] for d in data)
    has_pmaxsum = 'PMAXSUM' in all_algos
    
    # Group by network, timeout, agents
    grouped = defaultdict(dict)
    for d in data:
        key = (d['network'], d['timeout'], d['agents'])
        grouped[key][d['algorithm']] = d
    
    # Print header
    if has_pmaxsum:
        print(f"\n{'Network':<12} {'Timeout':>7} {'Agents':>6} | "
              f"{'PDSA Cost':>10} {'Rnds':>5} | {'PMGM Cost':>10} {'Rnds':>5} | "
              f"{'PMAXSUM Cost':>12} {'Rnds':>5} | {'Winner':>8}")
        print("-"*110)
    else:
        print(f"\n{'Network':<12} {'Timeout':>7} {'Agents':>6} | "
              f"{'PDSA Cost':>10} {'Rounds':>7} | {'PMGM Cost':>10} {'Rounds':>7} | {'Winner':>8}")
        print("-"*85)
    
    wins = defaultdict(int)
    
    for key in sorted(grouped.keys()):
        network, timeout, agents = key
        algos = grouped[key]
        
        pdsa = algos.get('PDSA', {})
        pmgm = algos.get('PMGM', {})
        pmaxsum = algos.get('PMAXSUM', {})
        
        pdsa_cost = pdsa.get('avg_cost', float('nan'))
        pdsa_rounds = pdsa.get('avg_rounds', float('nan'))
        pmgm_cost = pmgm.get('avg_cost', float('nan'))
        pmgm_rounds = pmgm.get('avg_rounds', float('nan'))
        pmaxsum_cost = pmaxsum.get('avg_cost', float('nan'))
        pmaxsum_rounds = pmaxsum.get('avg_rounds', float('nan'))
        
        # Determine winner (lowest cost)
        costs = {'PDSA': pdsa_cost, 'PMGM': pmgm_cost}
        if has_pmaxsum:
            costs['PMAXSUM'] = pmaxsum_cost
        
        valid_costs = {k: v for k, v in costs.items() if not (v != v)}  # filter NaN
        if valid_costs:
            min_cost = min(valid_costs.values())
            winners = [k for k, v in valid_costs.items() if v == min_cost]
            if len(winners) == 1:
                winner = winners[0]
                wins[winner] += 1
            else:
                winner = "TIE"
                wins['TIE'] += 1
        else:
            winner = "N/A"
        
        # Mark zero-round issues
        pdsa_marker = "*" if pdsa.get('zero_round_count', 0) > 0 else " "
        pmgm_marker = "*" if pmgm.get('zero_round_count', 0) > 0 else " "
        pmaxsum_marker = "*" if pmaxsum.get('zero_round_count', 0) > 0 else " "
        
        if has_pmaxsum:
            pmaxsum_cost_str = f"{pmaxsum_cost:>11.1f}{pmaxsum_marker}" if pmaxsum else "         N/A "
            pmaxsum_rounds_str = f"{pmaxsum_rounds:>5.1f}" if pmaxsum else "  N/A"
            print(f"{network:<12} {timeout:>7}s {agents:>6} | "
                  f"{pdsa_cost:>9.1f}{pdsa_marker} {pdsa_rounds:>5.1f} | "
                  f"{pmgm_cost:>9.1f}{pmgm_marker} {pmgm_rounds:>5.1f} | "
                  f"{pmaxsum_cost_str} {pmaxsum_rounds_str} | {winner:>8}")
        else:
            print(f"{network:<12} {timeout:>7}s {agents:>6} | "
                  f"{pdsa_cost:>9.1f}{pdsa_marker} {pdsa_rounds:>7.1f} | "
                  f"{pmgm_cost:>9.1f}{pmgm_marker} {pmgm_rounds:>7.1f} | {winner:>8}")
    
    print("-"*110 if has_pmaxsum else "-"*85)
    print(f"* = has zero-round completions")
    winner_str = ", ".join(f"{k}={v}" for k, v in sorted(wins.items()))
    print(f"\nWinner summary: {winner_str}")

def print_summary_by_agents(data):
    """Print summary grouped by agent count."""
    print("\n" + "="*110)
    print("SUMMARY BY AGENT COUNT (averaged across networks and timeouts)")
    print("="*110)
    
    # Check which algorithms are present
    all_algos = set(d['algorithm'] for d in data)
    has_pmaxsum = 'PMAXSUM' in all_algos
    
    # Group by algorithm and agents
    grouped = defaultdict(list)
    for d in data:
        grouped[(d['algorithm'], d['agents'])].append(d)
    
    # Calculate averages
    summary = []
    for (algo, agents), items in grouped.items():
        avg_cost = sum(d['avg_cost'] for d in items) / len(items)
        avg_rounds = sum(d['avg_rounds'] for d in items) / len(items)
        total_zero = sum(d['zero_round_count'] for d in items)
        total_problems = sum(d['num_problems'] for d in items)
        
        summary.append({
            'algorithm': algo,
            'agents': agents,
            'avg_cost': avg_cost,
            'avg_rounds': avg_rounds,
            'zero_round_pct': 100 * total_zero / total_problems if total_problems > 0 else 0
        })
    
    summary.sort(key=lambda x: (x['agents'], x['algorithm']))
    
    if has_pmaxsum:
        print(f"\n{'Agents':>6} | {'PDSA Cost':>10} {'Rnds':>6} {'0R%':>5} | "
              f"{'PMGM Cost':>10} {'Rnds':>6} {'0R%':>5} | "
              f"{'PMAXSUM Cost':>12} {'Rnds':>6} {'0R%':>5}")
        print("-"*105)
    else:
        print(f"\n{'Agents':>6} | {'PDSA Cost':>10} {'Rounds':>8} {'0-Rnd%':>7} | "
              f"{'PMGM Cost':>10} {'Rounds':>8} {'0-Rnd%':>7}")
        print("-"*75)
    
    agents_list = sorted(set(d['agents'] for d in summary))
    for agents in agents_list:
        pdsa = next((d for d in summary if d['algorithm'] == 'PDSA' and d['agents'] == agents), None)
        pmgm = next((d for d in summary if d['algorithm'] == 'PMGM' and d['agents'] == agents), None)
        pmaxsum = next((d for d in summary if d['algorithm'] == 'PMAXSUM' and d['agents'] == agents), None)
        
        pdsa_cost = pdsa['avg_cost'] if pdsa else float('nan')
        pdsa_rounds = pdsa['avg_rounds'] if pdsa else float('nan')
        pdsa_zero = pdsa['zero_round_pct'] if pdsa else 0
        
        pmgm_cost = pmgm['avg_cost'] if pmgm else float('nan')
        pmgm_rounds = pmgm['avg_rounds'] if pmgm else float('nan')
        pmgm_zero = pmgm['zero_round_pct'] if pmgm else 0
        
        pmaxsum_cost = pmaxsum['avg_cost'] if pmaxsum else float('nan')
        pmaxsum_rounds = pmaxsum['avg_rounds'] if pmaxsum else float('nan')
        pmaxsum_zero = pmaxsum['zero_round_pct'] if pmaxsum else 0
        
        if has_pmaxsum:
            pmaxsum_str = f"{pmaxsum_cost:>12.1f} {pmaxsum_rounds:>6.1f} {pmaxsum_zero:>4.0f}%" if pmaxsum else "         N/A    N/A   N/A"
            print(f"{agents:>6} | {pdsa_cost:>10.1f} {pdsa_rounds:>6.1f} {pdsa_zero:>4.0f}% | "
                  f"{pmgm_cost:>10.1f} {pmgm_rounds:>6.1f} {pmgm_zero:>4.0f}% | {pmaxsum_str}")
        else:
            print(f"{agents:>6} | {pdsa_cost:>10.1f} {pdsa_rounds:>8.1f} {pdsa_zero:>6.0f}% | "
                  f"{pmgm_cost:>10.1f} {pmgm_rounds:>8.1f} {pmgm_zero:>6.0f}%")

def main():
    if len(sys.argv) < 2:
        results_dir = "results/2026_10_02-2127295"
    else:
        results_dir = sys.argv[1]
    
    print(f"Analyzing results from: {results_dir}")
    
    data = analyze_directory(results_dir)
    
    if not data:
        print("No result files found!")
        return 1
    
    print(f"Found {len(data)} result configurations")
    
    print_zero_round_analysis(data)
    print_comparison_table(data)
    print_summary_by_agents(data)
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
