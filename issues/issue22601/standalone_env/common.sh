SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
export PULSAR_HOME="$SCRIPT_DIR/apache-pulsar-3.2.2"
export PATH="$PULSAR_HOME/bin:$PATH"