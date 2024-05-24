#!/usr/bin/python3
import sys
import re

def extract_exceptions():
    log_line_pattern = re.compile(r' (WARN|ERROR|DEBUG|INFO|TRACE) ')
    exception_error_pattern = re.compile(r'Exception: |Error: ')
    block = []

    for line in sys.stdin:
        line = line.rstrip()
        if log_line_pattern.search(line):
            if any(exception_error_pattern.search(line) for line in block):
                for entry in block:
                    print(entry)
                print()
            block = []
        block.append(line)

extract_exceptions()