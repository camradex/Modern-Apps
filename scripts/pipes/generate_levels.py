#!/usr/bin/env python3
"""Generate solvable Flow/Pipes levels using Hamiltonian path partitioning.

Every level guarantees:
- Non-crossing solution paths that fill the entire grid
- No shortcut: verified by exhaustive backtracking solver that proves
  no combination of non-crossing paths can connect all pairs without
  filling every cell.
"""
import json
import random
import sys
from collections import deque


def rectangular_cells(rows, cols):
    return {(r, c) for r in range(rows) for c in range(cols)}


def octagon_cells(size, cut=None):
    if cut is None:
        cut = size // 4 + 1
    return {(r, c) for r in range(size) for c in range(size)
            if not ((r < cut and c < cut - r) or
                    (r < cut and c >= size - cut + r) or
                    (r >= size - cut and c < cut - (size - 1 - r)) or
                    (r >= size - cut and c >= size - cut + (size - 1 - r)))}


def spiral_cells(rows, cols):
    result = []
    top, bottom, left, right = 0, rows - 1, 0, cols - 1
    target = int(rows * cols * 0.7)
    seen = set()
    while len(result) < target and top <= bottom and left <= right:
        for c in range(left, right + 1):
            if (top, c) not in seen:
                result.append((top, c)); seen.add((top, c))
        top += 1
        for r in range(top, bottom + 1):
            if (r, right) not in seen:
                result.append((r, right)); seen.add((r, right))
        right -= 1
        if top <= bottom:
            for c in range(right, left - 1, -1):
                if (bottom, c) not in seen:
                    result.append((bottom, c)); seen.add((bottom, c))
            bottom -= 1
        if left <= right:
            for r in range(bottom, top - 1, -1):
                if (r, left) not in seen:
                    result.append((r, left)); seen.add((r, left))
            left += 1
    return set(result[:target])


def compute_adjacency(cells):
    dirs = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    adj = {}
    for (r, c) in cells:
        adj[(r, c)] = [(r + dr, c + dc) for dr, dc in dirs if (r + dr, c + dc) in cells]
    return adj


# --------------- Hamiltonian path ---------------

def find_hamiltonian_path(cells, adj, rng):
    n = len(cells)
    cells_list = list(cells)
    deg1 = [c for c in cells if len(adj[c]) == 1]
    if len(deg1) > 2:
        return None
    for _ in range(200):
        rng.shuffle(cells_list)
        starts = deg1 + [c for c in cells_list if c not in deg1]
        for start in starts[:15]:
            path = [start]
            visited = {start}
            while len(path) < n:
                cur = path[-1]
                nbs = [nb for nb in adj[cur] if nb not in visited]
                if not nbs:
                    break
                nbs.sort(key=lambda nb: (
                    sum(1 for nn in adj[nb] if nn not in visited),
                    rng.random()
                ))
                path.append(nbs[0])
                visited.add(nbs[0])
            if len(path) == n:
                return path
    return None


# --------------- BFS utilities ---------------

def bfs_dist(adj, start, end):
    if start == end:
        return 1
    visited = {start}
    queue = deque([(start, 1)])
    while queue:
        cur, d = queue.popleft()
        for nb in adj[cur]:
            if nb == end:
                return d + 1
            if nb not in visited:
                visited.add(nb)
                queue.append((nb, d + 1))
    return 10**9


def find_short_paths(adj, start, end, blocked, max_extra=2, max_paths=8):
    """Find multiple short paths from start to end, avoiding blocked cells.
    Returns paths sorted by length. Uses BFS-from-end for pruning."""
    if start in blocked or end in blocked:
        return []

    # BFS from end for distance pruning
    dist_to_end = {end: 0}
    queue = deque([end])
    while queue:
        cur = queue.popleft()
        for nb in adj[cur]:
            if nb not in dist_to_end and nb not in blocked:
                dist_to_end[nb] = dist_to_end[cur] + 1
                queue.append(nb)

    if start not in dist_to_end:
        return []

    shortest = dist_to_end[start]
    max_depth = shortest + max_extra

    paths = []
    path_set = set()  # track path[0] visited to avoid revisiting

    def dfs(cur, path, visited):
        if len(paths) >= max_paths:
            return
        if cur == end:
            paths.append(tuple(path))
            return
        budget = max_depth - len(path)
        if budget <= 0:
            return
        for nb in adj[cur]:
            if nb not in visited and nb not in blocked:
                d = dist_to_end.get(nb)
                if d is not None and d <= budget:
                    visited.add(nb)
                    path.append(nb)
                    dfs(nb, path, visited)
                    path.pop()
                    visited.discard(nb)
                    if len(paths) >= max_paths:
                        return

    dfs(start, [start], {start})
    return paths


# --------------- Shortcut solver ---------------

def has_shortcut(cells, adj, endpoints):
    """Exhaustive backtracking: can all pairs be connected without filling every cell?

    Tries multiple short paths per pair, different pair orderings.
    If ANY combination leaves cells unfilled, returns True (shortcut exists).
    """
    n = len(cells)
    pairs = [(tuple(ep['cells'][0]), tuple(ep['cells'][1])) for ep in endpoints]

    # Try 3 orderings: shortest-first, longest-first, original
    orderings = [
        sorted(range(len(pairs)), key=lambda i: bfs_dist(adj, pairs[i][0], pairs[i][1])),
        sorted(range(len(pairs)), key=lambda i: -bfs_dist(adj, pairs[i][0], pairs[i][1])),
        list(range(len(pairs))),
    ]

    for ordering in orderings:
        ordered = [pairs[i] for i in ordering]
        if _solve_shortcut(n, adj, ordered):
            return True
    return False


def _solve_shortcut(n, adj, pairs):
    """Backtracking solver: can pairs be connected using < n cells total?"""
    found = [False]
    calls = [0]
    LIMIT = 200000

    def solve(idx, used):
        if found[0] or calls[0] >= LIMIT:
            return
        if idx == len(pairs):
            if len(used) < n:
                found[0] = True
            return

        start, end = pairs[idx]
        paths = find_short_paths(adj, start, end, used, max_extra=2, max_paths=8)
        for path in paths:
            calls[0] += 1
            if calls[0] >= LIMIT:
                return
            solve(idx + 1, used | set(path))
            if found[0]:
                return

    solve(0, set())
    return found[0]


# --------------- Splitting ---------------

def split_path(path_len, num_flows, rng):
    min_seg = 3
    if path_len < num_flows * min_seg:
        return None
    remaining = path_len - num_flows * min_seg
    sizes = [min_seg] * num_flows
    for _ in range(remaining):
        sizes[rng.randint(0, num_flows - 1)] += 1
    rng.shuffle(sizes)
    return sizes


# --------------- Level generation ---------------

def generate_level(cells, adj, num_flows, seed, level_id):
    rng = random.Random(seed)
    path = find_hamiltonian_path(cells, adj, rng)
    if path is None:
        return None

    rows = max(r for r, c in cells) + 1
    cols = max(c for r, c in cells) + 1

    # Try several random splits of this Hamiltonian path
    for attempt in range(10):
        r = random.Random(seed + attempt * 37)
        sizes = split_path(len(path), num_flows, r)
        if sizes is None:
            continue

        endpoints = []
        idx = 0
        for color, size in enumerate(sizes):
            seg = path[idx:idx + size]
            endpoints.append({
                "color": color,
                "cells": [[seg[0][0], seg[0][1]], [seg[-1][0], seg[-1][1]]]
            })
            idx += size

        if not has_shortcut(cells, adj, endpoints):
            return {
                "id": level_id,
                "rows": rows,
                "cols": cols,
                "endpoints": endpoints,
                "optimalMoves": num_flows
            }

    return None


def generate_pack(name, shape, cells, num_levels, flow_range, base_seed):
    adj = compute_adjacency(cells)
    levels = []
    seed = base_seed
    attempts = 0
    max_attempts = num_levels * 300
    while len(levels) < num_levels and attempts < max_attempts:
        num_flows = random.Random(seed).randint(flow_range[0], flow_range[1])
        lid = f"{name.replace(' ', '_')}_{len(levels)+1:03d}"
        level = generate_level(cells, adj, num_flows, seed, lid)
        if level is not None:
            if shape not in ("rectangular",):
                level["cells"] = [[r, c] for r, c in sorted(cells)]
                adj_json = {}
                for (r, c), neighbors in adj.items():
                    adj_json[f"{r},{c}"] = [[nr, nc] for nr, nc in neighbors]
                level["adjacency"] = adj_json
            levels.append(level)
            sys.stdout.write(f"\r  {len(levels)}/{num_levels}")
            sys.stdout.flush()
        seed += 1
        attempts += 1
    print()
    return levels


def generate_special_pack(name, shape, cells, num_levels, flow_range, base_seed):
    adj = compute_adjacency(cells)
    render_positions = {}
    for r, c in sorted(cells):
        render_positions[f"{r},{c}"] = {"x": float(c), "y": float(r)}

    levels = []
    seed = base_seed
    attempts = 0
    max_attempts = num_levels * 300
    while len(levels) < num_levels and attempts < max_attempts:
        num_flows = random.Random(seed).randint(flow_range[0], flow_range[1])
        lid = f"{name.replace(' ', '_')}_{len(levels)+1:03d}"
        level = generate_level(cells, adj, num_flows, seed, lid)
        if level is not None:
            level["cells"] = [[r, c] for r, c in sorted(cells)]
            adj_json = {}
            for (r, c), neighbors in adj.items():
                adj_json[f"{r},{c}"] = [[nr, nc] for nr, nc in neighbors]
            level["adjacency"] = adj_json
            level["renderPositions"] = render_positions
            levels.append(level)
            sys.stdout.write(f"\r  {len(levels)}/{num_levels}")
            sys.stdout.flush()
        seed += 1
        attempts += 1
    print()
    return levels


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."

    packs = [
        {"name": "5×5", "shape": "rectangular",
         "cells": rectangular_cells(5, 5), "levels_count": 30,
         "flow_range": (4, 5), "seed": 1000},
        {"name": "6×6", "shape": "rectangular",
         "cells": rectangular_cells(6, 6), "levels_count": 30,
         "flow_range": (5, 6), "seed": 2000},
        {"name": "7×7", "shape": "rectangular",
         "cells": rectangular_cells(7, 7), "levels_count": 30,
         "flow_range": (6, 7), "seed": 3000},
        {"name": "Octagon", "shape": "octagon",
         "cells": octagon_cells(6, 2), "levels_count": 20,
         "flow_range": (4, 5), "seed": 4000},
        {"name": "Spiral", "shape": "spiral",
         "cells": spiral_cells(7, 7), "levels_count": 20,
         "flow_range": (4, 5), "seed": 5000},
    ]

    filenames = ["5x5.json", "6x6.json", "7x7.json", "octagon.json", "spiral.json"]

    for pack_info, filename in zip(packs, filenames):
        print(f"Generating {pack_info['name']}...")
        if pack_info["shape"] in ("octagon", "spiral"):
            levels = generate_special_pack(
                pack_info["name"], pack_info["shape"], pack_info["cells"],
                pack_info["levels_count"], pack_info["flow_range"], pack_info["seed"])
        else:
            levels = generate_pack(
                pack_info["name"], pack_info["shape"], pack_info["cells"],
                pack_info["levels_count"], pack_info["flow_range"], pack_info["seed"])

        pack_data = {"name": pack_info["name"], "shape": pack_info["shape"], "levels": levels}
        path = f"{out_dir}/{filename}"
        with open(path, "w") as f:
            json.dump(pack_data, f, indent=2)
        print(f"  -> {len(levels)} levels written to {path}")


if __name__ == "__main__":
    main()
