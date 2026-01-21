#!/bin/bash
WORKDIR=/tmp/output$$
iteration=0
while true; do
  iteration=$((iteration + 1))
  echo "====== Iteration ${iteration} ======"
  echo "Start time: $(date)"
  mkdir -p $WORKDIR
  current_time=$(date +%Y%m%d-%H%M%S)
  # prepare this by building the jar with "PULSAR_VERSION=4.0.8 ./gradlew shadowJar"
  java -cp build/libs/pulsar-playground-all.jar com.github.lhotari.pulsar.playground.TestScenarioIssue25145 2>&1 | tee $WORKDIR/output_${current_time}.log
  rc=${PIPESTATUS[0]}

  if [ "${rc}" -eq 0 ]; then
    echo "====== Iteration ${iteration} completed successfully ======"
    echo "Sleeping 5s before next iteration..."
    sleep 5
    continue
  else
    echo "====== Iteration ${iteration} failed with exit code ${rc} ======"
    echo "Inconsistency detected. Total iterations run: ${iteration}"
    echo "Log saved to: $WORKDIR/output_${current_time}.log"
    exit ${rc}
  fi
done
