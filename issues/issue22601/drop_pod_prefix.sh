#!/bin/bash
exec sed 's/^[^]]*] *//' "$@"