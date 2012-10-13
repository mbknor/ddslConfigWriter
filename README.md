DDSL Config Writer
==============

Short description
----------------

ddslConfigWriter automatically reconfigures nginx (or any other reverse proxy) when you add or remove a server.

Longer description
----------------

ddslConfigWriter is a utility to write new configuration when the list of online services registered to [DDSL](https://github.com/mbknor/ddsl) changes.

ddslConfigWriter uses velocity template engine to generate documentation and can therefor be used to reconfigure:

* Apache reverese proxy
* Squid
* nginx
* any reverese proxy out there.

Currently only nginx reconfiguration is bundled but as you can see, it is really easy to confure any reverse proxy, using the templateing system.

How does it work?
===========

ddslConfigWriter monitors [DDSL](https://github.com/mbknor/ddsl) for a specific service and (re)writes nginex-config (or any other reverse proxy) when the list of online services changes. Then it triggers reloading of the configuration.

It reads configuration from [config.properties](https://github.com/mbknor/ddslConfigWriter/blob/master/config.properties)


How to test it?
=============

Download and install [zookeeper](http://zookeeper.apache.org/)

start it:

	zkServer start-foreground

Start an application that uses DDSL, for example [ddsl-play2-producer-example](https://github.com/mbknor/ddsl-play2-module/tree/master/samples/ddsl-play2-producer-example)

compile and stage it:

	/path-to-play/play clean stage

start it:

	target/start

It will broadcast to DDSL that it is online.

configure **config.properties** to query for this application.

start ddslConfigWriter:

    sbt -DDDSL_CONFIG_PATH=../ddsl/ddsl-core/ddsl_config.properties run

It will write a new nginx config file: **generatedConfig.conf**

Start nginex with that config-file:

	nginex -c /full-path/generatedConfig.conf

nginex now runs in the background listening on port 7080 and will forward trafic to the play app

Now we want to add another play server.

Start ddsl-play2-producer-example on a new port on this or any other server.

Dupicate the ddsl-play2-producer-example-folder (need different folder due to RUNNING_PID-file)

compile and stage it:

    /path-to-play/play clean stage

run it on different port:

    target/start -Dhttp.port=9001

when this new play-app starts up and registers to DDSL, ddslConfigWriter will detect it and write new and update config to **generatedConfig.conf**

ddslConfigWriter will then tell nginx to reload its configuration.

traffic will now be forwarded to the old app running on 9000 and the new app running on 9001

If you now quits the old app running on 9000, nginex will get reconfigured to only forward trafic to 9001.




