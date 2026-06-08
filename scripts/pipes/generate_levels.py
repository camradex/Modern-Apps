#!/usr/bin/env python3
"""Generate Flow/Pipes levels.

Rectangular grids: Numberlink algorithm (Thomas Ahle) — paths fill the grid
by construction. Levels validated with NumberLink solver (unique solution).
"""
import sys, json, random, subprocess, os
from collections import defaultdict


# ===================== Numberlink Generator =====================

T, L, R = range(3)


def sign(x):
    if x == 0:
        return x
    return -1 if x < 0 else 1


def unrotate(x, y, dx, dy):
    while (dx, dy) != (0, 1):
        x, y, dx, dy = -y, x, -dy, dx
    return x, y


class Path:
    def __init__(self, steps):
        self.steps = steps

    def xys(self, dx=0, dy=1):
        x, y = 0, 0
        yield (x, y)
        for step in self.steps:
            x, y = x + dx, y + dy
            yield (x, y)
            if step == L:
                dx, dy = -dy, dx
            if step == R:
                dx, dy = dy, -dx
            elif step == T:
                x, y = x + dx, y + dy
                yield (x, y)

    def test(self):
        ps = list(self.xys())
        return len(set(ps)) == len(ps)

    def test_loop(self):
        ps = list(self.xys())
        seen = set(ps)
        return len(ps) == len(seen) or (len(ps) == len(seen) + 1 and ps[0] == ps[-1])

    def winding(self):
        return self.steps.count(R) - self.steps.count(L)


class UnionFind:
    def __init__(self):
        self.uf = {}

    def union(self, a, b):
        self.uf[self.find(a)] = self.find(b)

    def find(self, a):
        if self.uf.get(a, a) == a:
            return a
        par = self.find(self.uf.get(a, a))
        self.uf[a] = par
        return par


class Mitm:
    def __init__(self, lr_price=2, t_price=1):
        self.lr_price = lr_price
        self.t_price = t_price
        self.inv = defaultdict(list)
        self.list = []

    def prepare(self, budget):
        for path, (x, y, dx, dy) in self._good_paths(0, 0, 0, 1, budget):
            self.list.append((path, x, y, dx, dy))
            self.inv[x, y, dx, dy].append(path)

    def rand_path2(self, xn, yn, dxn, dyn):
        seen = set()
        path = []
        for _attempt in range(10000):
            seen.clear()
            del path[:]
            x, y, dx, dy = 0, 0, 0, 1
            seen.add((x, y))
            for _ in range(2 * (abs(xn) + abs(yn))):
                step, = random.choices(
                    [L, R, T],
                    [1 / self.lr_price, 1 / self.lr_price, 2 / self.t_price])
                path.append(step)
                x, y = x + dx, y + dy
                if (x, y) in seen:
                    break
                seen.add((x, y))
                if step == L:
                    dx, dy = -dy, dx
                if step == R:
                    dx, dy = dy, -dx
                elif step == T:
                    x, y = x + dx, y + dy
                    if (x, y) in seen:
                        break
                    seen.add((x, y))
                if (x, y) == (xn, yn):
                    return Path(path)
                ends = self._lookup(dx, dy, xn - x, yn - y, dxn, dyn)
                if ends:
                    return Path(tuple(path) + random.choice(ends))
        return None

    def rand_loop(self, clock=0):
        for _attempt in range(10000):
            path, x, y, dx, dy = random.choice(self.list)
            path2s = self._lookup(dx, dy, -x, -y, 0, 1)
            if path2s:
                path2 = random.choice(path2s)
                joined = Path(path + path2)
                if clock and joined.winding() != clock * 4:
                    continue
                if joined.test_loop():
                    return joined
        return None

    def _good_paths(self, x, y, dx, dy, budget, seen=None):
        if seen is None:
            seen = set()
        if budget >= 0:
            yield (), (x, y, dx, dy)
        if budget <= 0:
            return
        seen.add((x, y))
        x1, y1 = x + dx, y + dy
        if (x1, y1) not in seen:
            for path, end in self._good_paths(
                    x1, y1, -dy, dx, budget - self.lr_price, seen):
                yield (L,) + path, end
            for path, end in self._good_paths(
                    x1, y1, dy, -dx, budget - self.lr_price, seen):
                yield (R,) + path, end
            seen.add((x1, y1))
            x2, y2 = x1 + dx, y1 + dy
            if (x2, y2) not in seen:
                for path, end in self._good_paths(
                        x2, y2, dx, dy, budget - self.t_price, seen):
                    yield (T,) + path, end
            seen.remove((x1, y1))
        seen.remove((x, y))

    def _lookup(self, dx, dy, xn, yn, dxn, dyn):
        xt, yt = unrotate(xn, yn, dx, dy)
        dxt, dyt = unrotate(dxn, dyn, dx, dy)
        return self.inv[xt, yt, dxt, dyt]


class Grid:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.grid = {}

    def __setitem__(self, key, val):
        self.grid[key] = val

    def __getitem__(self, key):
        return self.grid.get(key, ' ')

    def __contains__(self, key):
        return key in self.grid

    def __iter__(self):
        return iter(self.grid.items())

    def clear(self):
        self.grid.clear()

    def values(self):
        return self.grid.values()

    def shrink(self):
        small = Grid(self.w // 2, self.h // 2)
        for y in range(self.h // 2):
            for x in range(self.w // 2):
                small[x, y] = self[2 * x + 1, 2 * y + 1]
        return small

    def test_path(self, path, x0, y0, dx0=0, dy0=1):
        return all(
            0 <= x0 - x + y < self.w and 0 <= y0 + x + y < self.h
            and (x0 - x + y, y0 + x + y) not in self
            for x, y in path.xys(dx0, dy0))

    def draw_path(self, path, x0, y0, dx0=0, dy0=1, loop=False):
        ps = list(path.xys(dx0, dy0))
        if loop:
            assert ps[0] == ps[-1]
            ps.append(ps[1])
        for i in range(1, len(ps) - 1):
            xp, yp = ps[i - 1]
            x, y = ps[i]
            xn, yn = ps[i + 1]
            self[x0 - x + y, y0 + x + y] = {
                (1, 1, 1): '<', (-1, -1, -1): '<',
                (1, 1, -1): '>', (-1, -1, 1): '>',
                (-1, 1, 1): 'v', (1, -1, -1): 'v',
                (-1, 1, -1): '^', (1, -1, 1): '^',
                (0, 2, 0): '\\', (0, -2, 0): '\\',
                (2, 0, 0): '/', (-2, 0, 0): '/'
            }[xn - xp, yn - yp, sign((x - xp) * (yn - y) - (xn - x) * (y - yp))]

    def make_tubes(self):
        uf = UnionFind()
        tube_grid = Grid(self.w, self.h)
        for x in range(self.w):
            d = '-'
            for y in range(self.h):
                for dx, dy in {
                    '/-': [(0, 1)], '\\-': [(1, 0), (0, 1)],
                    '/|': [(1, 0)],
                    ' -': [(1, 0)], ' |': [(0, 1)],
                    'v|': [(0, 1)], '>|': [(1, 0)],
                    'v-': [(0, 1)], '>-': [(1, 0)],
                }.get(self[x, y] + d, []):
                    uf.union((x, y), (x + dx, y + dy))
                tube_grid[x, y] = {
                    '/-': '┐', '\\-': '┌',
                    '/|': '└', '\\|': '┘',
                    ' -': '-', ' |': '|',
                }.get(self[x, y] + d, 'x')
                if self[x, y] in '\\/v^':
                    d = '|' if d == '-' else '-'
        return tube_grid, uf

    def clear_path(self, path, x, y):
        path_grid = Grid(self.w, self.h)
        path_grid.draw_path(path, x, y, loop=True)
        for key, val in path_grid.make_tubes()[0]:
            if val == '|':
                self.grid.pop(key, None)


LOOP_TRIES = 1000


def has_loops(grid, uf):
    groups = len({uf.find((x, y)) for y in range(grid.h) for x in range(grid.w)})
    ends = sum(bool(grid[x, y] in 'v^<>') for y in range(grid.h) for x in range(grid.w))
    return ends != 2 * groups


def has_pair(tg, uf):
    for y in range(tg.h):
        for x in range(tg.w):
            for dx, dy in ((1, 0), (0, 1)):
                x1, y1 = x + dx, y + dy
                if x1 < tg.w and y1 < tg.h:
                    if tg[x, y] == tg[x1, y1] == 'x' \
                            and uf.find((x, y)) == uf.find((x1, y1)):
                        return True
    return False


def has_tripple(tg, uf):
    for y in range(tg.h):
        for x in range(tg.w):
            r = uf.find((x, y))
            nbs = 0
            for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                x1, y1 = x + dx, y + dy
                if 0 <= x1 < tg.w and 0 <= y1 < tg.h and uf.find((x1, y1)) == r:
                    nbs += 1
            if nbs >= 3:
                return True
    return False


def make(w, h, mitm, min_numbers=0, max_numbers=1000):
    def test_ready(grid):
        sg = grid.shrink()
        stg, uf = sg.make_tubes()
        numbers = list(stg.values()).count('x') // 2
        return (min_numbers <= numbers <= max_numbers
                and not has_loops(sg, uf)
                and not has_pair(stg, uf)
                and not has_tripple(stg, uf))

    grid = Grid(2 * w + 1, 2 * h + 1)

    for _ in range(2000):
        grid.clear()

        path = mitm.rand_path2(h, h, 0, -1)
        if path is None or not grid.test_path(path, 0, 0):
            continue
        grid.draw_path(path, 0, 0)
        grid[0, 0], grid[0, 2 * h] = '\\', '/'

        path2 = mitm.rand_path2(h, h, 0, -1)
        if path2 is None or not grid.test_path(path2, 2 * w, 2 * h, 0, -1):
            continue
        grid.draw_path(path2, 2 * w, 2 * h, 0, -1)
        grid[2 * w, 0], grid[2 * w, 2 * h] = '/', '\\'

        if test_ready(grid):
            return grid.shrink()

        tg, _ = grid.make_tubes()
        for tries in range(LOOP_TRIES):
            x, y = 2 * random.randrange(w), 2 * random.randrange(h)
            if tg[x, y] not in '-|':
                continue
            loop = mitm.rand_loop(clock=1 if tg[x, y] == '-' else -1)
            if loop is None:
                continue
            if grid.test_path(loop, x, y):
                grid.clear_path(loop, x, y)
                grid.draw_path(loop, x, y, loop=True)
                tg, _ = grid.make_tubes()

                sg = grid.shrink()
                stg, uf = sg.make_tubes()
                numbers = list(stg.values()).count('x') // 2
                if numbers > max_numbers:
                    break
                if test_ready(grid):
                    return sg

    return None


def too_many_short_paths(path_lengths, num_pairs):
    """At most 1 path with ≤ 4 cells (≤ 3 edges), or 2 if ≥ 6 pairs."""
    short = sum(1 for l in path_lengths if l <= 4)
    max_short = 2 if num_pairs >= 6 else 1
    return short > max_short


def grid_to_level(grid, level_id, w, h):
    tube_grid, uf = grid.make_tubes()

    group_sizes = {}
    for y in range(grid.h):
        for x in range(grid.w):
            g = uf.find((x, y))
            group_sizes[g] = group_sizes.get(g, 0) + 1

    groups = defaultdict(list)
    for y in range(grid.h):
        for x in range(grid.w):
            if tube_grid[x, y] == 'x':
                groups[uf.find((x, y))].append([y, x])

    endpoints = []
    path_lengths = []
    color = 0
    for group_id, cells in groups.items():
        if len(cells) == 2:
            endpoints.append({"color": color, "cells": cells})
            path_lengths.append(group_sizes[group_id])
            color += 1

    if not endpoints:
        return None

    if too_many_short_paths(path_lengths, len(endpoints)):
        return None

    return {
        "id": level_id,
        "rows": h,
        "cols": w,
        "endpoints": endpoints,
        "optimalMoves": len(endpoints)
    }


# ===================== Solver Validation =====================

SOLVER_DIR = os.path.dirname(os.path.abspath(__file__))
SOLVER_SRC = os.path.join(SOLVER_DIR, "numberlink_solver.cpp")
SOLVER_BIN = os.path.join(SOLVER_DIR, "numberlink_solver")


def compile_solver():
    if os.path.exists(SOLVER_BIN) and os.path.getmtime(SOLVER_BIN) >= os.path.getmtime(SOLVER_SRC):
        return True
    print("Compiling NumberLink solver...")
    result = subprocess.run(
        ["g++", "-O2", "-o", SOLVER_BIN, SOLVER_SRC],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print(f"Failed to compile solver: {result.stderr}", file=sys.stderr)
        return False
    return True


def validate_unique_solution(level):
    rows, cols = level["rows"], level["cols"]
    grid = [[0] * cols for _ in range(rows)]
    for ep in level["endpoints"]:
        color_num = ep["color"] + 1
        for r, c in ep["cells"]:
            grid[r][c] = color_num

    input_str = f"{cols} {rows}\n"
    for row in grid:
        input_str += " ".join(str(v) for v in row) + "\n"

    try:
        result = subprocess.run(
            [SOLVER_BIN],
            input=input_str, capture_output=True, text=True, timeout=60
        )
    except subprocess.TimeoutExpired:
        return False
    if result.returncode != 0:
        return False

    for line in result.stdout.strip().split("\n"):
        if "# of solutions:" in line:
            count_str = line.split(":")[-1].strip()
            try:
                count = int(float(count_str))
                return count == 1
            except ValueError:
                return False
    return False


# ===================== Pack Generation =====================

def generate_rect_pack(name, w, h, num_levels, flow_range, base_seed):
    budget = min(20, max(h, 6))
    mitm = Mitm(lr_price=2, t_price=1)
    mitm.prepare(budget)

    levels = []
    seed = base_seed
    max_attempts = num_levels * 500
    attempts = 0
    while len(levels) < num_levels and attempts < max_attempts:
        random.seed(seed)
        min_n, max_n = flow_range
        grid = make(w, h, mitm, min_n, max_n)
        if grid is not None:
            lid = f"{name.replace(' ', '_')}_{len(levels)+1:03d}"
            level = grid_to_level(grid, lid, w, h)
            if level is not None and validate_unique_solution(level):
                levels.append(level)
                sys.stdout.write(f"\r  {len(levels)}/{num_levels}")
                sys.stdout.flush()
        seed += 1
        attempts += 1
    print()
    return levels


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."

    if not compile_solver():
        print("Error: Could not compile NumberLink solver", file=sys.stderr)
        sys.exit(1)

    packs = [
        {"name": "5×5", "type": "rect", "w": 5, "h": 5,
         "levels": 30, "flow_range": (3, 7), "seed": 10000},
        {"name": "6×6", "type": "rect", "w": 6, "h": 6,
         "levels": 30, "flow_range": (4, 9), "seed": 20000},
        {"name": "7×7", "type": "rect", "w": 7, "h": 7,
         "levels": 30, "flow_range": (4, 10), "seed": 30000},
    ]

    filenames = ["5x5.json", "6x6.json", "7x7.json"]

    for pack_info, filename in zip(packs, filenames):
        print(f"Generating {pack_info['name']}...")
        levels = generate_rect_pack(
            pack_info["name"], pack_info["w"], pack_info["h"],
            pack_info["levels"], pack_info["flow_range"], pack_info["seed"])
        pack_data = {"name": pack_info["name"], "shape": "rectangular", "levels": levels}

        path = f"{out_dir}/{filename}"
        with open(path, "w") as f:
            json.dump(pack_data, f, indent=2)
        print(f"  -> {len(levels)} levels written to {path}")


if __name__ == "__main__":
    main()
