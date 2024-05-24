#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
filter="${1:-"_no_filter_"}"
name_prefix="logs_"
[[ "$filter" == "extract_exceptions" ]] && name_prefix="exceptions_"
$SCRIPT_DIR/logs.sh --no-follow "$filter" > "${name_prefix}$(date +%Y-%m-%d-%H%M%S).txt"