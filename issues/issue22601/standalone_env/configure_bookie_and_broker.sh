#!/bin/bash
. ./common.sh
cd $SCRIPT_DIR
if [[ ! -f apply-config-from-env.py ]]; then
    curl -o apply-config-from-env.py https://raw.githubusercontent.com/apache/pulsar/master/docker/pulsar/scripts/apply-config-from-env.py
    chmod +x apply-config-from-env.py
fi
cd "$PULSAR_HOME"/conf
rm truststore.jks keystore.jks
$SCRIPT_DIR/ca.sh
$SCRIPT_DIR/gen_truststore_and_keystore.sh

PULSAR_PREFIX_tlsProvider=OpenSSL \
  PULSAR_PREFIX_tlsProviderFactoryClass=org.apache.bookkeeper.tls.TLSContextFactory \
  PULSAR_PREFIX_tlsClientAuthentication=true \
  PULSAR_PREFIX_tlsKeyStoreType=JKS \
  PULSAR_PREFIX_tlsKeyStore=conf/keystore.jks \
  PULSAR_PREFIX_tlsKeyStorePasswordPath=conf/.pass \
  PULSAR_PREFIX_tlsTrustStoreType=JKS \
  PULSAR_PREFIX_tlsTrustStore=conf/truststore.jks \
  PULSAR_PREFIX_tlsTrustStorePasswordPath=conf/.pass \
  PULSAR_PREFIX_allowLoopback=true \
  PULSAR_PREFIX_prometheusStatsHttpPort=8001 \
  PULSAR_PREFIX_httpServerPort=8001 \
  $SCRIPT_DIR/apply-config-from-env.py $PULSAR_HOME/conf/bookkeeper.conf

PULSAR_PREFIX_bookkeeperTLSProviderFactoryClass=org.apache.bookkeeper.tls.TLSContextFactory \
  PULSAR_PREFIX_bookkeeperTLSClientAuthentication=true \
  PULSAR_PREFIX_bookkeeperTLSKeyFileType=JKS \
  PULSAR_PREFIX_bookkeeperTLSTrustCertTypes=JKS \
  PULSAR_PREFIX_bookkeeperTLSKeyStorePasswordPath=conf/.pass \
  PULSAR_PREFIX_bookkeeperTLSTrustStorePasswordPath=conf/.pass \
  PULSAR_PREFIX_bookkeeperTLSKeyFilePath=conf/keystore.jks \
  PULSAR_PREFIX_bookkeeperTLSCertificateFilePath= \
  PULSAR_PREFIX_bookkeeperTLSTrustCertsFilePath=conf/truststore.jks \
  clusterName=pulsar-cluster-1 \
  metadataStoreUrl=zk:localhost:2181 \
  managedLedgerDefaultEnsembleSize=1 \
  managedLedgerDefaultWriteQuorum=1 \
  managedLedgerDefaultAckQuorum=1 \
  bookkeeperUseV2WireProtocol=true \
  $SCRIPT_DIR/apply-config-from-env.py $PULSAR_HOME/conf/broker.conf
