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

Debian Installation
-------------------

1. Create "/etc/apt/sources.list.d/mkroli.list":
```
deb http://content.wuala.com/contents/mkroli/public/debian/ mkroli/
deb-src http://content.wuala.com/contents/mkroli/public/debian/ mkroli/
```

2. Add GPG public key to apt keyring:
```bash
wget https://content.wuala.com/contents/mkroli/public/debian/mkroli_public_key.asc -O - | apt-key add -
```

3. Update apt index:
```bash
apt-get update
```

4. Install the dss package:
```bash
apt-get install dss
```

Configuration
-------------

dss.conf:

	server {
	  bind.port = 53          # the port where to bind to
	  fallback {
	    address = "8.8.8.8"   # the IP of the DNS server used as relay
	    port = 53             # the port of the DNS server used as relay
	  }
	  autoindex = true        # automatically add successfully looked up hosts to the index
	}
	
	index.include {
	  host = true             # whether to include the hostname in the search index
	  domain = true           # whether to include the domainname in the search index
	}
	
	http {
	  port = 5380             # port of the admin ui
	  interface = "127.0.0.1" # the interface the admin ui binds to
	}
