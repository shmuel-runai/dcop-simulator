#!/usr/bin/env python3
"""
Analyze DCOP test results and compare PDSA vs PMGM.

Usage:
    python scripts/analyze_results.py <results_directory>
"""

import sys
import os
import csv
import re
from collections import defaultdict


def parse_filename(filename):
    """Extract configuration from filename."""
    # Pattern: test_comparison_ID_ALGO_NETWORK_tTIMEOUT_nAGENTS_results.csv
    # Network can be RANDOM or SCALE_FREE (with underscore)
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


def read_results(filepath):
    """Read results from a CSV file and compute averages."""
    costs = []
    runtimes = []
    
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    # Find the results section (after "# Results")
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
                    cost = int(parts[2])  # final_cost
                    runtime = int(parts[4])  # runtime_ms
                    costs.append(cost)
                    runtimes.append(runtime)
                except (ValueError, IndexError):
                    pass
    
    if not costs:
        return None
    
    return {
        'avg_cost': sum(costs) / len(costs),
        'min_cost': min(costs),
        'max_cost': max(costs),
        'avg_runtime_ms': sum(runtimes) / len(runtimes),
        'count': len(costs)
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_results.py <results_directory>")
        sys.exit(1)
    
    results_dir = sys.argv[1]
    
    # Collect all results
    all_results = {}  # (network, timeout, agents) -> {algo: stats}
    
    for filename in os.listdir(results_dir):
        if not filename.endswith('_results.csv'):
            continue
        
        config = parse_filename(filename)
        if not config:
            continue
        
        filepath = os.path.join(results_dir, filename)
        stats = read_results(filepath)
        
        if stats:
            key = (config['network'], config['timeout'], config['agents'])
            if key not in all_results:
                all_results[key] = {}
            all_results[key][config['algorithm']] = stats
    
    # Print comparison tables
    for network in ['RANDOM', 'SCALE_FREE']:
        print(f"\n{'='*80}")
        print(f"Network Type: {network}")
        print(f"{'='*80}")
        
        for timeout in [60, 120, 180]:
            print(f"\n--- Timeout: {timeout}s ---")
            print(f"{'Agents':>8} | {'PDSA Avg Cost':>14} | {'PMGM Avg Cost':>14} | {'Diff':>10} | {'PDSA Wins':>10}")
            print(f"{'-'*8}-+-{'-'*14}-+-{'-'*14}-+-{'-'*10}-+-{'-'*10}")
            
            pdsa_wins = 0
            pmgm_wins = 0
            ties = 0
            
            for agents in [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]:
                key = (network, timeout, agents)
                
                if key not in all_results:
                    print(f"{agents:>8} | {'N/A':>14} | {'N/A':>14} | {'N/A':>10} | {'N/A':>10}")
                    continue
                
                data = all_results[key]
                pdsa = data.get('PDSA', {}).get('avg_cost', float('nan'))
                pmgm = data.get('PMGM', {}).get('avg_cost', float('nan'))
                
                if pdsa != pdsa or pmgm != pmgm:  # NaN check
                    print(f"{agents:>8} | {'N/A':>14} | {'N/A':>14} | {'N/A':>10} | {'N/A':>10}")
                    continue
                
                diff = pmgm - pdsa
                if abs(diff) < 0.01:
                    winner = "TIE"
                    ties += 1
                elif pdsa < pmgm:
                    winner = "PDSA"
                    pdsa_wins += 1
                else:
                    winner = "PMGM"
                    pmgm_wins += 1
                
                print(f"{agents:>8} | {pdsa:>14.2f} | {pmgm:>14.2f} | {diff:>+10.2f} | {winner:>10}")
            
            print(f"\nSummary: PDSA wins {pdsa_wins}, PMGM wins {pmgm_wins}, Ties {ties}")
    
    # Overall summary
    print(f"\n{'='*80}")
    print("OVERALL SUMMARY")
    print(f"{'='*80}")
    
    pdsa_total = 0
    pmgm_total = 0
    pdsa_count = 0
    pmgm_count = 0
    
    for key, data in all_results.items():
        if 'PDSA' in data:
            pdsa_total += data['PDSA']['avg_cost']
            pdsa_count += 1
        if 'PMGM' in data:
            pmgm_total += data['PMGM']['avg_cost']
            pmgm_count += 1
    
    if pdsa_count > 0 and pmgm_count > 0:
        pdsa_overall = pdsa_total / pdsa_count
        pmgm_overall = pmgm_total / pmgm_count
        print(f"\nAverage cost across all configurations:")
        print(f"  PDSA: {pdsa_overall:.2f}")
        print(f"  PMGM: {pmgm_overall:.2f}")
        print(f"  Difference: {pmgm_overall - pdsa_overall:+.2f}")
        
        if pdsa_overall < pmgm_overall:
            print(f"\n  PDSA achieves {((pmgm_overall - pdsa_overall) / pmgm_overall * 100):.1f}% lower cost overall")
        else:
            print(f"\n  PMGM achieves {((pdsa_overall - pmgm_overall) / pdsa_overall * 100):.1f}% lower cost overall")


if __name__ == '__main__':
    main()
