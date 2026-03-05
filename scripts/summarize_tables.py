#!/usr/bin/env python3
"""Generate structured summary tables for DCOP test results."""

import os
import csv
import sys
from collections import defaultdict


GRACE_MULTIPLIER = 2.0


def parse_result_file(filepath):
    """Parse a result CSV, applying a grace period to PMAXSUM round counts.

    If a problem's runtime exceeds GRACE_MULTIPLIER * timeout, the reported
    rounds are reduced by 1 (floored at 0).  This compensates for the fact
    that the timeout is only checked between Sinalgo rounds, so heavy rounds
    can overshoot the timeout significantly and inflate the round count.
    """
    results = []
    timeout_ms = None
    in_config = False
    in_results = False
    config_header = None
    is_pmaxsum = False

    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if line == '# Configuration':
                in_config = True
                continue
            if in_config and not line.startswith('#') and line:
                if config_header is None:
                    config_header = line.split(',')
                    continue
                vals = line.split(',')
                cfg = dict(zip(config_header, vals))
                try:
                    timeout_ms = int(cfg.get('timeout_sec', '0')) * 1000
                except ValueError:
                    timeout_ms = None
                is_pmaxsum = cfg.get('algorithm', '') == 'PMAXSUM'
                in_config = False
                continue
            if line == '# Results':
                in_results = True
                continue
            if in_results and line and not line.startswith('#') and not line.startswith('problem_id'):
                parts = line.split(',')
                if len(parts) >= 5:
                    try:
                        cost = float(parts[2])
                        rounds = float(parts[3])
                        runtime = float(parts[4])

                        if is_pmaxsum and timeout_ms and runtime > timeout_ms * GRACE_MULTIPLIER:
                            rounds = max(0, rounds - 1)

                        results.append((cost, rounds))
                    except ValueError:
                        continue
    return results


def load_agents_data(results_dir):
    data = {}
    for fname in os.listdir(results_dir):
        if not fname.endswith('_results.csv'):
            continue
        name = fname.replace('test_', '').replace('_results.csv', '')
        parts = name.split('_')
        if 'SCALE' in fname:
            algo, net = parts[0], 'SCALE_FREE'
            timeout, agents = int(parts[3][1:]), int(parts[4][1:])
        else:
            algo, net = parts[0], parts[1]
            timeout, agents = int(parts[2][1:]), int(parts[3][1:])

        results = parse_result_file(os.path.join(results_dir, fname))
        if results:
            avg_cost = sum(r[0] for r in results) / len(results)
            avg_rounds = sum(r[1] for r in results) / len(results)
            data[(net, timeout, agents, algo)] = (avg_cost, avg_rounds)
    return data


def load_domain_data(results_dir):
    data = {}
    for fname in os.listdir(results_dir):
        if not fname.endswith('_results.csv'):
            continue
        name = fname.replace('test_', '').replace('_results.csv', '')
        parts = name.split('_')
        if 'SCALE' in fname:
            algo, net = parts[0], 'SCALE_FREE'
            timeout, domain = int(parts[3][1:]), int(parts[4][1:])
        else:
            algo, net = parts[0], parts[1]
            timeout, domain = int(parts[2][1:]), int(parts[3][1:])

        results = parse_result_file(os.path.join(results_dir, fname))
        if results:
            avg_cost = sum(r[0] for r in results) / len(results)
            avg_rounds = sum(r[1] for r in results) / len(results)
            data[(net, timeout, domain, algo)] = (avg_cost, avg_rounds)
    return data


def load_density_data(results_dir):
    data = {}
    density_map = {'02': 0.2, '04': 0.4, '06': 0.6, '08': 0.8, '10': 1.0}
    for fname in os.listdir(results_dir):
        if not fname.endswith('_results.csv'):
            continue
        name = fname.replace('test_', '').replace('_results.csv', '')
        parts = name.split('_')
        algo = parts[0]
        timeout = int(parts[2][1:])
        density = density_map.get(parts[3][2:], 0)

        results = parse_result_file(os.path.join(results_dir, fname))
        if results:
            avg_cost = sum(r[0] for r in results) / len(results)
            avg_rounds = sum(r[1] for r in results) / len(results)
            data[(timeout, density, algo)] = (avg_cost, avg_rounds)
    return data


def fmt_cell(data, key_base, algos):
    """Format a cell with 3 algo values: cost (rounds) for each."""
    parts = []
    for algo in algos:
        key = key_base + (algo,)
        if key in data:
            cost, rounds = data[key]
            parts.append(f"{cost:.1f} ({rounds:.1f})")
        else:
            parts.append("N/A")
    return " | ".join(parts)


def write_table_text(f, title, col_header, col_values, row_label, row_values, data, key_builder, algos):
    f.write(f"\n{'=' * 120}\n")
    f.write(f"{title}\n")
    f.write(f"{'=' * 120}\n")
    f.write(f"Cell format: PDSA cost (rounds) | PMGM cost (rounds) | PMAXSUM cost (rounds)\n\n")

    header = f"{'Timeout':>10}"
    for cv in col_values:
        header += f" | {col_header}={cv}".center(45)
    f.write(header + "\n")
    f.write("-" * len(header) + "\n")

    for rv in row_values:
        row = f"{rv}s".rjust(10)
        for cv in col_values:
            key_base = key_builder(rv, cv)
            cell = fmt_cell(data, key_base, algos)
            row += f" | {cell:>43}"
        f.write(row + "\n")
    f.write("\n")


def write_table_csv(writer, title, col_header, col_values, row_label, row_values, data, key_builder, algos):
    writer.writerow([title])
    header = [row_label]
    for cv in col_values:
        for algo in algos:
            header.append(f"{col_header}={cv} {algo} Cost")
            header.append(f"{col_header}={cv} {algo} Rounds")
    writer.writerow(header)

    for rv in row_values:
        row = [f"{rv}s"]
        for cv in col_values:
            key_base = key_builder(rv, cv)
            for algo in algos:
                key = key_base + (algo,)
                if key in data:
                    cost, rounds = data[key]
                    row.extend([f"{cost:.1f}", f"{rounds:.1f}"])
                else:
                    row.extend(["N/A", "N/A"])
        writer.writerow(row)
    writer.writerow([])


def main():
    base_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'results')

    agents_dir = domain_dir = density_dir = None
    for d in sorted(os.listdir(base_dir)):
        full = os.path.join(base_dir, d)
        if not os.path.isdir(full):
            continue
        if d.startswith('agents_'):
            agents_dir = full
        elif d.startswith('domain_'):
            domain_dir = full
        elif d.startswith('density_'):
            density_dir = full

    algos = ['PDSA', 'PMGM', 'PMAXSUM']
    timeouts = [60, 120, 180, 240, 300]
    agent_counts = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    domain_sizes = [5, 10, 15, 20, 25]
    densities = [0.2, 0.4, 0.6, 0.8, 1.0]

    out_txt = os.path.join(base_dir, 'summary_tables.txt')
    out_csv = os.path.join(base_dir, 'summary_tables.csv')

    with open(out_txt, 'w') as ftxt, open(out_csv, 'w', newline='') as fcsv:
        writer = csv.writer(fcsv)

        if agents_dir:
            adata = load_agents_data(agents_dir)

            write_table_text(ftxt,
                "Table 1: Varying Agent Count - RANDOM Network",
                "Agents", agent_counts, "Timeout", timeouts, adata,
                lambda t, a: ('RANDOM', t, a), algos)
            write_table_csv(writer,
                "Table 1: Varying Agent Count - RANDOM Network",
                "Agents", agent_counts, "Timeout", timeouts, adata,
                lambda t, a: ('RANDOM', t, a), algos)

            write_table_text(ftxt,
                "Table 2: Varying Agent Count - SCALE_FREE Network",
                "Agents", agent_counts, "Timeout", timeouts, adata,
                lambda t, a: ('SCALE_FREE', t, a), algos)
            write_table_csv(writer,
                "Table 2: Varying Agent Count - SCALE_FREE Network",
                "Agents", agent_counts, "Timeout", timeouts, adata,
                lambda t, a: ('SCALE_FREE', t, a), algos)

        if domain_dir:
            ddata = load_domain_data(domain_dir)

            write_table_text(ftxt,
                "Table 3: Varying Domain Size - RANDOM Network",
                "Domain", domain_sizes, "Timeout", timeouts, ddata,
                lambda t, d: ('RANDOM', t, d), algos)
            write_table_csv(writer,
                "Table 3: Varying Domain Size - RANDOM Network",
                "Domain", domain_sizes, "Timeout", timeouts, ddata,
                lambda t, d: ('RANDOM', t, d), algos)

            write_table_text(ftxt,
                "Table 4: Varying Domain Size - SCALE_FREE Network",
                "Domain", domain_sizes, "Timeout", timeouts, ddata,
                lambda t, d: ('SCALE_FREE', t, d), algos)
            write_table_csv(writer,
                "Table 4: Varying Domain Size - SCALE_FREE Network",
                "Domain", domain_sizes, "Timeout", timeouts, ddata,
                lambda t, d: ('SCALE_FREE', t, d), algos)

        if density_dir:
            ndata = load_density_data(density_dir)

            write_table_text(ftxt,
                "Table 5: Varying Network Density - RANDOM Network",
                "Density", densities, "Timeout", timeouts, ndata,
                lambda t, d: (t, d), algos)
            write_table_csv(writer,
                "Table 5: Varying Network Density - RANDOM Network",
                "Density", densities, "Timeout", timeouts, ndata,
                lambda t, d: (t, d), algos)

    print(f"Text output: {out_txt}")
    print(f"CSV output:  {out_csv}")

    with open(out_txt, 'r') as f:
        print(f.read())


if __name__ == '__main__':
    main()
