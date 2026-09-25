import os
import sys
import time
import json
import random
import argparse
import numpy as np
import zarr
import pyperf
from ome_zarr.io import parse_url
from ome_zarr.reader import Reader
from ome_zarr.writer import write_image
from ome_zarr.format import FormatV04, format_from_version

LOG_FILE = os.path.abspath("benchmark_results.log")

LOCAL_V04_PATH = os.path.abspath("data/v04/pig_heart.ome.zarr")
LOCAL_V05_PATH = os.path.abspath("data/v05/pig_heart.ome.zarr")

BUCKET_NAME = "ome-zarr-scivis"
REMOTE_V04_PREFIX = "v0.4/128x0/pig_heart.ome.zarr"
REMOTE_V05_PREFIX = "v0.5/128x0/pig_heart.ome.zarr"

REMOTE_V04_URL = f"https://{BUCKET_NAME}.s3.amazonaws.com/{REMOTE_V04_PREFIX}"
REMOTE_V05_URL = f"https://{BUCKET_NAME}.s3.amazonaws.com/{REMOTE_V05_PREFIX}"

# Helper for stdout logging and file tee
class LoggerTee:
    def __init__(self, log_path):
        self.terminal = sys.stdout
        self.log_file = open(log_path, "a", encoding="utf-8")

    def write(self, message):
        self.terminal.write(message)
        self.log_file.write(message)
        self.log_file.flush()

    def flush(self):
        self.terminal.flush()
        self.log_file.flush()

# Fixed random chunk indices using seed 42
random.seed(42)
RANDOM_CHUNKS = [(random.randint(0, 652), random.randint(0, 15), random.randint(0, 15)) for _ in range(20)]

# 1. Open Dataset
def bench_v04_local_open_dataset():
    reader = Reader(parse_url(LOCAL_V04_PATH))
    nodes = list(reader())
    return len(nodes)

def bench_v05_local_open_dataset():
    reader = Reader(parse_url(LOCAL_V05_PATH))
    nodes = list(reader())
    return len(nodes)

def bench_v04_remote_open_dataset():
    try:
        reader = Reader(parse_url(REMOTE_V04_URL))
        nodes = list(reader())
        return len(nodes)
    except Exception as e:
        print(f"SKIPPED: v04_remote_open_dataset ({e})")

def bench_v05_remote_open_dataset():
    try:
        reader = Reader(parse_url(REMOTE_V05_URL))
        nodes = list(reader())
        return len(nodes)
    except Exception as e:
        print(f"SKIPPED: v05_remote_open_dataset ({e})")

# 2. Read Single Chunk (Level 0)
def bench_v04_local_read_single_chunk():
    nodes = list(Reader(parse_url(LOCAL_V04_PATH))())
    data0 = nodes[0].data[0]
    return np.asarray(data0[400:404, 1280:1408, 640:768])

def bench_v05_local_read_single_chunk():
    nodes = list(Reader(parse_url(LOCAL_V05_PATH))())
    data0 = nodes[0].data[0]
    return np.asarray(data0[400:404, 1280:1408, 640:768])

# 3. Read Random Chunks (20 chunks, seed 42)
def bench_v04_local_read_random_chunks():
    nodes = list(Reader(parse_url(LOCAL_V04_PATH))())
    data0 = nodes[0].data[0]
    for zc, yc, xc in RANDOM_CHUNKS:
        _ = np.asarray(data0[zc * 4 : (zc + 1) * 4, yc * 128 : (yc + 1) * 128, xc * 128 : (xc + 1) * 128])

def bench_v05_local_read_random_chunks():
    nodes = list(Reader(parse_url(LOCAL_V05_PATH))())
    data0 = nodes[0].data[0]
    for zc, yc, xc in RANDOM_CHUNKS:
        _ = np.asarray(data0[zc * 4 : (zc + 1) * 4, yc * 128 : (yc + 1) * 128, xc * 128 : (xc + 1) * 128])

# 4. Read Contiguous Region
def bench_v04_local_read_contiguous():
    nodes = list(Reader(parse_url(LOCAL_V04_PATH))())
    data0 = nodes[0].data[0]
    return np.asarray(data0[100:116, 1280:1792, 640:1152])

def bench_v05_local_read_contiguous():
    nodes = list(Reader(parse_url(LOCAL_V05_PATH))())
    data0 = nodes[0].data[0]
    return np.asarray(data0[100:116, 1280:1792, 640:1152])

# 5. Read Multiple Pyramid Levels
def bench_v04_local_read_pyramid_levels():
    nodes = list(Reader(parse_url(LOCAL_V04_PATH))())
    data = nodes[0].data
    s0 = np.asarray(data[0][1306:1307, 640:768, 640:768])
    s1 = np.asarray(data[1][256:257, 640:768, 640:768])
    s2 = np.asarray(data[2][0:1, 0:128, 0:128])
    s3 = np.asarray(data[3][0:1, 0:128, 0:128])
    s4 = np.asarray(data[4][0:1, 0:128, 0:128])
    return (s0, s1, s2, s3, s4)

def bench_v05_local_read_pyramid_levels():
    nodes = list(Reader(parse_url(LOCAL_V05_PATH))())
    data = nodes[0].data
    s0 = np.asarray(data[0][1306:1307, 640:768, 640:768])
    s1 = np.asarray(data[1][256:257, 640:768, 640:768])
    s2 = np.asarray(data[2][0:1, 0:128, 0:128])
    s3 = np.asarray(data[3][0:1, 0:128, 0:128])
    s4 = np.asarray(data[4][0:1, 0:128, 0:128])
    return (s0, s1, s2, s3, s4)

# 6. Write Chunks (LOCAL only)
SYNTH_DATA = np.random.randint(0, 1000, size=(4, 128, 128), dtype=np.int16)

def bench_v04_local_write_chunks():
    store_dir = os.path.abspath("temp_write_v04.zarr")
    os.makedirs(store_dir, exist_ok=True)
    store = zarr.DirectoryStore(store_dir)
    group = zarr.group(store=store, overwrite=True)
    write_image(
        image=SYNTH_DATA,
        group=group,
        scaler=None,
        fmt=format_from_version("0.4"),
        axes=["z", "y", "x"],
    )

def bench_v05_local_write_chunks():
    store_dir = os.path.abspath("temp_write_v05.zarr")
    os.makedirs(store_dir, exist_ok=True)
    store = zarr.DirectoryStore(store_dir)
    group = zarr.group(store=store, overwrite=True)
    write_image(
        image=SYNTH_DATA,
        group=group,
        scaler=None,
        fmt=format_from_version("0.5"),
        axes=["z", "y", "x"],
    )

# 7. Remote S3 Reads
def bench_v04_remote_read_single_chunk():
    try:
        nodes = list(Reader(parse_url(REMOTE_V04_URL))())
        data0 = nodes[0].data[0]
        return np.asarray(data0[400:404, 1280:1408, 640:768])
    except Exception as e:
        print(f"SKIPPED: v04_remote_read_single_chunk ({e})")

def bench_v05_remote_read_single_chunk():
    try:
        nodes = list(Reader(parse_url(REMOTE_V05_URL))())
        data0 = nodes[0].data[0]
        return np.asarray(data0[400:404, 1280:1408, 640:768])
    except Exception as e:
        print(f"SKIPPED: v05_remote_read_single_chunk ({e})")

def bench_v04_remote_read_contiguous():
    try:
        nodes = list(Reader(parse_url(REMOTE_V04_URL))())
        data0 = nodes[0].data[0]
        return np.asarray(data0[100:104, 1280:1536, 640:896])
    except Exception as e:
        print(f"SKIPPED: v04_remote_read_contiguous ({e})")

def bench_v05_remote_read_contiguous():
    try:
        nodes = list(Reader(parse_url(REMOTE_V05_URL))())
        data0 = nodes[0].data[0]
        return np.asarray(data0[100:104, 1280:1536, 640:896])
    except Exception as e:
        print(f"SKIPPED: v05_remote_read_contiguous ({e})")


BENCHMARKS = [
    ("v04_local_open_dataset", bench_v04_local_open_dataset),
    ("v05_local_open_dataset", bench_v05_local_open_dataset),
    ("v04_local_read_single_chunk", bench_v04_local_read_single_chunk),
    ("v05_local_read_single_chunk", bench_v05_local_read_single_chunk),
    ("v04_local_read_random_chunks", bench_v04_local_read_random_chunks),
    ("v05_local_read_random_chunks", bench_v05_local_read_random_chunks),
    ("v04_local_read_contiguous", bench_v04_local_read_contiguous),
    ("v05_local_read_contiguous", bench_v05_local_read_contiguous),
    ("v04_local_read_pyramid_levels", bench_v04_local_read_pyramid_levels),
    ("v05_local_read_pyramid_levels", bench_v05_local_read_pyramid_levels),
    ("v04_local_write_chunks", bench_v04_local_write_chunks),
    ("v05_local_write_chunks", bench_v05_local_write_chunks),
    ("v04_remote_read_single_chunk", bench_v04_remote_read_single_chunk),
    ("v05_remote_read_single_chunk", bench_v05_remote_read_single_chunk),
    ("v04_remote_read_contiguous", bench_v04_remote_read_contiguous),
    ("v05_remote_read_contiguous", bench_v05_remote_read_contiguous),
]

def format_time(seconds):
    if seconds is None:
        return "N/A"
    if seconds < 1e-6:
        return f"{seconds*1e9:.2f} ns"
    elif seconds < 1e-3:
        return f"{seconds*1e6:.2f} us"
    elif seconds < 1.0:
        return f"{seconds*1e3:.2f} ms"
    else:
        return f"{seconds:.3f} s"

def main():
    quick_mode = "--quick" in sys.argv
    if quick_mode:
        sys.argv.remove("--quick")

    if quick_mode:
        runner = pyperf.Runner(values=2, warmups=1, loops=1)
    else:
        runner = pyperf.Runner()

    is_main = "--worker" not in sys.argv

    if is_main:
        print(f"Log file output path: {LOG_FILE}")
        sys.stdout = LoggerTee(LOG_FILE)
        if quick_mode:
            print("Running in QUICK mode (reduced rounds/warmups for rapid testing).")
        else:
            print("Running in FULL RIGOR mode (default pyperf process isolation).")

    results = {}

    for name, func in BENCHMARKS:
        if is_main:
            print(f"\n---> Running Benchmark: {name}", flush=True)
        try:
            bench = runner.bench_func(name, func)
            if is_main and bench and hasattr(bench, "mean"):
                results[name] = bench.mean()
            else:
                results[name] = None
        except Exception as e:
            if is_main:
                print(f"SKIPPED: {name} (Error: {e})", flush=True)
            results[name] = None

    # Print Summary Table
    if is_main:
        print("\n" + "="*85)
        print("                      OME-ZARR PERFORMANCE SUMMARY TABLE                      ")
        print("="*85)
        print(f"{'Operation Category':<30} | {'v0.4 Mean Time':<18} | {'v0.5 Mean Time':<18} | {'% Difference':<12}")
        print("-" * 85)

        categories = [
            ("Open Dataset (Local)", "v04_local_open_dataset", "v05_local_open_dataset"),
            ("Read Single Chunk (Local)", "v04_local_read_single_chunk", "v05_local_read_single_chunk"),
            ("Read 20 Random Chunks", "v04_local_read_random_chunks", "v05_local_read_random_chunks"),
            ("Read Contiguous Region", "v04_local_read_contiguous", "v05_local_read_contiguous"),
            ("Read Pyramid Levels", "v04_local_read_pyramid_levels", "v05_local_read_pyramid_levels"),
            ("Write Chunks (Local)", "v04_local_write_chunks", "v05_local_write_chunks"),
            ("Read Single Chunk (Remote)", "v04_remote_read_single_chunk", "v05_remote_read_single_chunk"),
            ("Read Contiguous Region (Remote)", "v04_remote_read_contiguous", "v05_remote_read_contiguous"),
        ]

        for label, v04_key, v05_key in categories:
            v04_t = results.get(v04_key)
            v05_t = results.get(v05_key)

            v04_str = format_time(v04_t)
            v05_str = format_time(v05_t)

            if v04_t is not None and v05_t is not None and v04_t > 0:
                diff_pct = ((v05_t - v04_t) / v04_t) * 100
                diff_str = f"{diff_pct:+.1f}%"
            else:
                diff_str = "N/A"

            print(f"{label:<30} | {v04_str:<18} | {v05_str:<18} | {diff_str:<12}")

        print("="*85)
        print(f"Full benchmark results saved to: {LOG_FILE}\n")

if __name__ == "__main__":
    main()
