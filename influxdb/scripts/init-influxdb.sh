#!/bin/sh

set -e
influx bucket create -n sleep_rating -r 30d
influx bucket create -n smart_alarm_clock -r 30d