SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
export PULSAR_HOME="$HOME/workspace-pulsar/pulsar-debugging/apache-pulsar-3.2.2-patched-pulsar-perf"
export PATH="$PULSAR_HOME/bin:$PATH"