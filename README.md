# Yet another GELF appender for logback

This is a GELF appender for logback, with a slightly different focus. Specifically:

* Send messages over TCP, but don't block the logging path network IO (because we use an AWS ELB in front of graylog).
* Periodically re-lookup and reconnect to the gelf host (because we use AWS Route53 in front of graylog).
* Depend on exactly nothing except the logback API.