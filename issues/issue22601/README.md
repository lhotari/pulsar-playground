# Issue 22601 repro attempt

Issue: https://github.com/apache/pulsar/issues/22601

Requires pulsar-perf with https://github.com/semistone/pulsar/commit/71d438dd19a20bd700dd7f0ce96c23002a7e5f77 .
Included in https://github.com/apache/pulsar/compare/branch-3.2...lhotari:pulsar:lh-3.2.3-patched-pulsar-perf .

### Building pulsar-perf

```shell
git clone --single-branch -b lh-3.2.3-patched-pulsar-perf https://github.com/lhotari/pulsar pulsar-issue22601-patch
cd pulsar-issue22601-patch

# build the distribution (includes pulsar-perf) to distribution/server/target/apache-pulsar-3.2.3-bin.tar.gz
mvn -Pcore-modules,-main -T 1C clean install -DskipTests -Dspotbugs.skip=true -Dcheckstyle.skip=true

# build a minimal docker image with the changes
./build/build_java_test_image.sh -Dcheckstyle.skip=true
```

I have installed distribution/server/target/apache-pulsar-3.2.3-bin.tar.gz this way
to `~/workspace-pulsar/pulsar-debugging/apache-pulsar-3.2.3-patched-pulsar-perf` directory

```shell
mkdir -p ~/workspace-pulsar/pulsar-debugging
cd ~/workspace-pulsar/pulsar-debugging
tar zxvf ~/pulsar-issue22601-patch/distribution/server/target/apache-pulsar-3.2.3-bin.tar.gz
mv apache-pulsar-3.2.3 apache-pulsar-3.2.3-patched-pulsar-perf
```

### Supporting shell scripts

I use https://github.com/lhotari/pulsar-contributor-toolbox/blob/master/functions/pulsar-contributor-toolbox-functions.sh for automating things. 
Further commands might reference `ptbx_` commands. They are included in these shell functions.
Installation instructions at https://github.com/lhotari/pulsar-contributor-toolbox

### Setting up the test environment

I use microk8s on Linux (PopOS, Ubuntu based). 
Automation scripts and config explained in https://github.com/lhotari/pulsar-playground/tree/master/test-env .

After building the docker image with this command in the directory where I have the lh-3.2.3-patched-pulsar-perf branch checked out, 
the docker image will be available in microk8s local repository.

```shell
cd ~/workspace-pulsar/pulsar-issue22601-patch
ptbx_build_and_push_java_test_image_to_microk8s
```

Checking out pulsar-playground
```shell
cd ~/workspace-pulsar
git clone https://github.com/lhotari/pulsar-playground
```

Installing the test cluster
```shell
cd ~/workspace-pulsar/pulsar-playground/test-env
# update java_test_images.yaml with the most recent image tag
./update_java_test_image_tag.sh
# re-install the pulsar cluster in pulsar-testenv namespace
./redeploy_1node_cluster.sh java_test_images.yaml issue22601.yaml
```

### Reproducing the issue

Checking out pulsar-playground
```shell
cd ~/workspace-pulsar
git clone https://github.com/lhotari/pulsar-playground
```

Edit `common.sh` to contain the correct settings for your cluster.

Init pulsar namespace
```shell
cd ~/workspace-pulsar/pulsar-playground/issues/issue22601
. ./common.sh
./create_ns.sh
```

Open 3 terminals in `~/workspace-pulsar/pulsar-playground/issues/issue22601` directory

1. terminal: run `./logs.sh`

2. terminal: run `./start_consuming.sh`

3. terminal: run `./produce.sh`


To reproduce the issue. CTRL-C the `./start_consuming.sh` script and pause for about 10 seconds so that backlog
builds up. When `./start_consuming.sh` is started, it reproduces the issue if the fix isn't represent.
This step can be repeated multiple times. It can be seen from the stats that consuming gets stuck very often.
That's a separate issue.

### Connecting a debugger

The test env has debugger active on port 5005. Port forwarding or direct access to `pulsar-testenv-deployment-broker-0.pulsar-testenv-deployment-broker.pulsar-testenv.svc.cluster.local:5005` can be used for debugging.

I use this to connect from IntelliJ debugger running in my MacOS laptop:
```shell
ssh -C -L 5005:pulsar-testenv-deployment-broker-0.pulsar-testenv-deployment-broker.pulsar-testenv.svc.cluster.local:5005 xps
```

### Collecting heapdump + threaddumps

I use https://github.com/lhotari/pulsar-contributor-toolbox/blob/master/scripts/collect_pulsar_gc_logs_from_pod.sh for collecting heapdump + threaddumps for the broker process.
This works also when the debugger is suspended on a break point.

```shell
~/workspace-pulsar/pulsar-contributor-toolbox/scripts/collect_jvm_diagnostics_from_pod.sh -n pulsar-testenv pod/pulsar-testenv-deployment-broker-0
```










