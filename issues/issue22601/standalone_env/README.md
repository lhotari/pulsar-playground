# Repro for issue 22601 based on repro contributed by semistone

based on https://github.com/semistone/personal_notes/blob/main/pulsar_issue_22601/Test.md

## Instructions

```
./download_pulsar.sh
./configure_bookie_and_broker.sh
./start_zk.sh
./initialize_metadata.sh
./start_bookie_and_broker.sh
```

You need Pulsar client from https://github.com/semistone/pulsar/tree/debug_ssues_22601 to run 

consumer
```
pulsar-perf consume persistent://public/default/my-topic -n 10 -sp Latest -ss angus_test -st Key_Shared
```

producer
```
pulsar-perf produce persistent://public/default/my-topic -r 6000 -s 2000 -bp 2 -db -b 1
```