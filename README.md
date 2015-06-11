# Yet another GELF appender for logback

[![Build Status](https://travis-ci.org/csm/gelfback.svg?branch=master)](https://travis-ci.org/csm/gelfback)

This is a GELF appender for logback, with a slightly different focus. Specifically:

* Send messages over TCP, but don't block the logging path with network IO (because we use an AWS ELB in front of graylog, we can't use UDP).
* Periodically re-lookup and reconnect to the gelf host (because we use AWS Route53 in front of graylog).
* Depend on exactly nothing except the logback API (because we want to put it in an established application that we don't want to compile ourselves and don't want to litter with possibly-incompatible jars).

Binary releases are [on clojars.org](https://clojars.org/org.metastatic/gelfback).