!#/bin/bash

# https://github.com/leandromoreira/linux-network-performance-parameters/blob/master/README.md
# https://stackoverflow.com/a/62643129
# socket listen backlog 
sudo sysctl -w net.core.somaxconn=10000
# TCP/IP syn backlog
echo 10000 | sudo tee /proc/sys/net/ipv4/tcp_max_syn_backlog

# make more ports available
sudo sysctl -w net.ipv4.ip_local_port_range="15000 60999"
