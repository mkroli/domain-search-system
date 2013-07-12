Domain-Search-System
====================

The DSS (Domain-Search-System) adds search to DNS (Domain-Name-System).

Overview
--------

If you do a DNS lookup, DSS will proxy it to the configured DNS server.
If the response from the DNS server is a success, the response will directly be
relayed back.
If the response from the DNS server was not successful, DSS will look at the
question and search in its search index.
If the search was successful DSS will lookup the host configured in the search
index and answer the original question with the result of this lookup and with
an additional CNAME record containing the configured hostname.
If the search returns no match DSS will relay the unsuccessful response from
the DNS server back.

Installation
------------

Download and unpack the latest release from the releases page.
The configuration will be inside the "etc" directory.
The server can be started as follows:

```sh
bin/dss
```

Remember that DNS binds to UDP port 53 which requires root-privileges.

Configuration
-------------

The configuration is split in application configuration and configuration of
the search index.

dss.conf:

	server {
	  bind.port = 53        # the port where to bind to
	  fallback {
	    address = "8.8.8.8" # the IP of the DNS server used as relay
	    port = 53           # the port of the DNS server used as relay
	  }
	}
	
	index.include {
	  host = true           # whether to include the hostname in the search index
	  domain = true         # whether to include the domainname in the search index
	}

dss.hosts:

	github.com			git code share
	mkroli.github.io	michael krolikowski
